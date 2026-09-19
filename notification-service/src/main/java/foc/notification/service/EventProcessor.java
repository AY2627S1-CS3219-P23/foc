/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: inbound port for issue #64, named by the team's D11
 * ports-and-adapters decision (docs/notification-service.md, "Broker
 * decoupling": the listener calls eventProcessor.process(...)).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.service;

import foc.contracts.events.EventEnvelope;

/**
 * Inbound port of the notification pipeline (D11): broker adapters
 * hand every consumed envelope to {@link #process}; the service layer
 * behind it has zero broker imports.
 *
 * <p>Contract for callers: a normal return means the envelope's
 * effects are durably committed (or it was a duplicate/stale event and
 * was deliberately discarded) — safe to acknowledge. A thrown
 * exception means nothing was committed — safe to redeliver (NFR1.1).
 */
public interface EventProcessor {

    void process(EventEnvelope envelope);
}
