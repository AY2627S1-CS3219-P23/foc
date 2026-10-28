/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267.
 * Scope: typed user event record, written from the team's decisions
 * (USR-1, USR-2). The identity string and the fields are the team's
 * decisions; the tool transcribed them into the existing record pattern.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.contracts.events.user;

import foc.contracts.events.core.EventTypeRegistry;

import java.time.Instant;
import java.util.List;

/**
 * The User Service permanently removed a user account: the day-31 purge
 * of a soft-deleted account ("deleted" is the soft delete in
 * user-service; this is the irreversible step). Identity
 * {@code user.purged} (see {@link EventTypeRegistry}). Carries no
 * personal data; {@code parties} is always empty.
 */
public record UserPurged(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String userId) implements UserEvent {
}
