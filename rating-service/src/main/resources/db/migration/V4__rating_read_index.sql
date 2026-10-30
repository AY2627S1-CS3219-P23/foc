-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Fable 5), 2026-10-30, issue #253.
-- Scope: the read index behind the profile endpoints; V3 left rating
-- unindexed by ratee_id because nothing read by it until now.
-- Author review: [pending]

-- The profile reads (#253): both endpoints filter by ratee_id, and the
-- review list pages newest first with the id as tiebreak, so the index
-- carries the sort too.
create index idx_rating_ratee_created on rating (ratee_id, created_at desc, id desc);
