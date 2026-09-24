/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-24.
 * Scope: new @IdClass for SupplierTypes, per author decision that a
 * supplier with multiple types (e.g. CSV "Food/Coffee") gets one
 * SupplierTypes row per type, so (id, type) together are the primary
 * key rather than id alone.
 * Renamed same day, SupplierTypeId -> SupplierCategoryId, following the
 * SupplierTypes -> SupplierCategories table/entity rename (author
 * decision; the internal "type" field itself was kept as-is).
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.supplier.model;

import java.io.Serializable;
import java.util.Objects;

public class SupplierCategoryId implements Serializable {

    private Long id;
    private String type;

    public SupplierCategoryId() {
    }

    public SupplierCategoryId(Long id, String type) {
        this.id = id;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplierCategoryId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }
}
