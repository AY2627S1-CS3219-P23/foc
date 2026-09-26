/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-26.
 * Scope: added a batch lookup by supplier id for issue #133, so the
 * list endpoint fetches every returned page's categories in one query
 * instead of one query per supplier.
 * Reviewed by: [pending]
 */
package foc.supplier.repository;

import foc.supplier.model.SupplierCategories;
import foc.supplier.model.SupplierCategoryId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierCategoriesRepository extends JpaRepository<SupplierCategories, SupplierCategoryId> {
    // Basic CRUD operations (save, findAll, findById, delete) are automatically included!

    List<SupplierCategories> findBySupplier_IdIn(List<Long> supplierIds);
}
