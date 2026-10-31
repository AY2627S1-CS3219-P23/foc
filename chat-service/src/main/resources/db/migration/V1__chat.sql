-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
-- Scope: the chat schema. Images stored in Postgres (C9), text capped at
-- 500 characters and images at 2 MB (C7), a chat per accepted courier
-- (C4); the UUID keys and the partial unique index are the tool's
-- implementation choices, listed in the doc for review.
-- Author review: Ko-Khan, pending pull request review.

-- One row per accepted courier of a request: opened when order-service
-- reports the acceptance, closed when the request leaves the in-progress
-- states (C3-C5). The party ids are the platform user ids, as in JWT sub.
create table chat (
    id            uuid primary key,
    request_id    bigint not null,
    requester_id  bigint not null,
    courier_id    bigint not null,
    status        varchar(16) not null check (status in ('OPEN', 'CLOSED')),
    opened_at     timestamp(6) with time zone not null,
    closed_at     timestamp(6) with time zone,
    check ((status = 'OPEN') = (closed_at is null))
);

-- at most one open chat per request
create unique index chat_one_open_per_request on chat (request_id) where status = 'OPEN';
-- admins list every chat of a request
create index idx_chat_request_id on chat (request_id);

create table chat_message (
    id                  uuid primary key,
    chat_id             uuid not null references chat (id) on delete cascade,
    sender_id           bigint not null,
    text                varchar(500),
    image               bytea,
    image_content_type  varchar(32),
    sent_at             timestamp(6) with time zone not null,
    -- text, an image, or both (C7)
    check (text is not null or image is not null),
    -- an image always has its type
    check ((image is null) = (image_content_type is null)),
    check (image is null or octet_length(image) <= 2097152)
);

-- history pages (newest first, before a time) and the retention purge
create index idx_chat_message_chat_sent on chat_message (chat_id, sent_at desc);
create index idx_chat_message_sent_at on chat_message (sent_at);
