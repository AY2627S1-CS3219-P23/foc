/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated response DTO that exposes user fields without the password hash.
Author review: Ryan validated that the endpoint logic matches the feature design.
*/

package foc.user.dto;

import java.time.Instant;

public record UserResponse(
    Long id,
    String email,
    String username,
    String role,
    Instant createdAt
) {}
