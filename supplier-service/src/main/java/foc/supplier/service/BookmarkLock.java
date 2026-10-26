/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8), per-user guard for adding a bookmark. A
 * transaction-scoped PostgreSQL advisory lock keyed by the user id: the
 * first request gets it, a simultaneous one from the same user does not
 * and is refused ("bookmarking too fast"), so the count-then-insert cap
 * check cannot be raced past the limit. It is released when the
 * transaction ends, works across service instances, and does not make
 * different users collide. PostgreSQL-only (the tests substitute it, as
 * they run on H2). Nothing else in this database uses advisory locks, so
 * the bare user id is the key.
 * Reviewed by: [pending]
 */
package foc.supplier.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class BookmarkLock {

    @PersistenceContext
    private EntityManager entityManager;

    // Must be called inside a transaction; the lock lives until it ends.
    public boolean tryLock(Long userId) {
        Object acquired = entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(:key)")
                .setParameter("key", userId)
                .getSingleResult();
        return Boolean.TRUE.equals(acquired);
    }
}
