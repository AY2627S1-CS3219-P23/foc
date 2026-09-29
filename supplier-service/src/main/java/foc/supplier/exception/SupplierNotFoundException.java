/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-29.
 * Scope: thrown by SupplierService when an update/delete targets a
 * supplier id that doesn't exist (issue #104), mirroring
 * InvalidSortException's plain-RuntimeException-plus-controller-handler
 * pattern already established in this service.
 * Reviewed by: [pending]
 */
package foc.supplier.exception;

public class SupplierNotFoundException extends RuntimeException {

    public SupplierNotFoundException(Long id) {
        super("Supplier " + id + " not found");
    }
}
