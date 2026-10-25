-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-25, issue #235.
-- Scope: the RESOLVED reservation status and the stored split of a
-- resolved dispute. The status, the column and the CHECK are the team's
-- decision (DSP-4, docs/disputes.md); the tool transcribed them.
-- Author review: Ryan Ang, pending pull request review.

-- A disputed request settled by an admin dividing the held reward
-- (DSP-4). The requester's share is amount - courier_share, never stored.
alter table credit_reservation drop constraint credit_reservation_status_check;
alter table credit_reservation add constraint credit_reservation_status_check
    check (status in ('HELD', 'REJECTED', 'TRANSFERRED', 'RELEASED', 'RESOLVED'));

-- null unless RESOLVED; kept with a 0 share too, so a repeated
-- resolution can be compared against it (DSP-13)
alter table credit_reservation add column courier_share integer;

-- the courier is recorded even when their share is 0
alter table credit_reservation add constraint credit_reservation_resolved_has_split
    check (status <> 'RESOLVED' or (courier_id is not null and courier_share between 0 and amount));
