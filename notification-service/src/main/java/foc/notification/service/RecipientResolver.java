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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>A type with no rule stores nothing and logs at WARN (the repo's
 * logging-default rule for switches). Credit and user events land there
 * by design — this service binds neither exchange. A new request or
 * rating event needs its rule here; {@code RecipientResolverTest} checks
 * every registered type against its fixture.
 */
final class RecipientResolver {

	private static final Logger log = LoggerFactory.getLogger(RecipientResolver.class);

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
			case RequestAccepted e -> List.of(e.requesterId(), e.courierId());
			case RequestCollected e -> List.of(e.requesterId(), e.courierId());
			case RequestCompleted e -> List.of(e.requesterId(), e.courierId());
			case RequestDisputed e -> List.of(e.requesterId(), e.courierId());
			case RequestDisputeResolved e -> List.of(e.requesterId(), e.courierId());
			case RequestExpired e -> e.courierId() == null
					? List.of(e.requesterId())
					: List.of(e.requesterId(), e.courierId());
			case RatingSubmitted e -> List.of(e.rateeId());
			default -> {
				log.warn("no recipient rule for {} (eventId {}); storing nothing",
						event.eventType(), event.eventId());
				yield List.of();
			}
		};
	}
}
