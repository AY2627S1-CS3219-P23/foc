/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: idempotent processor following notification-service's
 * IdempotentEventProcessor (its D4 event-ID dedupe, one transaction);
 * the business write here is the single eligibility row. The upsert
 * stance on a repeated requestId is the author's decision (plan Q&A),
 * flagged in the pull request.
 * Author review: [pending]
 */
package foc.rating.service;

import foc.contracts.events.request.RequestCompleted;
import foc.rating.entity.ProcessedEvent;
import foc.rating.entity.RateableRequest;
import foc.rating.repository.ProcessedEventRepository;
import foc.rating.repository.RateableRequestRepository;
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
 * wins.
 */
@Service
class RatingEligibilityProcessor implements EventProcessor {

    private final ProcessedEventRepository processedEvents;
    private final RateableRequestRepository rateableRequests;

    RatingEligibilityProcessor(ProcessedEventRepository processedEvents,
            RateableRequestRepository rateableRequests) {
        this.processedEvents = processedEvents;
        this.rateableRequests = rateableRequests;
    }

    @Override
    @Transactional
    public void process(RequestCompleted event) {
        if (processedEvents.existsById(event.eventId())) {
            return;
        }
        processedEvents.save(new ProcessedEvent(event.eventId()));
        rateableRequests.save(new RateableRequest(event.requestId(), event.requesterId(),
                event.courierId(), event.occurredAt()));
    }
}
