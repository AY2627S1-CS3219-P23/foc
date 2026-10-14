/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-12, issue #170.
 * Scope: what the outbox publisher needs from the broker, so that
 * service/ imports no broker type; the implementation is in
 * messaging/rabbitmq. The three results follow the team's decision on
 * what a failed publish does to the rest of a poll.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import foc.credit.entity.OutboxEvent;

/** Sends one stored reply to the broker (D10). */
public interface ReplyPublisher {

    /** What became of one publish. */
    enum Result {
        // the broker confirmed the reply and did not return it
        SENT,
        // the broker confirmed this reply but returned it as unroutable:
        // the one failure that is about this reply, so the others may
        // still go through
        NOT_SENT,
        // the broker could not be reached, the connection was lost, or
        // the reply was nacked or not confirmed in time: the broker is
        // in trouble, and every other reply would fail the same way
        BROKER_UNAVAILABLE
    }

    Result publish(OutboxEvent reply);
}
