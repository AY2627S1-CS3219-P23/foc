-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-25, PR #247 review follow-up.
-- Scope: the other direction of V4's split CHECK, a review suggestion
-- the team took; the tool transcribed it.
-- Author review: Ryan Ang, pending pull request review.

-- V4 makes a RESOLVED record carry its split; this makes the split
-- belong to a RESOLVED record only, so "null unless RESOLVED" holds in
-- the database as well as in the service
alter table credit_reservation add constraint credit_reservation_share_only_when_resolved
    check (courier_share is null or status = 'RESOLVED');
