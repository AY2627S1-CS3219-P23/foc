/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: a copy of order-service's foc.order.EventPublisher, package
 * changed: what the outbox publisher needs from the broker, so that no
 * class outside messaging.rabbitmq imports a broker type.
 * Author review: [pending]
 */
package foc.rating.service;

import foc.rating.entity.OutboxEvent;

/** Sends one stored event to the broker. */
public interface EventPublisher {

    /** What became of one publish. */
    enum Result {
        // the broker confirmed the event and did not return it
        SENT,
        // the broker confirmed this event but returned it as unroutable:
        // the one failure that is about this event, so the others may
        // still go through
        NOT_SENT,
        // the broker could not be reached, the connection was lost, or
        // the event was nacked or not confirmed in time: the broker is
        // in trouble, and every other event would fail the same way
        BROKER_UNAVAILABLE
    }

    Result publish(OutboxEvent event);
}
