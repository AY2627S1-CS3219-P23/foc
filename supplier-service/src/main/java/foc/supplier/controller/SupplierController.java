/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-26.
 * Scope: GET /suppliers list endpoint for issue #133 — search by name,
 * filter by category, paging + sorting. Default page size and sort
 * follow docs/supplier-service.md's D7 (indexed on name/category for
 * the NFR1.1 5-second bound); author decision on the default of 20.
 * Reviewed by: [pending]
 */
package foc.supplier.controller;

import foc.supplier.dto.PageResponse;
import foc.supplier.dto.SupplierResponse;
import foc.supplier.service.SupplierService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping("/suppliers")
    public PageResponse<SupplierResponse> listSuppliers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return supplierService.listSuppliers(search, category, pageable);
    }
}
