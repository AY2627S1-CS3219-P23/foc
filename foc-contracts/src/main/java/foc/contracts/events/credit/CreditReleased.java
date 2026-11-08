/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-08, Credit NFR1.1 (PR #313).
 * Scope: typed credit event record for the credit-change notification.
 * The identity string and the fields are team decisions (the fields
 * mirror CreditReserved's); the tool transcribed them into the record
 * pattern.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.contracts.events.credit;

import foc.contracts.events.core.EventTypeRegistry;

import java.time.Instant;

/**
 * The Credit Service returned a cancelled or expired request's held
 * reward to its requester's available balance. Identity
 * {@code credit.released} (see {@link EventTypeRegistry}).
 */
public record CreditReleased(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		String requestId,
		String requesterId,
		Integer amount) implements CreditEvent {
}
