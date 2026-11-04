/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267.
 * Scope: typed user event record, written from the team's decisions
 * (USR-1, USR-2). The identity string and the fields are the team's
 * decisions; the tool transcribed them into the existing record pattern.
 * Author review: Ryan Ang, pending pull request review.
 * 2026-11-04, Claude Code (Fable 5.1), D34 (author decision): the
 * always-empty parties component removed. Author review: Leong Wei Zhi,
 * pending pull request review.
 */
package foc.contracts.events.user;

import foc.contracts.events.core.EventTypeRegistry;

import java.time.Instant;

/**
 * The User Service permanently removed a user account: the day-31 purge
 * of a soft-deleted account ("deleted" is the soft delete in
 * user-service; this is the irreversible step). Identity
 * {@code user.purged} (see {@link EventTypeRegistry}). Carries no
 * personal data; the Notification Service does not consume user events.
 */
public record UserPurged(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		String userId) implements UserEvent {
}
