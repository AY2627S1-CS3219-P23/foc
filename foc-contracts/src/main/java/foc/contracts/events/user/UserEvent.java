/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267.
 * Scope: user-domain event marker interface, mirroring CreditEvent, for
 * the user events in the team's decisions (USR-1).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.contracts.events.user;

import foc.contracts.events.core.DomainEvent;

/**
 * A user-domain event: every record the User Service publishes
 * implements this, giving consumers uniform typed access to the user
 * reference without knowing the concrete event class.
 */
public interface UserEvent extends DomainEvent {

	/** The user this fact is about. */
	String userId();

}
