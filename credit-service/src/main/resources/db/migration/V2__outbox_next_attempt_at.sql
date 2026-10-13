-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-13, PR #188 review (Leong Wei
-- Zhi).
-- Scope: the outbox column that spaces out the attempts of a reply the
-- broker returned as unroutable (docs/credit-service.md D10). The column
-- and what it means are the team's decision.
-- Author review: Ryan Ang, pending pull request review.

-- When the publisher may try the reply again. Null = due at once, which
-- is every reply until the broker returns it as unroutable; the poll
-- reads only the unsent rows that are due (D10).
alter table outbox_event add column next_attempt_at timestamp(6) with time zone;
