/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-08.
 * Scope: credit-domain event marker interface, mirroring RequestEvent,
 * for the Order-Credit saga in the team's design
 * (docs/credit-service.md D7-D8); package placement per D22
 * (docs/notification-service.md).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.contracts.events.credit;

import foc.contracts.events.core.DomainEvent;

/**
 * A credit-domain event: every record the Credit Service publishes
 * implements this, giving consumers uniform typed access to the request
 * reference without knowing the concrete event class.
 */
public interface CreditEvent extends DomainEvent {

	/** The request this fact is about. */
	String requestId();

}
