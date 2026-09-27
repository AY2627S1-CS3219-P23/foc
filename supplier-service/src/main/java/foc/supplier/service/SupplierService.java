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
 * Reviewed by: [pending]
 */
package foc.supplier.service;

import foc.supplier.dto.PageResponse;
import foc.supplier.dto.SupplierResponse;
import foc.supplier.model.SupplierCategories;
import foc.supplier.model.Suppliers;
import foc.supplier.repository.SupplierCategoriesRepository;
import foc.supplier.repository.SuppliersRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class SupplierService {

    private final SuppliersRepository suppliersRepository;
    private final SupplierCategoriesRepository supplierCategoriesRepository;

    public SupplierService(SuppliersRepository suppliersRepository,
            SupplierCategoriesRepository supplierCategoriesRepository) {
        this.suppliersRepository = suppliersRepository;
        this.supplierCategoriesRepository = supplierCategoriesRepository;
    }

    public PageResponse<SupplierResponse> listSuppliers(String search, String category, Double lat, Double lng,
            Pageable pageable) {
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

    private static SupplierResponse toResponse(Suppliers s, List<String> categories) {
        return new SupplierResponse(s.getId(), s.getName(), s.getBuilding(), s.getLocationDescription(),
                s.getLatitude(), s.getLongitude(), categories, s.getStartingTime(), s.getClosingTime(),
                s.getSupplierDescription(), s.getImageURL());
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
