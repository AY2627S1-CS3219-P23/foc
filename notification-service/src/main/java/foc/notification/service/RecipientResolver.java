/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-04.
 * Scope: the recipient rule per event type, implementing the author's
 * D34 decision (options Q&A, docs/notification-service.md): events
 * carry business facts only, and this service decides who is told. The
 * rules reproduce, unchanged, the lists the publishers used to stamp
 * into the removed parties field (order-service RequestEvents,
 * rating-service RatingEvents).
 * Author review: Leong Wei Zhi, pending pull request review.
 * PR #287 review (@claude bot): the two-party arms go through a
 * null-safe helper — a missing courier tells the requester alone
 * instead of List.of throwing inside process().
 * PR #287 review (@Sinnez1): reversed for the five events whose courier
 * is required — a missing one fails loudly, as does a type without a
 * rule; the processor resolves recipients before its first write, so the
 * message dead-letters and stays replayable.
 * 2026-11-04, follow-up to PR #287 (author's call): the explicit credit
 * and user arms removed — this service binds neither exchange, so one
 * that arrives is refused like any type without a rule.
 */
package foc.notification.service;

import foc.contracts.events.core.DomainEvent;
import foc.contracts.events.rating.RatingSubmitted;
import foc.contracts.events.request.CourierArrived;
import foc.contracts.events.request.RequestAccepted;
import foc.contracts.events.request.RequestCancelled;
import foc.contracts.events.request.RequestCollected;
import foc.contracts.events.request.RequestCompleted;
import foc.contracts.events.request.RequestCreated;
import foc.contracts.events.request.RequestDisputeResolved;
import foc.contracts.events.request.RequestDisputed;
import foc.contracts.events.request.RequestDropped;
import foc.contracts.events.request.RequestExpired;
import foc.contracts.events.request.RequestRejected;
import foc.contracts.events.request.RequestSubmitted;
import java.util.List;

/**
 * Who a consumed event notifies (D34), read off the event's own id
 * fields — no outward call (F1.1). One rule per event type; the table
 * lives in docs/notification-service.md, "Recipient resolution (D34)".
 *
 * <p>The requester alone: submitted, created, rejected, cancelled,
 * courier-arrived and dropped (the dropping courier is named in
 * {@code courierId} but not told — their rows are pruned instead, D33).
 * The requester and the courier: accepted, collected, completed,
 * disputed and dispute-resolved. Expired: the requester, plus the
 * courier who had accepted when there was one. Rating submitted: the
 * ratee alone (#44).
 *
 * <p>Only {@code request.expired} may lack a courier (the request was
 * still open). On the five events that require one, a null courier is a
 * contract violation: the converter already dead-letters such a body
 * (D21), and this resolver throws too, so a caller on the port cannot
 * quietly lose the courier's row. A registered type with no rule throws
 * as well. {@link IdempotentEventProcessor} resolves recipients before
 * its first write, so either throw leaves nothing behind and the
 * listener's retry/DLQ path keeps the message replayable once the rule
 * or the producer is fixed (PR #287 review). Credit and user events take
 * that path too: this service binds neither exchange (D16), so one can
 * only arrive by hand or by a rebinding, and dead-lettering it is the
 * visible outcome. {@code RecipientResolverTest} checks every registered
 * request and rating type against its fixture.
 */
final class RecipientResolver {

	private RecipientResolver() {
	}

	static List<String> recipientsOf(DomainEvent event) {
		return switch (event) {
			case RequestSubmitted e -> List.of(e.requesterId());
			case RequestCreated e -> List.of(e.requesterId());
			case RequestRejected e -> List.of(e.requesterId());
			case RequestCancelled e -> List.of(e.requesterId());
			case CourierArrived e -> List.of(e.requesterId());
			case RequestDropped e -> List.of(e.requesterId());
			case RequestAccepted e -> both(e, e.requesterId(), e.courierId());
			case RequestCollected e -> both(e, e.requesterId(), e.courierId());
			case RequestCompleted e -> both(e, e.requesterId(), e.courierId());
			case RequestDisputed e -> both(e, e.requesterId(), e.courierId());
			case RequestDisputeResolved e -> both(e, e.requesterId(), e.courierId());
			// the one event whose courier is optional: null while the request was still open
			case RequestExpired e -> e.courierId() == null
					? List.of(e.requesterId())
					: List.of(e.requesterId(), e.courierId());
			case RatingSubmitted e -> List.of(e.rateeId());
			default -> throw new IllegalStateException(
					"no recipient rule for " + event.eventType() + " (eventId " + event.eventId() + ")");
		};
	}

	// A required courier that is missing is a contract violation the
	// converter already rejects (D21); on the port it fails loudly too,
	// before anything is written, instead of quietly dropping the
	// courier's row (PR #287 review).
	private static List<String> both(DomainEvent event, String requesterId, String courierId) {
		if (courierId == null) {
			throw new IllegalStateException(
					event.eventType() + " (eventId " + event.eventId() + ") has no courierId");
		}
		return List.of(requesterId, courierId);
	}
}
