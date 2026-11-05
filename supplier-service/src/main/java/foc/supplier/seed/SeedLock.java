/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): Flyway baseline / advisory-locked seeding for
 * multi-replica starts (team decision, Leong Wei Zhi via options Q&A).
 * A transaction-scoped PostgreSQL advisory lock that SuppliersSeeder
 * takes before its empty-table check, so two replicas starting against
 * one empty database seed the CSV once: the second waits, then sees the
 * rows the first committed. Same mechanism as BookmarkLock, but blocking
 * (pg_advisory_xact_lock, not the try- form), since the waiting replica
 * should carry on starting rather than fail.
 * Key: the two-int form (1, 1). PostgreSQL keeps the two-int and the
 * single-bigint key spaces apart, so it cannot collide with BookmarkLock,
 * which keys the single-bigint form by user id. A later lock in this
 * database should take another two-int pair.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.supplier.seed;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class SeedLock {

    // two-int advisory key (classid, objid) for supplier seeding
    static final int KEY_CLASS = 1;
    static final int KEY_SUPPLIERS_CSV = 1;

    @PersistenceContext
    private EntityManager entityManager;

    // Must be called inside a transaction; blocks until the lock is free
    // and holds it until that transaction ends.
    public void lock() {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(:keyClass, :key)")
                .setParameter("keyClass", KEY_CLASS)
                .setParameter("key", KEY_SUPPLIERS_CSV)
                .getSingleResult();
    }
}
