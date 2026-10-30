-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
-- Scope: the rating table (columns from the issue; the FK to
-- rateable_request and the stars check are defense-in-depth, the
-- author's decision) and outbox_event (credit-service's V1 DDL with
-- order-service's next_attempt_at folded in; copied, not shared, by
-- the author's decision).
-- Author review: [pending]

-- One rating per direction per completed request (#44 decisions:
-- stars 1-5 required, write-up optional, immutable after submit).
-- The unique key is the race backstop behind the service's
-- already-rated pre-check; the FK holds because the eligibility
-- upsert never changes request_id and nothing deletes its rows.
create table rating (
    id uuid not null,
    request_id varchar(255) not null,
    rater_id varchar(255) not null,
    ratee_id varchar(255) not null,
    stars smallint not null,
    body text,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint rating_request_fk foreign key (request_id) references rateable_request (request_id),
    constraint rating_once_per_rater unique (request_id, rater_id),
    constraint rating_stars_range check (stars between 1 and 5)
);

-- rating.submitted events waiting to be published, written in the same
-- transaction as the rating row (transactional outbox, the
-- credit/order-service pattern).
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
-- index stays small however many sent rows accumulate.
create index idx_outbox_event_unsent on outbox_event (created_at) where sent_at is null;
