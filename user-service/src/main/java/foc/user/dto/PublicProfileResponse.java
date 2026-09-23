/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-23
Scope: Generated public profile DTO (issue #95): username and joined date
       only, per Ryan's decision. Ratings etc. may be added later.
Author review: pending (Ryan to review before merge).
*/

package foc.user.dto;

import java.time.Instant;

public record PublicProfileResponse(
    String username,
    Instant createdAt
) {}
