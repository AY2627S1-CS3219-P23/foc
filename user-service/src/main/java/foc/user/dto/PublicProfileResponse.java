/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-23
Scope: Generated public profile DTO (issue #95): username and joined date
       only. Ratings etc. may be added later in future PR or features.
Author review: Ryan reviewed to ensure it follows team's decision.
*/

package foc.user.dto;

import java.time.Instant;

public record PublicProfileResponse(
    String username,
    Instant createdAt
) {}
