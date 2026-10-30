/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: inbound port, following notification-service's EventProcessor
 * (its D11 ports-and-adapters decision) with the signature narrowed to
 * RequestCompleted — the one event this service consumes.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), PR #271 review (Sinnez1): returns
 * whether the event was applied, so the listener's log can tell an
 * applied event from a discarded duplicate.
 * Author review: [pending]
 */
package foc.rating.service;

import foc.contracts.events.request.RequestCompleted;

/**
 * Inbound port of the eligibility pipeline: the broker adapter hands
 * every consumed {@code request.completed} to {@link #process}; the
 * service layer behind it has zero broker imports.
 *
 * <p>Contract for callers: a normal return means the event's effects
 * are durably committed ({@code true}), or it was an already-seen
 * duplicate and was deliberately discarded ({@code false}) — either
 * way, safe to acknowledge. A thrown exception means nothing was
 * committed — safe to redeliver.
 */
public interface EventProcessor {

    boolean process(RequestCompleted event);
}
