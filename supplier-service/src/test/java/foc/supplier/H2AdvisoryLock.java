/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): Flyway baseline / advisory-locked seeding for
 * multi-replica starts (team decision, Leong Wei Zhi via options Q&A).
 * Test-only H2 stand-in for PostgreSQL's pg_advisory_xact_lock(int, int),
 * registered by the INIT clause of the test datasource URL, so the H2
 * tests' contexts can still run SuppliersSeeder (H2 has no advisory
 * locks). It does nothing: the H2 contexts seed on one thread. The real
 * lock is tested on Postgres (SuppliersSeederConcurrencyTest).
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.supplier;

public final class H2AdvisoryLock {

    private H2AdvisoryLock() {
    }

    public static boolean lock(int keyClass, int key) {
        return true;
    }
}
