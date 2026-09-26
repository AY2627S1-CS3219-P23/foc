/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-26.
 * Scope: added a paginated search/filter query for issue #133 (list
 * endpoint: search by name, filter by category, paging + sorting).
 * Joins supplier_categories (not the redundant flat `category` column)
 * so filtering matches a supplier's actual, possibly multiple,
 * categories.
 * Reviewed by: [pending]
 */
package foc.supplier.repository;

import foc.supplier.model.Suppliers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SuppliersRepository extends JpaRepository<Suppliers, Long> {
    // Basic CRUD operations (save, findAll, findById, delete) are automatically included!

    @Query("""
            SELECT DISTINCT s FROM Suppliers s
            LEFT JOIN SupplierCategories sc ON sc.supplier = s
            WHERE (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            AND (:category IS NULL OR LOWER(sc.category) = LOWER(CAST(:category AS string)))
            """)
    Page<Suppliers> search(@Param("search") String search, @Param("category") String category,
            Pageable pageable);
}
