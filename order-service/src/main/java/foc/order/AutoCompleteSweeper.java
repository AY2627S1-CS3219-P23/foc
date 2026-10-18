/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: completes requests an hour after the courier arrived when the
 * requester has not confirmed (Order F7.2), following
 * PendingTimeoutSweeper. The 1 h default (configurable) and the 5 min
 * sweep interval are the author's decisions.
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
 * Fires COMPLETE at every request that has been ARRIVED for longer than
 * the auto-complete delay, each in its own transaction. It publishes the
 * same request.completed as the requester's confirmation, so the Credit
 * Service pays the courier. A request the requester completed meanwhile
 * is refused by the state machine and skipped.
 *
 * <p>Off in tests via {@code order.auto-complete.sweep.enabled=false};
 * the tests call {@link #completeOverdue()} themselves.
 */
@Component
@ConditionalOnProperty(name = "order.auto-complete.sweep.enabled", havingValue = "true", matchIfMissing = true)
public class AutoCompleteSweeper {

    private static final Logger log = LoggerFactory.getLogger(AutoCompleteSweeper.class);

    private final RequestsRepository requestsRepository;
    private final RequestsService requestsService;
    private final Duration delay;

    public AutoCompleteSweeper(RequestsRepository requestsRepository, RequestsService requestsService,
            @Value("${order.auto-complete.after-ms}") long delayMs) {
        this.requestsRepository = requestsRepository;
        this.requestsService = requestsService;
        this.delay = Duration.ofMillis(delayMs);
    }

    @Scheduled(fixedDelayString = "${order.auto-complete.sweep.interval-ms}")
    public void completeOverdue() {
        Instant cutoff = Instant.now().minus(delay);
        for (Requests request : requestsRepository.findByStatusAndArrivedAtBefore(OrderStates.ARRIVED, cutoff)) {
            try {
                requestsService.completeAutomatically(request.getId());
                log.info("Request {} ARRIVED for over {} min without confirmation; completed",
                        request.getId(), delay.toMinutes());
            } catch (RuntimeException e) {
                // the requester completed it meanwhile
                log.info("Request {} not auto-completed: {}", request.getId(), e.getMessage());
            }
        }
    }
}
