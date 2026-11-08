-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Fable 5.1), 2026-11-08, PR #306 review (Sinnez1).
-- Scope: a volume built before the typed-contracts refactor (D16-D19) still
-- holds the NOT NULL entity_type/entity_id columns and the entity_sequences
-- table; baseline-on-migrate records it as V1 without comparing, so every
-- insert failed at runtime. Dropping them here lets such a volume converge
-- on its own; on any other database every statement is a no-op.
-- Author review: Leong Wei Zhi, pending pull request review.

alter table notifications drop column if exists entity_type;
alter table notifications drop column if exists entity_id;
drop table if exists entity_sequences;
