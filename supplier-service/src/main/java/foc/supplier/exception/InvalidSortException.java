/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-28 (PR #134 review, LeongWZ).
 * Scope: thrown by SupplierService when a `sort` request parameter
 * names a property that either doesn't exist on the `Suppliers` entity
 * or isn't meant to be sortable — see SupplierService for why this
 * needs to be caught before Spring Data ever sees the Sort.
 * Reviewed by: [pending]
 */
package foc.supplier.exception;

public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String property) {
        super("Cannot sort by '" + property + "'");
    }
}
