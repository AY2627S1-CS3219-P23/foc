-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-27.
-- Scope: issue #242: closed_at on requests, for the admin failed-orders
-- table and its closed-time filter. The column, leaving existing rows
-- null, the partial index and having no CHECK against status are the
-- team's decisions.
-- Author review: Ryan Ang, pending pull request review.

-- When the request reached a final state (RequestsService sets it on
-- every transition into one). Null while active and for rows from before
-- this migration: their closing time was never recorded, so none is
-- guessed. No CHECK ties it to status; that would repeat the state
-- machine's final states.
alter table requests add column closed_at timestamp(6) with time zone;

-- For the closedFrom / closedTo filter, which never matches a null.
create index idx_requests_closed_at on requests (closed_at) where closed_at is not null;
