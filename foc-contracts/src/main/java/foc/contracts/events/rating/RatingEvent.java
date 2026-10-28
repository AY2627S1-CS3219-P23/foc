/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5), 2026-10-28.
 * Scope: rating-domain event marker interface, mirroring CreditEvent,
 * for the N1 rating system (issue #249); package placement per D22
 * (docs/notification-service.md). The scope decisions are the issue
 * author's, recorded on #44.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.contracts.events.rating;

import foc.contracts.events.core.DomainEvent;

/**
 * A rating-domain event: every record the Rating Service publishes
 * implements this, giving consumers uniform typed access to the request
 * reference without knowing the concrete event class.
 */
public interface RatingEvent extends DomainEvent {

	/** The completed request this rating is about. */
	String requestId();

}
