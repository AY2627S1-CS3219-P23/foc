/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8), repository for SupplierBookmarks: list and count
 * a user's bookmarks (newest first), and bulk delete by user for the
 * account-purge cleanup (D12). Deletes are bulk @Modifying queries rather
 * than derived deletes, since a derived delete cannot build its key from a
 * @MapsId association combined with @IdClass (see
 * SupplierCategoriesRepository).
 * Reviewed by: [pending]
 */
package foc.supplier.repository;

import foc.supplier.model.SupplierBookmarkId;
import foc.supplier.model.SupplierBookmarks;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierBookmarksRepository extends JpaRepository<SupplierBookmarks, SupplierBookmarkId> {

    @Query("SELECT b FROM SupplierBookmarks b JOIN FETCH b.supplier "
            + "WHERE b.userId = :userId ORDER BY b.bookmarkedAt DESC")
    List<SupplierBookmarks> findByUserIdNewestFirst(@Param("userId") Long userId);

    long countByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM SupplierBookmarks b WHERE b.userId = :userId AND b.supplierId = :supplierId")
    int deleteByUserIdAndSupplierId(@Param("userId") Long userId, @Param("supplierId") Long supplierId);

    @Modifying
    @Query("DELETE FROM SupplierBookmarks b WHERE b.userId IN :userIds")
    int deleteByUserIdIn(@Param("userIds") Collection<Long> userIds);
}
