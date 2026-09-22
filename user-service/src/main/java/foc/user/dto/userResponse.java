package foc.user.dto;

import java.time.Instant;

public record UserResponse(
    Long id,
    String email,
    String username,
    String role,
    Instant createdAt
) {}
