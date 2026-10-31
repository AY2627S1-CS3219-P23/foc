-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
-- Scope: the calls order-service owes chat-service (C11): written in the
-- transaction that changes the request, sent by ChatCallDispatcher,
-- retried every 15 s up to 20 attempts. The table shape is the tool's
-- implementation choice, listed in the doc for review.
-- Author review: Ko-Khan, pending pull request review.

-- A row is deleted once chat-service has taken the call; a row left FAILED
-- ran out of attempts and stays for inspection.
create table chat_call (
    id               bigserial primary key,
    request_id       bigint not null,
    kind             varchar(8) not null check (kind in ('OPEN', 'CLOSE')),
    -- the chat's parties, for an OPEN; null on a CLOSE
    requester_id     bigint,
    courier_id       bigint,
    status           varchar(8) not null check (status in ('PENDING', 'FAILED')),
    attempts         integer not null default 0,
    next_attempt_at  timestamp(6) with time zone not null,
    created_at       timestamp(6) with time zone not null,
    check (kind <> 'OPEN' or (requester_id is not null and courier_id is not null))
);

-- the dispatcher's poll: the pending calls that are due, oldest first
create index idx_chat_call_pending on chat_call (next_attempt_at) where status = 'PENDING';
-- one request's calls go out in order
create index idx_chat_call_request on chat_call (request_id, id);
