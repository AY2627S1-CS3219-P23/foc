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
 * The Credit Service paid a completed request's held reward to its
 * courier: the requester's reserved balance went down by {@code amount}
 * and the courier's available balance up by it. Identity
 * {@code credit.transferred} (see {@link EventTypeRegistry}).
 */
public record CreditTransferred(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		String requestId,
		String requesterId,
		String courierId,
		Integer amount) implements CreditEvent {
}
