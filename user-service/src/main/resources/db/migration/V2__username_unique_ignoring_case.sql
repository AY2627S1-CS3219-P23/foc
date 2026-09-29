-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-09-29, issue #147.
-- Scope: usernames unique ignoring case in the database, not just in the
-- app's check (team decision). Accounts whose usernames already differ
-- only in case are renamed first (team decision): the oldest keeps its
-- name, each later one gets a numbered suffix (bob, then bob_2, bob_3),
-- cut to fit the 30-character limit and skipping names already taken.
-- Author review: Ryan to review via the PR.

do $$
declare
    dup record;
    n integer;
    candidate text;
begin
    for dup in
        select id, username, pos
        from (
            select id, username,
                   row_number() over (partition by lower(username) order by created_at, id) as pos
            from users
        ) ranked
        where pos > 1
        order by lower(username), pos
    loop
        n := dup.pos;
        loop
            candidate := left(dup.username, 30 - length('_' || n)) || '_' || n;
            exit when not exists (select 1 from users where lower(username) = lower(candidate));
            n := n + 1;
        end loop;
        update users set username = candidate where id = dup.id;
    end loop;
end $$;

-- replaced by the case-insensitive index below
alter table users drop constraint if exists idx_users_username;

create unique index idx_users_username_lower on users (lower(username));
