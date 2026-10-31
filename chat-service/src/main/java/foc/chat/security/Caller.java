/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
 * Scope: the caller as the chat rules see it: a user id, and whether the
 * role is an admin one (C13: ADMIN and OWNER read every chat).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.security;

import java.util.Set;

/**
 * Who is asking: the platform user id from the token's {@code sub}, and
 * whether their role may read any chat (C13). The same for REST calls
 * and STOMP sessions.
 */
public record Caller(long userId, boolean admin) {

    // the roles that may use the admin dashboard (user-service's Role)
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "OWNER");

    /**
     * @throws NumberFormatException if the subject is not a numeric user id
     */
    public static Caller of(String subject, String role) {
        return new Caller(Long.parseLong(subject), ADMIN_ROLES.contains(role));
    }
}
