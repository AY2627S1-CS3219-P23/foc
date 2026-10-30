/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: idempotent processor following notification-service's
 * IdempotentEventProcessor (its D4 event-ID dedupe, one transaction);
 * the business write here is the single eligibility row. The upsert
 * stance on a repeated requestId is the author's decision (plan Q&A),
 * flagged in the pull request.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), PR #271 review (Sinnez1): process
 * reports applied vs duplicate; an upsert that changes the parties of
 * an existing row is logged at WARN (#252's eligibility reads this
 * row); the dedupe insert persists instead of merging (ProcessedEvent).
 * Author review: [pending]
 */
package foc.rating.service;

import foc.contracts.events.request.RequestCompleted;
import foc.rating.entity.ProcessedEvent;
import foc.rating.entity.RateableRequest;
import foc.rating.repository.ProcessedEventRepository;
import foc.rating.repository.RateableRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns consumed {@code request.completed} events into eligibility
 * rows, idempotently, in a single transaction — dedupe and eligibility
 * inserts commit or roll back together.
 *
 * <p>Flow: (1) an already-seen event ID returns immediately; (2) the
 * event ID is recorded — its primary key is the backstop for a
 * concurrent duplicate, whose losing transaction rolls back at commit
 * and resolves via broker redelivery into case 1 (only reachable with
 * listener concurrency &gt; 1; the default is 1); (3) one
 * {@link RateableRequest} row is saved. {@code completed_at} is the
 * event's {@code occurredAt} — the contract has no separate completion
 * timestamp. No outward calls inside the transaction.
 *
 * <p>A second completion for the same request under a fresh eventId
 * (a producer bug or a corrected replay — order-service emits one
 * completion per request) upserts the row rather than dead-lettering:
 * {@code save} on the assigned {@code request_id} merges, last write
 * wins. One that changes who may rate whom is logged at WARN, since
 * #252's eligibility checks read this row (PR #271 review).
 */
@Service
class RatingEligibilityProcessor implements EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(RatingEligibilityProcessor.class);

    private final ProcessedEventRepository processedEvents;
    private final RateableRequestRepository rateableRequests;

    RatingEligibilityProcessor(ProcessedEventRepository processedEvents,
            RateableRequestRepository rateableRequests) {
        this.processedEvents = processedEvents;
        this.rateableRequests = rateableRequests;
    }

    @Override
    @Transactional
    public boolean process(RequestCompleted event) {
        if (processedEvents.existsById(event.eventId())) {
            return false;
        }
        processedEvents.save(new ProcessedEvent(event.eventId()));
        rateableRequests.findById(event.requestId())
                .filter(existing -> !existing.getRequesterId().equals(event.requesterId())
                        || !existing.getCourierId().equals(event.courierId()))
                .ifPresent(existing -> log.warn(
                        "request={} completion replayed with different parties:"
                                + " requester {} -> {}, courier {} -> {} (eventId={})",
                        event.requestId(), existing.getRequesterId(), event.requesterId(),
                        existing.getCourierId(), event.courierId(), event.eventId()));
        rateableRequests.save(new RateableRequest(event.requestId(), event.requesterId(),
                event.courierId(), event.occurredAt()));
        return true;
    }
}
