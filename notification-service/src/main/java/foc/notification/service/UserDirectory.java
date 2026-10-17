/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17.
 * Scope: username-lookup port for issue #66 per the author's D30
 * decision (options Q&A, docs/notification-service.md): events carry
 * party IDs only, and notification-service resolves display names at
 * ingest. A port so the processor's tests stub it (the D11 "testing
 * follows the port" rule).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.service;

import java.util.Optional;

/**
 * Resolves a party's user id to its display username (D30). Lookups
 * are best-effort by contract: an unknown id, an unreachable
 * user-service or a disabled adapter all answer empty — never an
 * exception — because a missing display name must never fail the
 * ingest transaction (the note's rationale, issue #165).
 */
public interface UserDirectory {

	Optional<String> usernameOf(String userId);
}
