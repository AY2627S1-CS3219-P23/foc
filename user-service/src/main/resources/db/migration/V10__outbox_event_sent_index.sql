-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267 review.
-- Scope: the outbox retention purge's index, a copy of credit-service's
-- V3 (team decision: copy credit's retention purge).
-- Author review: Ryan Ang, pending pull request review.

-- the retention purge: sent events by when they were sent. Partial, so
-- it matches the purge's condition exactly and skips the unsent rows.
create index idx_outbox_event_sent on outbox_event (sent_at) where sent_at is not null;
