-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
-- Scope: processed_events (notification-service's event-ID dedupe
-- pattern, D4 there) and rateable_request, columns from the issue.
-- V1's comment said V2 would create the rating table; the team
-- sequenced the eligibility ingest (N1.1 prerequisite) first, so the
-- rating table lands in a later migration with #252.
-- Author review: [pending]

-- Event IDs already applied; the primary key rejects a concurrent
-- duplicate delivery. processed_at is indexed for a future retention
-- purge (notification-service precedent, its issue #137).
create table processed_events (
    event_id varchar(255) not null,
    processed_at timestamp(6) with time zone not null,
    primary key (event_id)
);
create index idx_processed_events_processed_at on processed_events (processed_at);

-- One row per completed request: who may rate whom (requester and
-- courier) and when it completed (the event's occurredAt). Written
-- once per request in practice — order-service emits one completion —
-- and a replayed completion under a fresh eventId upserts rather than
-- fails (RatingEligibilityProcessor).
create table rateable_request (
    request_id varchar(255) not null,
    requester_id varchar(255) not null,
    courier_id varchar(255) not null,
    completed_at timestamp(6) with time zone not null,
    primary key (request_id)
);
