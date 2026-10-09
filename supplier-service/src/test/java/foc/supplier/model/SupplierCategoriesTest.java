/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-10-05.
 * Scope: unit tests for SupplierCategories.normalizeCategory (issue #160 —
 * case normalisation of category names).
 * Reviewed by: [pending]
 */
package foc.supplier.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SupplierCategoriesTest {

    @Test
    void capitalisesFirstLetterAndLowercasesRest() {
        assertEquals("Food", SupplierCategories.normalizeCategory("Food"));
        assertEquals("Food", SupplierCategories.normalizeCategory("food"));
        assertEquals("Food", SupplierCategories.normalizeCategory("fOOD"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals("Coffee", SupplierCategories.normalizeCategory("  coffee "));
    }

    @Test
    void blankOrNullReturnsNull() {
        assertNull(SupplierCategories.normalizeCategory(null));
        assertNull(SupplierCategories.normalizeCategory("   "));
    }
}
