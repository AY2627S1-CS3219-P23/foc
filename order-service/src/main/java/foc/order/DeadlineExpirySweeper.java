/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: expires requests whose deadline has passed before collection
 * (Order F6.1-F6.2), following PendingTimeoutSweeper. The 5 min sweep
 * interval is the author's decision; EXPIRE from CREATED and ACCEPTED is
 * the state machine's.
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-26, Claude Code (Opus 5.5), issue #239: the same sweep
 * escalates requests still COLLECTED a grace period after their deadline
 * to DISPUTED (team decision DSP-6, docs/disputes.md). Author review:
 * Ryan Ang, pending pull request review.
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
import java.util.List;

/**
 * Fires EXPIRE at every CREATED or ACCEPTED request whose deadline has
 * passed, each in its own transaction; request.expired then releases
 * the reward to the requester. A request that moved on meanwhile (e.g.
 * collected) is refused by the state machine and skipped.
 *
 * <p>The same sweep fires ESCALATE at every request still COLLECTED the
 * grace period after its deadline (DSP-6): it is disputed as not
 * delivered, and request.disputed tells both parties. A request the
 * courier marked arrived meanwhile is refused and skipped likewise.
 *
 * <p>Off in tests via {@code order.expiry.sweep.enabled=false}; the
 * tests call {@link #expireOverdue()} and {@link #escalateOverdue()}
 * themselves.
 */
@Component
@ConditionalOnProperty(name = "order.expiry.sweep.enabled", havingValue = "true", matchIfMissing = true)
public class DeadlineExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(DeadlineExpirySweeper.class);

    // the states EXPIRE is allowed from (OrderStateMachine)
    private static final List<OrderStates> EXPIRABLE = List.of(OrderStates.CREATED, OrderStates.ACCEPTED);

    // the state ESCALATE is allowed from (OrderStateMachine)
    private static final List<OrderStates> ESCALATABLE = List.of(OrderStates.COLLECTED);

    private final RequestsRepository requestsRepository;
    private final RequestsService requestsService;
    private final Duration escalationGrace;

    public DeadlineExpirySweeper(RequestsRepository requestsRepository, RequestsService requestsService,
            @Value("${order.expiry.dispute-escalation-grace-ms}") long escalationGraceMs) {
        this.requestsRepository = requestsRepository;
        this.requestsService = requestsService;
        this.escalationGrace = Duration.ofMillis(escalationGraceMs);
    }

    @Scheduled(fixedDelayString = "${order.expiry.sweep.interval-ms}")
    public void sweep() {
        expireOverdue();
        escalateOverdue();
    }

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

    public void escalateOverdue() {
        Instant cutoff = Instant.now().minus(escalationGrace);
        for (Requests request : requestsRepository.findByStatusInAndDeadlineBefore(ESCALATABLE, cutoff)) {
            try {
                requestsService.escalate(request.getId());
                log.info("Request {} still collected {} min after its deadline {}; disputed",
                        request.getId(), escalationGrace.toMinutes(), request.getDeadline());
            } catch (RuntimeException e) {
                // the courier marked it arrived meanwhile
                log.info("Request {} not escalated: {}", request.getId(), e.getMessage());
            }
        }
    }
}
