/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-26.
 * Scope: added a batch lookup by supplier id for issue #133, so the
 * list endpoint fetches every returned page's categories in one query
 * instead of one query per supplier.
 * Revised same day: added a distinct-categories query for the filter
 * dropdown, which must list every category that exists, not just
 * those on the currently filtered/paginated page — fixing a bug where
 * selecting "Food" then reopening the dropdown only offered categories
 * found on Food suppliers, since it was previously derived from the
 * filtered result set on the frontend.
 * Reviewed by: [pending]
 */
package foc.supplier.repository;

import foc.supplier.model.SupplierCategories;
import foc.supplier.model.SupplierCategoryId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierCategoriesRepository extends JpaRepository<SupplierCategories, SupplierCategoryId> {
    // Basic CRUD operations (save, findAll, findById, delete) are automatically included!

    List<SupplierCategories> findBySupplier_IdIn(List<Long> supplierIds);

    @Query("SELECT DISTINCT sc.category FROM SupplierCategories sc ORDER BY sc.category")
    List<String> findDistinctCategories();
}
