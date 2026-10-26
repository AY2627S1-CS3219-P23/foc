/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8), new @IdClass for SupplierBookmarks: the composite
 * primary key (userId, supplierId), per the author's decision to use the
 * pair as the key rather than a surrogate id. Field names must match
 * SupplierBookmarks' @Id fields exactly.
 * Reviewed by: [pending]
 */
package foc.supplier.model;

import java.io.Serializable;
import java.util.Objects;

public class SupplierBookmarkId implements Serializable {

    private Long userId;
    private Long supplierId;

    public SupplierBookmarkId() {
    }

    public SupplierBookmarkId(Long userId, Long supplierId) {
        this.userId = userId;
        this.supplierId = supplierId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplierBookmarkId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(supplierId, that.supplierId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, supplierId);
    }
}
