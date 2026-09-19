/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: cross-service event contract constants for issue #62 per
 * team decisions D13 and D15 (docs/notification-service.md). Named
 * "events" rather than "messaging" to avoid colliding with a possible
 * future user-to-user chat feature (N6) — author's naming decision.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events;

/**
 * Broker event contract names shared by every service on the message
 * broker (decision D15). Only cross-service names belong here:
 * exchanges are the rendezvous points between producers and consumers,
 * so publisher and consumer must agree on them at compile time. Queue
 * names do NOT belong here — a queue is private to its consuming
 * service (D13).
 *
 * <p>"Events" means broker-carried domain facts ("events are facts,
 * not commands" — see the design doc); not to be confused with any
 * future user-to-user chat messaging feature (N6).
 */
public final class EventContracts {

	/**
	 * Durable <strong>topic</strong> exchange for the order domain
	 * (D16; supersedes the D14 fanout): the Order Service publishes
	 * order events here (state transitions per Order F0.2, courier
	 * arrival per Order F4.1.1-F4.1.2) and the Notification Service
	 * consumes them (D13). Routing keys are the canonical event
	 * identity strings in {@link EventTypeRegistry} (D18); future
	 * domains get their own exchanges.
	 */
	public static final String ORDER_EVENTS_EXCHANGE = "order-events";

	private EventContracts() {
	}

}
