/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-08.
 * Scope: typed credit event record for the Order-Credit saga, written
 * from the team's design (docs/credit-service.md D7-D8). The identity
 * string and the fields are the team's decisions; the tool transcribed
 * them into the existing record pattern.
 * 2026-10-09, PR #163 Copilot review: amount changed from int to Integer
 * (team decision) so a missing value is null and consumers reject it.
 * 2026-10-21, issue #234: reasonCode added, @Nullable per D18 (team
 * decision, DSP-12); its values as String constants plus a REASON_CODES
 * set (team decision).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.contracts.events.credit;

import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.core.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The Credit Service refused to reserve a request's reward (saga step
 * 2); nothing was held. {@code reason} says why, for people to read;
 * {@code reasonCode} says why, for code: one of the constants below,
 * all listed in {@link #REASON_CODES}.
 * Identity {@code credit.reservation-rejected} (see
 * {@link EventTypeRegistry}).
 *
 * <p>{@code reasonCode} is always set by credit-service from this
 * version on; it is null only on messages published before it (D18:
 * a field added to an existing event is nullable).
 */
public record CreditReservationRejected(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String requestId,
		String requesterId,
		Integer amount,
		String reason,
		@Nullable String reasonCode) implements CreditEvent {

	public static final String INSUFFICIENT_CREDITS = "INSUFFICIENT_CREDITS";
	public static final String INVALID_AMOUNT = "INVALID_AMOUNT";

	/** Every valid {@code reasonCode}. */
	public static final Set<String> REASON_CODES = Set.of(INSUFFICIENT_CREDITS, INVALID_AMOUNT);
}
