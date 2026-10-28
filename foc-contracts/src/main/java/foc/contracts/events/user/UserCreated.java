/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267.
 * Scope: typed user event record, written from the team's decisions
 * (USR-1, USR-8). The identity string and the fields are the team's
 * decisions; the tool transcribed them into the existing record pattern.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.contracts.events.user;

import foc.contracts.events.core.EventTypeRegistry;

import java.time.Instant;
import java.util.List;

/**
 * The User Service created a user account: its row was inserted, by a
 * verified sign-up or owner setup, or by the demo seeder (USR-8). Identity {@code user.created} (see
 * {@link EventTypeRegistry}). Carries no personal data; {@code parties}
 * is always empty.
 */
public record UserCreated(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String userId) implements UserEvent {
}
