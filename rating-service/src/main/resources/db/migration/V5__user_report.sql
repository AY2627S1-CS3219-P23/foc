-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Fable 5), 2026-10-30, issue #254.
-- Scope: the user_report table from the issue. request_id is an opaque
-- reference, not an FK to rateable_request (the author's decision via
-- options Q&A: that table holds completed requests only, and reports
-- about cancelled/dropped requests are the most report-worthy); details
-- is unbounded like rating.body, the cap lives in the DTO.
-- Author review: [pending]

-- A moderation flag on a user (N1.4), not an N3 dispute: stored and
-- listed only, no credit consequences, no events. Reports are immutable
-- and nothing deletes them in N1's scope.
create table user_report (
    id uuid not null,
    reported_user_id varchar(255) not null,
    reporter_id varchar(255) not null,
    request_id varchar(255),
    reason varchar(255) not null,
    details text,
    created_at timestamp(6) with time zone not null,
    primary key (id)
);

-- the admin list (#254): newest first with the id as tiebreak, so the
-- index carries the sort
create index idx_user_report_created on user_report (created_at desc, id desc);
