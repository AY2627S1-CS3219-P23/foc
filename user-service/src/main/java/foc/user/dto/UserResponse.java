/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated response DTO that exposes user fields without the password hash.
Author review: Ryan validated that the endpoint logic matches the feature design.
2026-09-25 (Claude Code, Opus 5.5), PR #131 review: from(User) factory added so
owner setup and profile lookups share one mapping.
2026-09-29 (Claude Code, Opus 5.5), issue #147: deletedAt added for the
admin list's removed accounts (team decision); left out of the JSON while
null, so other responses keep their shape.
*/

package foc.user.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import foc.user.entity.User;

public record UserResponse(
    Long id,
    String email,
    String username,
    String role,
    Instant createdAt,
    // null (and omitted) unless the account is soft-deleted
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Instant deletedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getRole().name(),
            user.getCreatedAt(),
            user.getDeletedAt()
        );
    }
}
