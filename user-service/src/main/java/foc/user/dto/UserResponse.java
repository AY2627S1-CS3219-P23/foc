/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated response DTO that exposes user fields without the password hash.
Author review: Ryan validated that the endpoint logic matches the feature design.
2026-09-25 (Claude Code, Opus 5.5), PR #131 review: from(User) factory added so
owner setup and profile lookups share one mapping.
*/

package foc.user.dto;

import java.time.Instant;

import foc.user.entity.User;

public record UserResponse(
    Long id,
    String email,
    String username,
    String role,
    Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getRole().name(),
            user.getCreatedAt()
        );
    }
}
