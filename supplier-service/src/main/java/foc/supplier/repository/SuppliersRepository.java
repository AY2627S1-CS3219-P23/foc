/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-26; revised 2026-09-27.
 * Scope: added a paginated search/filter query for issue #133 (list
 * endpoint: search by name, filter by category, paging + sorting).
 * Joins supplier_categories (not the redundant flat `category` column)
 * so filtering matches a supplier's actual, possibly multiple,
 * categories.
 * 2026-09-27: added searchOrderedByDistance — team decision to support
 * "sort by distance from the user's current location" (nearest first).
 * Native query (not JPQL) because ordering by a Haversine
 * great-circle-distance expression needs trig functions JPQL doesn't
 * expose; table/column names (suppliers, supplier_categories,
 * lower-case columns) taken from the actual generated schema, not the
 * @Table(name=...) casing, since Spring's physical naming strategy
 * lower-cases/snake-cases it regardless. Category filter uses EXISTS
 * instead of the JOIN the other query uses, to avoid duplicate rows
 * for multi-category suppliers without needing SELECT DISTINCT (which
 * doesn't compose with ORDER BY on a computed, non-selected column in
 * Postgres). LEAST/GREATEST clamp the acos() argument to [-1, 1] —
 * floating-point rounding can push it fractionally outside that range
 * for a supplier at ~0 distance, which would otherwise make acos()
 * return NULL.
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

    @Query(value = """
            SELECT s.* FROM suppliers s
            WHERE (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%')))
            AND (:category IS NULL OR EXISTS (
                SELECT 1 FROM supplier_categories sc
                WHERE sc.id = s.id AND LOWER(sc.category) = LOWER(CAST(:category AS TEXT))
            ))
            ORDER BY (
                6371000 * acos(
                    LEAST(1.0, GREATEST(-1.0,
                        cos(radians(CAST(:lat AS DOUBLE PRECISION))) * cos(radians(s.latitude))
                            * cos(radians(s.longitude) - radians(CAST(:lng AS DOUBLE PRECISION)))
                            + sin(radians(CAST(:lat AS DOUBLE PRECISION))) * sin(radians(s.latitude))
                    ))
                )
            ) ASC, s.id ASC
            """,
            countQuery = """
            SELECT count(*) FROM suppliers s
            WHERE (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%')))
            AND (:category IS NULL OR EXISTS (
                SELECT 1 FROM supplier_categories sc
                WHERE sc.id = s.id AND LOWER(sc.category) = LOWER(CAST(:category AS TEXT))
            ))
            """,
            nativeQuery = true)
    Page<Suppliers> searchOrderedByDistance(@Param("search") String search, @Param("category") String category,
            @Param("lat") Double lat, @Param("lng") Double lng, Pageable pageable);
}
