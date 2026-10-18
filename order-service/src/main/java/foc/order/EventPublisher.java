/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: a copy of credit-service's ReplyPublisher, renamed: what the
 * outbox publisher needs from the broker, so that no class outside
 * messaging.rabbitmq imports a broker type.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

import foc.order.model.OutboxEvent;

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
