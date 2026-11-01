/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-23
Scope: Generated public profile DTO (issue #95): username only, per
       F8.1.1. Ratings etc. may be added later in future PR or features.
Author review: Ryan reviewed to ensure it follows team's decision.
2026-10-31 (Claude Code, Fable 5), issue #280: joinedAt added (the
signup instant; backs the wireframe's "Joined {month year}" tile) — a
deliberate re-widening of the F8.1.1 username-only decision, recorded
in ai/usage-log.md. Email, role and id stay excluded.
Author review: pending.
*/

package foc.user.dto;

import java.time.Instant;

public record PublicProfileResponse(
    String username,
    Instant joinedAt
) {}
