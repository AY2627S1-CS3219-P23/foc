-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267.
-- Scope: the transactional outbox for user events (team decision USR-2:
-- a copy of order-service's): its outbox_event table and partial index,
-- without request_id.
-- Author review: Ryan Ang, pending pull request review.

-- One row per user event, written in the same transaction as the change
-- it reports; the publisher sets sent_at once the broker confirmed it.
create table outbox_event (
    event_id uuid not null,
    event_type varchar(255) not null,
    payload text not null,
    created_at timestamp(6) with time zone not null,
    sent_at timestamp(6) with time zone,
    next_attempt_at timestamp(6) with time zone,
    primary key (event_id)
);

-- the publisher's poll: unsent events, oldest first. Partial, so the
-- index stays small no matter how many sent rows accumulate.
create index idx_outbox_event_unsent on outbox_event (created_at) where sent_at is null;
