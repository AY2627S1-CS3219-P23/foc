/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-26.
 * Scope: GET /suppliers list endpoint for issue #133 — search by name,
 * filter by category, paging + sorting. Default sort (name) and page
 * size (20) are author decisions.
 * Revised same day: added GET /suppliers/categories — the frontend's
 * filter dropdown needs the full set of categories, independent of any
 * current search/filter, so it can't be derived from listSuppliers's
 * own (filtered, paginated) response.
 * 2026-09-27: added optional lat/lng params — team decision to support
 * sorting by distance from the user's current (browser-geolocated)
 * position. When both are present they take priority over the
 * Pageable's own sort.
 * 2026-09-28 (PR #134 review, LeongWZ): added a handler for
 * InvalidSortException (see SupplierService.validateSort) so an
 * unrecognized `sort` value renders as a clean 400 problem+json body
 * instead of an unhandled 500, matching ProfileController's pattern in
 * user-service.
 * 2026-09-28 (PR #134 review, LeongWZ): removed this header's original
 * claim that the defaults follow D7's "indexed on name/category"
 * rationale — D7 was revised the same day as this file was first
 * written (2026-09-27, docs/supplier-service.md) to say indexing isn't
 * required at the current 1,000-supplier NFR2.1 target, so that
 * justification no longer holds; the defaults themselves are
 * unchanged.
 * 2026-09-29, issue #104: added the admin CRUD endpoints (POST/PUT/
 * DELETE /suppliers/{id}) — access control is enforced entirely by
 * SecurityConfig's role gate (ADMIN/OWNER), not here; a non-admin
 * never reaches these methods (403 before the body is even parsed).
 * POST/PUT bodies are validated with @Valid; SupplierNotFoundException
 * (PUT/DELETE on an unknown id) and bad request bodies both render as
 * problem+json, matching this controller's existing InvalidSortException
 * handler.
 * 2026-10-07 (PR #161 review, LeongWZ): added a 400 handler for
 * InvalidCategoryException (a category name containing "/").
 * Reviewed by: [pending]
 */
package foc.supplier.controller;

import foc.supplier.dto.PageResponse;
import foc.supplier.dto.SupplierRequest;
import foc.supplier.dto.SupplierResponse;
import foc.supplier.exception.InvalidCategoryException;
import foc.supplier.exception.InvalidSortException;
import foc.supplier.exception.SupplierNotFoundException;
import foc.supplier.service.SupplierService;
import jakarta.validation.Valid;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return supplierService.listSuppliers(search, category, lat, lng, pageable);
    }

    @GetMapping("/suppliers/categories")
    public List<String> listCategories() {
        return supplierService.listCategories();
    }

    // admin-only (SecurityConfig); the web client's SupplierFormModal
    // submits exactly this shape (see SupplierRequest's header)
    @PostMapping(value = "/suppliers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse createSupplier(@Valid @RequestBody SupplierRequest request) {
        return supplierService.createSupplier(request);
    }

    // admin-only (SecurityConfig)
    @PutMapping(value = "/suppliers/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SupplierResponse updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.updateSupplier(id, request);
    }

    // admin-only (SecurityConfig); hard delete per design doc D5 (team decision)
    @DeleteMapping("/suppliers/{id}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.noContent().build();
    }

    // the web client reads RFC 9457 problem+json error bodies
    @ExceptionHandler(InvalidSortException.class)
    public ProblemDetail handleInvalidSort(InvalidSortException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidCategoryException.class)
    public ProblemDetail handleInvalidCategory(InvalidCategoryException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    public ProblemDetail handleSupplierNotFound(SupplierNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // an unparsable openingTime/closingTime ("not HH:mm"), a malformed
    // JSON body, or a failed @Valid check (blank name/location/times)
    @ExceptionHandler({
        DateTimeParseException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentNotValidException.class
    })
    public ProblemDetail handleBadSupplierRequest() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid supplier request — check name, location, openingTime and closingTime (HH:mm)");
    }
}
