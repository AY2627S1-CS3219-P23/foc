/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: login response (issues #89/#90); camelCase fields per team decision.
       expiresIn is in seconds.
Author review: Ryan to review via the PR.
2026-10-11 (Claude Code, Fable 5), issue #94: deletionScheduledFor — set
only when a soft-deleted account logs in inside its 30-day window, since
login no longer silently recovers it (owner decision via options Q&A).
The SPA keys the restore-or-keep-deleted screen on this field; omitted
(not null) for active accounts so existing clients see the exact body
they always did.
*/

package foc.user.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    // when the purge will take the still-deleted account; absent for an
    // active one
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Instant deletionScheduledFor
) {
    public static LoginResponse bearer(String accessToken, long expiresInSeconds) {
        return bearer(accessToken, expiresInSeconds, null);
    }

    public static LoginResponse bearer(String accessToken, long expiresInSeconds,
            Instant deletionScheduledFor) {
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds, deletionScheduledFor);
    }
}
