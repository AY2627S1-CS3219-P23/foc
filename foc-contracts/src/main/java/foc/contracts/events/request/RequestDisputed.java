/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-21.
 * Scope: typed event record for N3 dispute resolution (issue #234),
 * written from the team's decisions (DSP-9, DSP-12): the identity
 * string, the fields, their nullability and the category values are the
 * team's; the tool transcribed them into the existing record pattern.
 * The values as String constants plus a CATEGORIES set: team decision.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.contracts.events.request;

import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.core.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The requester disputed the request, or the expiry sweep escalated it
 * because it was still collected a grace period after its deadline; the
 * reward stays held until an admin resolves it. Identity
 * {@code request.disputed} (see {@link EventTypeRegistry}).
 *
 * <p>{@code note} is the request's own note, as on every request event;
 * the requester's dispute text is {@code disputeNote}. {@code category}
 * is one of the constants below; {@link #CATEGORIES} lists them all.
 */
public record RequestDisputed(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String requestId,
		String requesterId,
		String courierId,
		String pickupLocation,
		String dropoffLocation,
		@Nullable String note,
		String category,
		@Nullable String disputeNote) implements RequestEvent {

	public static final String NOT_DELIVERED = "NOT_DELIVERED";
	public static final String WRONG_ITEM = "WRONG_ITEM";
	public static final String DAMAGED = "DAMAGED";
	public static final String OTHER = "OTHER";

	/** Every valid {@code category}: Order's API refuses anything else. */
	public static final Set<String> CATEGORIES = Set.of(NOT_DELIVERED, WRONG_ITEM, DAMAGED, OTHER);
}
