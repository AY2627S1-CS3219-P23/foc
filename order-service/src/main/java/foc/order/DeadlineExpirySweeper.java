/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: expires requests whose deadline has passed before collection
 * (Order F6.1-F6.2), following PendingTimeoutSweeper. The 5 min sweep
 * interval is the author's decision; EXPIRE from CREATED and ACCEPTED is
 * the state machine's.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

import foc.order.model.Requests;
import foc.order.repository.RequestsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Fires EXPIRE at every CREATED or ACCEPTED request whose deadline has
 * passed, each in its own transaction; request.expired then releases
 * the reward to the requester. A request that moved on meanwhile (e.g.
 * collected) is refused by the state machine and skipped.
 *
 * <p>Off in tests via {@code order.expiry.sweep.enabled=false}; the
 * tests call {@link #expireOverdue()} themselves.
 */
@Component
@ConditionalOnProperty(name = "order.expiry.sweep.enabled", havingValue = "true", matchIfMissing = true)
public class DeadlineExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(DeadlineExpirySweeper.class);

    // the states EXPIRE is allowed from (OrderStateMachine)
    private static final List<OrderStates> EXPIRABLE = List.of(OrderStates.CREATED, OrderStates.ACCEPTED);

    private final RequestsRepository requestsRepository;
    private final RequestsService requestsService;

    public DeadlineExpirySweeper(RequestsRepository requestsRepository, RequestsService requestsService) {
        this.requestsRepository = requestsRepository;
        this.requestsService = requestsService;
    }

    @Scheduled(fixedDelayString = "${order.expiry.sweep.interval-ms}")
    public void expireOverdue() {
        for (Requests request : requestsRepository.findByStatusInAndDeadlineBefore(EXPIRABLE, Instant.now())) {
            try {
                requestsService.expire(request.getId());
                log.info("Request {} passed its deadline {}; expired", request.getId(), request.getDeadline());
            } catch (RuntimeException e) {
                // it moved on meanwhile; the next sweep sees its new state
                log.info("Request {} not expired: {}", request.getId(), e.getMessage());
            }
        }
    }
}
