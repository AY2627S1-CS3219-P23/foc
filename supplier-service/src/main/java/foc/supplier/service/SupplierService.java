/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-26.
 * Scope: new service for issue #133 — the supplier list endpoint's
 * pagination, search-by-name, and category-filter logic. Maps
 * `Suppliers` + its `SupplierCategories` rows into `SupplierResponse`.
 * Revised same day: added listCategories() — the frontend's filter
 * dropdown needs every category that exists, not just those on the
 * currently filtered page.
 * 2026-09-27: listSuppliers() now takes optional lat/lng and, when
 * both are present, orders by distance from that point instead of the
 * Pageable's own sort (team decision: sort by distance from the
 * user's current location). The distance query strips the incoming
 * Pageable's Sort before calling the repository — Spring Data appends
 * a Pageable's Sort onto a native @Query's raw SQL as a naive string
 * concatenation, and the query already ends in its own ORDER BY, so
 * leaving the default "name" sort attached produced a malformed
 * double ORDER BY (Postgres: "syntax error at or near order").
 * 2026-09-28 (PR #134 review, LeongWZ): search is escaped for LIKE
 * metacharacters before being bound — `%` and `_` are wildcards, not
 * literal characters, in a LIKE pattern; unescaped, a literal `%` in
 * the search box (e.g. typing just "%") produced the pattern `%%%`,
 * which matches every supplier instead of the (correct) zero, and
 * corrupted totalElements/totalPages the same way since the count
 * query shares the predicate. Not a SQL-injection risk either way —
 * :search was already a bound parameter — this only fixes wildcard
 * *content* being misinterpreted. `\` is the escape character; it's
 * escaped first so a literal backslash in a search term doesn't itself
 * get misread as introducing an escape sequence.
 * 2026-09-28 (PR #134 review, LeongWZ): the `sort` request parameter
 * was passed straight into the repository's Pageable with no
 * validation. Spring Data resolves a Sort property against the JPA
 * entity (Suppliers), not the response DTO (SupplierResponse) — the
 * two don't share field names (e.g. the DTO's "location"/"openingTime"
 * are the entity's "locationDescription"/"startingTime"), so a value
 * that matches the API response but not the entity — or the frontend's
 * own 'distance' sentinel, if it ever leaked through unconverted —
 * throws PropertyReferenceException. This service has no
 * @ControllerAdvice, so that surfaced as an unhandled HTTP 500. Fixed
 * by validating against an explicit allow-list before the Sort ever
 * reaches the repository, throwing InvalidSortException (mapped to 400
 * in the controller) for anything else.
 * 2026-09-28: the seed CSV's ImageURL column points at GitHub's file
 * *viewer* page (github.com/.../blob/<ref>/<path>), which serves
 * text/html, not the image itself — an <img> tag pointed at it shows a
 * broken image. The seed CSV is the course-provided data and can't be
 * edited, so the fix normalizes the URL here, at the API response
 * boundary, to GitHub's raw-content host (raw.githubusercontent.com),
 * which serves the actual image bytes. Applied at read time (not at
 * seed time) so it also covers any supplier created/edited later
 * through the admin CRUD API with the same kind of URL, not just the
 * CSV-seeded rows.
 * 2026-09-29, issue #104: added createSupplier/updateSupplier/
 * deleteSupplier (F1.1–F1.1.4; team decision, D5: hard delete). The
 * request DTO's single `location` field (the form has no separate
 * building/description inputs) is written entirely into
 * locationDescription with building cleared, not merged with whatever
 * building was already there — an edit's `location` starts as
 * SupplierResponse's already-combined "building, description" string
 * (round-tripped through the form), so keeping the old building too
 * would double it up on the next read. Categories are reconciled by
 * deleting a supplier's existing rows and reinserting the request's
 * set, the simplest correct way to handle an arbitrary added/removed
 * set without diffing. Delete removes the category rows first — no
 * cascade is declared from Suppliers, and the FK would otherwise
 * reject the supplier row's deletion.
 * Reviewed by: [pending]
 */
package foc.supplier.service;

import foc.supplier.dto.PageResponse;
import foc.supplier.dto.SupplierRequest;
import foc.supplier.dto.SupplierResponse;
import foc.supplier.exception.InvalidSortException;
import foc.supplier.exception.SupplierNotFoundException;
import foc.supplier.model.SupplierCategories;
import foc.supplier.model.Suppliers;
import foc.supplier.repository.SupplierCategoriesRepository;
import foc.supplier.repository.SuppliersRepository;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private final SuppliersRepository suppliersRepository;
    private final SupplierCategoriesRepository supplierCategoriesRepository;

    public SupplierService(SuppliersRepository suppliersRepository,
            SupplierCategoriesRepository supplierCategoriesRepository) {
        this.suppliersRepository = suppliersRepository;
        this.supplierCategoriesRepository = supplierCategoriesRepository;
    }

    // The only Suppliers entity properties this endpoint allows sorting
    // by. "distance" is deliberately absent — it's the frontend's own
    // sentinel for the lat/lng branch below, not a Sort property; it
    // must never reach Spring Data as one.
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("name", "id");

    public PageResponse<SupplierResponse> listSuppliers(String search, String category, Double lat, Double lng,
            Pageable pageable) {
        validateSort(pageable.getSort());

        String normalizedSearch = escapeLikePattern(blankToNull(search));
        String normalizedCategory = blankToNull(category);

        Page<Suppliers> page;
        if (lat != null && lng != null) {
            Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
            page = suppliersRepository.searchOrderedByDistance(normalizedSearch, normalizedCategory, lat, lng, unsorted);
        } else {
            page = suppliersRepository.search(normalizedSearch, normalizedCategory, pageable);
        }

        List<Long> supplierIds = page.getContent().stream().map(Suppliers::getId).toList();
        Map<Long, List<String>> categoriesBySupplierId = supplierCategoriesRepository
                .findBySupplier_IdIn(supplierIds).stream()
                .collect(Collectors.groupingBy(
                        sc -> sc.getSupplier().getId(),
                        Collectors.mapping(SupplierCategories::getCategory, Collectors.toList())));

        List<SupplierResponse> content = page.getContent().stream()
                .map(s -> toResponse(s, categoriesBySupplierId.getOrDefault(s.getId(), List.of())))
                .toList();

        return PageResponse.of(page, content);
    }

    public List<String> listCategories() {
        return supplierCategoriesRepository.findDistinctCategories();
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        Suppliers supplier = new Suppliers();
        applyRequest(supplier, request);
        Suppliers saved = suppliersRepository.save(supplier);
        List<String> categories = saveCategories(saved, request.categories());
        return toResponse(saved, categories);
    }

    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        Suppliers supplier = suppliersRepository.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));
        applyRequest(supplier, request);
        Suppliers saved = suppliersRepository.save(supplier);
        supplierCategoriesRepository.deleteBySupplierId(id);
        List<String> categories = saveCategories(saved, request.categories());
        return toResponse(saved, categories);
    }

    @Transactional
    public void deleteSupplier(Long id) {
        if (!suppliersRepository.existsById(id)) {
            throw new SupplierNotFoundException(id);
        }
        supplierCategoriesRepository.deleteBySupplierId(id);
        suppliersRepository.deleteById(id);
    }

    private static void applyRequest(Suppliers supplier, SupplierRequest request) {
        supplier.setName(request.name());
        // the form has one free-text location field, not separate
        // building/description inputs — see class header
        supplier.setBuilding(null);
        supplier.setLocationDescription(request.location());
        supplier.setStartingTime(LocalTime.parse(request.openingTime(), TIME_FORMAT));
        supplier.setClosingTime(LocalTime.parse(request.closingTime(), TIME_FORMAT));
        supplier.setSupplierDescription(request.description());
        supplier.setLatitude(request.latitude());
        supplier.setLongitude(request.longitude());
        supplier.setImageURL(request.imageUrl());
    }

    // Saves one SupplierCategories row per non-blank category and
    // returns exactly the set that was persisted, for the response.
    private List<String> saveCategories(Suppliers supplier, List<String> categories) {
        if (categories == null) {
            return List.of();
        }
        List<String> nonBlank = categories.stream().filter(c -> c != null && !c.isBlank()).toList();
        for (String category : nonBlank) {
            SupplierCategories sc = new SupplierCategories();
            sc.setSupplier(supplier);
            sc.setCategory(category);
            supplierCategoriesRepository.save(sc);
        }
        return nonBlank;
    }

    private static SupplierResponse toResponse(Suppliers s, List<String> categories) {
        return new SupplierResponse(s.getId(), s.getName(), s.getBuilding(), s.getLocationDescription(),
                s.getLatitude(), s.getLongitude(), categories, s.getStartingTime(), s.getClosingTime(),
                s.getSupplierDescription(), normalizeImageUrl(s.getImageURL()));
    }

    // Matches a GitHub file-viewer URL (github.com/<owner>/<repo>/blob/<ref>/<path>)
    // and captures the three parts needed to rebuild it as a raw-content
    // URL (raw.githubusercontent.com/<owner>/<repo>/<ref>/<path>), which
    // is what actually serves the image bytes an <img> tag needs.
    private static final Pattern GITHUB_BLOB_URL =
            Pattern.compile("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$");

    private static String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return imageUrl;
        }
        Matcher matcher = GITHUB_BLOB_URL.matcher(imageUrl);
        if (!matcher.matches()) {
            return imageUrl;
        }
        return "https://raw.githubusercontent.com/" + matcher.group(1) + "/" + matcher.group(2) + "/" + matcher.group(3);
    }

    private static void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty());
            }
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    // Escapes LIKE metacharacters (%, _) and the escape character (\)
    // itself so a search term is matched literally, not as a wildcard
    // pattern. Paired with `ESCAPE '\'` in each repository LIKE clause.
    private static String escapeLikePattern(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
