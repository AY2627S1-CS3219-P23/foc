/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-24.
 * Scope: new @IdClass for SupplierTypes, per author decision that a
 * supplier with multiple types (e.g. CSV "Food/Coffee") gets one
 * SupplierTypes row per type, so (id, type) together are the primary
 * key rather than id alone.
 * Renamed same day, SupplierTypeId -> SupplierCategoryId, following the
 * SupplierTypes -> SupplierCategories table/entity rename; the internal
 * "type" field was initially kept as-is, then also renamed to
 * "category" per author decision — this must match SupplierCategories'
 * @Id field name exactly, since @IdClass field names are matched by
 * name against the entity's @Id fields.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.supplier.model;

import java.io.Serializable;
import java.util.Objects;

public class SupplierCategoryId implements Serializable {

    private Long id;
    private String category;

    public SupplierCategoryId() {
    }

    public SupplierCategoryId(Long id, String category) {
        this.id = id;
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplierCategoryId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(category, that.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, category);
    }
}
