/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8.1, N8.5), new entity for a user's bookmarked
 * supplier. Decisions by the author (Alastair Tan Choon Wei): composite
 * primary key (user_id, supplier_id), which makes the pair unique (Hibernate
 * orders the key columns supplier_id, user_id, so a separate user_id index
 * serves the per-user list and count); supplier_id is a foreign key with a DB-level
 * ON DELETE CASCADE (D11); bookmarked_at orders the list, most recent
 * first (D13). user_id has no FK because users live in user-service.
 * Table/column names were left to the tool and follow the Suppliers /
 * SupplierCategories entities. The schema comes from ddl-auto: update,
 * as for the other entities.
 * Reviewed by: [pending]
 */
package foc.supplier.model;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "SupplierBookmarks", indexes = @Index(name = "idx_supplier_bookmarks_user", columnList = "user_id"))
@IdClass(SupplierBookmarkId.class)
public class SupplierBookmarks {

    @Id
    private Long userId;

    @Id
    private Long supplierId;

    @ManyToOne(optional = false)
    @MapsId("supplierId")
    @JoinColumn(name = "supplier_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Suppliers supplier;

    @Column(nullable = false)
    private Instant bookmarkedAt;

    protected SupplierBookmarks() {
    }

    public SupplierBookmarks(Long userId, Suppliers supplier) {
        this.userId = userId;
        this.supplier = supplier;
        this.bookmarkedAt = Instant.now();
    }

    // Getters

    public Long getUserId() {
        return userId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public Suppliers getSupplier() {
        return supplier;
    }

    public Instant getBookmarkedAt() {
        return bookmarkedAt;
    }
}
