/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-24.
 * Scope: new @IdClass for SupplierTypes, per author decision that a
 * supplier with multiple types (e.g. CSV "Food/Coffee") gets one
 * SupplierTypes row per type, so (id, type) together are the primary
 * key rather than id alone.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.supplier.model;

import java.io.Serializable;
import java.util.Objects;

public class SupplierTypeId implements Serializable {

    private Long id;
    private String type;

    public SupplierTypeId() {
    }

    public SupplierTypeId(Long id, String type) {
        this.id = id;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplierTypeId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }
}
