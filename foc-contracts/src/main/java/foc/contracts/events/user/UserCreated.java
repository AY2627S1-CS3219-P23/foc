/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267.
 * Scope: typed user event record, written from the team's decisions
 * (USR-1, USR-8). The identity string and the fields are the team's
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
 * The User Service created a user account: its row was inserted, by a
 * verified sign-up or owner setup, or by the demo seeder (USR-8). Identity {@code user.created} (see
 * {@link EventTypeRegistry}). Carries no personal data; the Notification
 * Service does not consume user events.
 */
public record UserCreated(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		String userId) implements UserEvent {
}
