-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267.
-- Scope: account closure for user.purged (team decision USR-4): the
-- closed_at column and the CLOSURE history type; the tool transcribed
-- them.
-- Author review: Ryan Ang, pending pull request review.

-- When the account's user was purged; null = open. A closed account is
-- kept, since its reservations, lots and history reference it, and is
-- never reopened (USR-3).
alter table credit_account add column closed_at timestamp(6) with time zone;

-- CLOSURE: credits moved from a closed account to the common pool, at
-- closure or when they reach the account afterwards (USR-4)
alter table credit_history drop constraint credit_history_type_check;
alter table credit_history add constraint credit_history_type_check check (type in (
    'PROVISION', 'RESERVE', 'RELEASE', 'TRANSFER_OUT', 'TRANSFER_IN', 'EXPIRY', 'REDISTRIBUTION',
    'CLOSURE'));
