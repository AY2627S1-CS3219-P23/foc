/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-26.
 * Scope: new service for issue #133 — the supplier list endpoint's
 * pagination, search-by-name, and category-filter logic. Maps
 * `Suppliers` + its `SupplierCategories` rows into `SupplierResponse`.
 * Revised same day: added listCategories() — the frontend's filter
 * dropdown needs every category that exists, not just those on the
 * currently filtered page.
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

    public PageResponse<SupplierResponse> listSuppliers(String search, String category, Pageable pageable) {
        String normalizedSearch = blankToNull(search);
        String normalizedCategory = blankToNull(category);

        Page<Suppliers> page = suppliersRepository.search(normalizedSearch, normalizedCategory, pageable);

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
}
