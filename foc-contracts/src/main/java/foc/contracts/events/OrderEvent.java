/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: order-domain event refinement of DomainEvent, per the
 * author's Method-B decisions (docs/notification-service.md D16-D19).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events;

/**
 * An order-domain event: every order-lifecycle record implements this,
 * giving consumers uniform typed access to the order reference without
 * knowing the concrete event class.
 */
public interface OrderEvent extends DomainEvent {

	/** The order this fact is about. */
	String orderId();

}
