-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-19, issue #218.
-- Scope: the index the outbox retention purge reads. That a partial
-- index on sent_at backs the purge is the team's decision.
-- Author review: Ryan Ang, pending pull request review.

-- the retention purge: sent replies, by when they were sent (D13).
-- Partial, the counterpart of idx_outbox_event_unsent, so the purge's
-- condition matches the index exactly and unsent rows are never in it.
create index idx_outbox_event_sent on outbox_event (sent_at) where sent_at is not null;
