-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-23.
-- Scope: issue #240 (team decisions DSP-1, DSP-16, DSP-17,
-- docs/disputes.md): close_reason on requests and request_id on
-- outbox_event. Column types, the named CHECK, the bigint request_id with
-- no foreign key, and the back-fill are the team's decisions.
-- Author review: Ryan Ang, pending pull request review.

-- Why a request ended without completing (DSP-1); null for COMPLETED, for
-- rows from before this migration, and for a rejection whose reply had no
-- reasonCode. Stored as a CloseReason name; a reason added to CloseReason
-- needs a migration that replaces this CHECK.
alter table requests add column close_reason text;
alter table requests add constraint requests_close_reason_check check (close_reason in (
    'REQUESTER_CANCELLED', 'RESERVATION_TIMEOUT', 'INSUFFICIENT_CREDITS',
    'INVALID_REWARD', 'DEADLINE_PASSED', 'DISPUTE_RESOLVED'));

-- The request an outbox event is about (DSP-17), so retry settlement finds
-- a request's events without searching the payload. No foreign key: the
-- outbox is a log of messages, independent of the requests table.
alter table outbox_event add column request_id bigint;

-- Existing events: every one carries its request's id as correlationId
-- (RequestEvents sets it so).
update outbox_event set request_id = (payload::jsonb ->> 'correlationId')::bigint;

create index idx_outbox_event_request_id on outbox_event (request_id);
