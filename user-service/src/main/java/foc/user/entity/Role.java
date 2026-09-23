/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-23.
 * Scope: role enum extracted for issue #86 from the merged String
 * role column (PR #126), per the design doc's role matrix. Enum
 * representation chosen by Leong Wei Zhi via options Q&A; OWNER kept
 * per the merged owner-bootstrap feature (issue #97, "admin-equivalent
 * super admin").
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.user.entity;

/**
 * Platform roles (design doc Part 1 §1). Stored as text; the CHECK
 * constraint on {@code users.role} keeps the column in sync with
 * these constants. Requester/courier are modes of {@code USER}, not
 * roles.
 */
public enum Role {
    USER,
    ADMIN,
    OWNER
}
