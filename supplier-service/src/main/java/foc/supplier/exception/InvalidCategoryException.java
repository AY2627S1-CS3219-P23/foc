/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-07 (PR #161 review, LeongWZ).
 * Scope: thrown by SupplierService when a category name contains "/",
 * the delimiter of the flat Suppliers.category column, so the column and
 * the SupplierCategories rows cannot disagree.
 * Reviewed by: [pending]
 */
package foc.supplier.exception;

public class InvalidCategoryException extends RuntimeException {

    public InvalidCategoryException(String category) {
        super("Category '" + category + "' must not contain '/'");
    }
}
