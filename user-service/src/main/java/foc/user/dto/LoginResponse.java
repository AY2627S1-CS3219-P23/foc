/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: login response (issues #89/#90); camelCase fields per team decision.
       expiresIn is in seconds.
Author review: Ryan to review via the PR.
*/

package foc.user.dto;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {
    public static LoginResponse bearer(String accessToken, long expiresInSeconds) {
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
