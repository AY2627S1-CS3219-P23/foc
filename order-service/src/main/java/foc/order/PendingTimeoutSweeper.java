/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: cancels requests left PENDING past the waiting time
 * (docs/credit-service.md, "What the other services must build"). The
 * TIMEOUT event, the 30 s waiting time and the 15 s sweep interval are
 * the author's decisions.
 * 2026-10-16: queries by the OrderStates enum.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

import foc.order.model.Requests;
import foc.order.repository.RequestsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Fires TIMEOUT at every request that has been PENDING longer than the
 * waiting time, each in its own transaction, so a lost or dead-lettered
 * reply cannot strand a request. A reply that lands first wins: the
 * request is no longer PENDING (or its version moved on), and that one
 * is skipped.
 *
 * <p>Off in tests via {@code order.pending.sweep.enabled=false}; the
 * tests call {@link #cancelOverdue()} themselves.
 */
@Component
@ConditionalOnProperty(name = "order.pending.sweep.enabled", havingValue = "true", matchIfMissing = true)
public class PendingTimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(PendingTimeoutSweeper.class);

    private final RequestsRepository requestsRepository;
    private final RequestsService requestsService;
    private final Duration timeout;

    public PendingTimeoutSweeper(RequestsRepository requestsRepository, RequestsService requestsService,
            @Value("${order.pending.timeout-ms}") long timeoutMs) {
        this.requestsRepository = requestsRepository;
        this.requestsService = requestsService;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    @Scheduled(fixedDelayString = "${order.pending.sweep.interval-ms}")
    public void cancelOverdue() {
        Instant cutoff = Instant.now().minus(timeout);
        for (Requests request : requestsRepository.findByStatusAndCreatedAtBefore(OrderStates.PENDING, cutoff)) {
            try {
                requestsService.timeout(request.getId());
                log.info("Request {} still PENDING after {} s; cancelled", request.getId(), timeout.toSeconds());
            } catch (RuntimeException e) {
                // the reply arrived meanwhile; the next sweep sees its outcome
                log.info("Request {} not timed out: {}", request.getId(), e.getMessage());
            }
        }
    }
}
