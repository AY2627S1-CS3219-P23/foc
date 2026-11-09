/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-08, Credit NFR1.1 (PR #313).
 * Scope: typed credit event record for the credit-change notification.
 * The identity string and the fields are team decisions; the tool
 * transcribed them into the record pattern.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.contracts.events.credit;

import foc.contracts.events.core.EventTypeRegistry;

import java.time.Instant;

/**
 * The Credit Service split a disputed request's held reward as the
 * admin resolved it: {@code courierAmount} to the courier's available
 * balance and {@code requesterAmount} back to the requester's. Either
 * share may be 0; together they are the reward. Identity
 * {@code credit.dispute-settled} (see {@link EventTypeRegistry}).
 */
public record CreditDisputeSettled(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		String requestId,
		String requesterId,
		String courierId,
		Integer courierAmount,
		Integer requesterAmount) implements CreditEvent {
}
