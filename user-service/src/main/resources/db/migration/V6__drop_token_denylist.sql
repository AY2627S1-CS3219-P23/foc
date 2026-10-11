-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Fable 5), 2026-10-11, issue #151.
-- Scope: drops the token_denylist table V1 baselined. The logout denylist
-- moved to Redis TTL keys checked by the API gateway (team decision,
-- Leong Wei Zhi, 2026-10-11, options Q&A) — no service reads this table,
-- no repository was ever written for it, and Redis expiry replaces the
-- planned purge-scheduler sweep.
-- Reviewed by: Leong Wei Zhi (via pull request).

drop table if exists token_denylist;
