-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Fable 5), 2026-10-20, issue #177.
-- Scope: the role a pending sign-up verifies into. OWNER is stamped only
-- after /auth/signup-owner validated the setup key, so verify never needs
-- to see the key and the key is never stored. A new migration rather than
-- editing V3 in place (the owner-frontend branch's draft did; Flyway
-- refuses a changed checksum on every already-migrated database). Same
-- CHECK as users.role in V1.
-- Reviewed by: Leong Wei Zhi (via pull request).

alter table pending_signups
    add column role varchar(255) not null default 'USER'
        check (role in ('USER','ADMIN','OWNER'));
