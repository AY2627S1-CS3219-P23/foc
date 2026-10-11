-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Fable 5), 2026-10-11, issue #94.
-- Scope: turns V1's generic account_tokens scaffold into the
-- password-reset flow's storage, in the pending-table-per-operation
-- shape and naming its siblings use (account_update_otps' parallel).
-- The flow decisions behind each piece were made by Leong Wei Zhi via
-- options Q&A: recovery is login + explicit restore, so no emailed
-- credential but the reset's exists and the kind discriminator goes
-- (V1's CHECK on it goes with the column); the reset credential is a
-- 6-digit OTP code, so code_hash is BCrypt (never looked up by hash —
-- confirm identifies the row by the caller's email), attempts carries
-- the 5-guess cap a 10^6 space needs, and created_at anchors the
-- resend cooldown. One live code per account: a repeat request renews
-- the row, and the unique index makes the no-row insert race a
-- constraint violation instead of a second live code.
-- (Squashed from this branch's V7/V8/V9 before merge — the design
-- iterated in review; anyone who ran the branch resets their dev
-- user-db, see the README's "Old local databases" steps.)
-- Author review: Leong Wei Zhi to review via the PR.

alter table account_tokens rename to password_reset_otps;

alter table password_reset_otps rename column token_hash to code_hash;

-- also drops V1's kind CHECK, which includes the column
alter table password_reset_otps drop column kind;

-- the resend cooldown's origin. The table has never been written by
-- production code (the flow arrives with this migration), so the
-- default only guards baselined databases that somehow hold rows; new
-- code always sets the value
alter table password_reset_otps
    add column created_at timestamp(6) with time zone not null default now();

-- wrong guesses against the current code, resends included
alter table password_reset_otps
    add column attempts integer not null default 0;

-- one live reset code per account: a repeat request renews the row
-- (same shape as account_update_otps' one-per-user index)
create unique index idx_password_reset_otps_user on password_reset_otps (user_id);

-- V1's generated FK name (unquoted there, so folded to lowercase),
-- renamed to the siblings' convention
alter table password_reset_otps
    rename constraint fk19h8y4psfcpn5ct8b9mm2o2na to fk_password_reset_otps_user;
