-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-11-06, issue #48 (DP10).
-- Scope: ShedLock's lock table, so the retention purge runs on one replica
-- at a time (team decision, Leong Wei Zhi via options Q&A). The DDL is
-- the PostgreSQL table from the ShedLock README (7.10.1), unchanged, as
-- in the other services' shedlock migrations.
-- Author review: Leong Wei Zhi, pending pull request review.

-- One row per lock name (SchedulerLockConfig, @SchedulerLock). A replica
-- runs a job only while it holds the row: lock_until is in the future and
-- locked_by names it. The rows are kept between runs; never delete one by
-- hand, ShedLock caches that the row exists.
CREATE TABLE shedlock(name VARCHAR(64) NOT NULL, lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL, locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name));
