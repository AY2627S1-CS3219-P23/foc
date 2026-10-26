-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-26.
-- Scope: issue #239 (team decisions DSP-2, DSP-7, DSP-16,
-- docs/disputes.md, and the #239 schema answers): DISPUTED and RESOLVED
-- in the status CHECK, and the request_dispute table. Column types,
-- nullability, the foreign key and every CHECK are the team's decisions.
-- Author review: Ryan Ang, pending pull request review.

-- V1's CHECK with the two dispute states added (OrderStates).
alter table requests drop constraint requests_status_check;
alter table requests add constraint requests_status_check check (status in (
    'PENDING', 'CREATED', 'ACCEPTED', 'COLLECTED', 'ARRIVED',
    'COMPLETED', 'CANCELLED', 'EXPIRED', 'REJECTED', 'DISPUTED', 'RESOLVED'));

-- One dispute per order (DSP-2): raised by the requester or by the expiry
-- sweep, then resolved by an admin. Unlike the outbox, a dispute is part
-- of its order, so request_id is a foreign key.
create table request_dispute (
    request_id bigint not null references requests (id),
    -- a DisputeCategory name (DSP-7)
    category text not null,
    dispute_note text,
    -- null when the sweep escalated it: no person raised it
    raised_by bigint,
    raised_at timestamp(6) with time zone not null,
    auto_escalated boolean not null,
    -- the resolution: all null until an admin resolves the dispute
    resolved_by bigint,
    resolved_at timestamp(6) with time zone,
    courier_amount integer,
    requester_amount integer,
    resolution_note text,
    primary key (request_id),
    constraint request_dispute_category_check check (category in (
        'NOT_DELIVERED', 'WRONG_ITEM', 'DAMAGED', 'OTHER')),
    -- a dispute raised by hand says who raised it
    constraint request_dispute_raised_by_check check (auto_escalated or raised_by is not null),
    -- the resolution fields are set together or not at all
    constraint request_dispute_resolution_check check (
        (resolved_at is null) = (resolved_by is null)
        and (resolved_at is null) = (courier_amount is null)
        and (resolved_at is null) = (requester_amount is null)),
    constraint request_dispute_amounts_check check (courier_amount >= 0 and requester_amount >= 0)
);
