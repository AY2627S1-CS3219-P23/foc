/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: cross-service messaging contract constants for issue #62 per
 * team decisions D13 and D15 (docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.messaging;

/**
 * Broker contract names shared by every service on the message broker
 * (decision D15). Only cross-service names belong here: exchanges are
 * the rendezvous points between producers and consumers, so publisher
 * and consumer must agree on them at compile time. Queue names do NOT
 * belong here — a queue is private to its consuming service (D13).
 */
public final class MessagingContracts {

	/**
	 * Durable fanout exchange the Order Service publishes order events
	 * to (state transitions per Order F0.2, courier arrival per Order
	 * F4.1.1-F4.1.2); the Notification Service consumes them (D13,
	 * D14).
	 */
	public static final String ORDER_EVENTS_EXCHANGE = "order-events";

	private MessagingContracts() {
	}

}
