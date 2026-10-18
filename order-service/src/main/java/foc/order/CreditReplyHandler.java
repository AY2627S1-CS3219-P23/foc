/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: applies the Credit Service's reservation reply to its request
 * (saga steps 3a/3b, docs/credit-service.md). Ignoring a reply for a
 * request that is no longer PENDING is the team's design; dead-lettering
 * a reply for an unknown or unreadable request id follows the copied
 * D11 rule that events conflicting with the recorded state go to the
 * dead-letter queue (the tool's reading, for the author to confirm).
 * 2026-10-16: compares the OrderStates enum status directly.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

import foc.contracts.events.credit.CreditEvent;
import foc.contracts.events.credit.CreditReservationRejected;
import foc.contracts.events.credit.CreditReserved;
import foc.order.model.Requests;
import foc.order.repository.RequestsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Applies one Credit Service reply. The state change and its request
 * event commit together in {@link RequestsService}'s transaction. The
 * listener only converts the message, calls this and settles the
 * delivery on the outcome, so no broker type is imported here.
 */
@Service
public class CreditReplyHandler {

    /** What the listener does with the delivery once the reply is applied. */
    public enum Outcome {
        // applied, or nothing to do: acknowledge
        HANDLED,
        // names no request this service holds: dead-letter, no retry
        UNKNOWN_REQUEST
    }

    private static final Logger log = LoggerFactory.getLogger(CreditReplyHandler.class);

    private final RequestsRepository requestsRepository;
    private final RequestsService requestsService;

    public CreditReplyHandler(RequestsRepository requestsRepository, RequestsService requestsService) {
        this.requestsRepository = requestsRepository;
        this.requestsService = requestsService;
    }

    // An exception (e.g. a timeout committing first, failing the version
    // check) reaches the listener, which sends the reply to the retry
    // queue; the retry then finds the request no longer PENDING.
    public Outcome handle(CreditEvent reply) {
        Optional<Requests> request = parseId(reply.requestId()).flatMap(requestsRepository::findById);
        if (request.isEmpty()) {
            log.warn("Reply {} (eventId {}) names unknown request {}; dead-lettered",
                    reply.eventType(), reply.eventId(), reply.requestId());
            return Outcome.UNKNOWN_REQUEST;
        }
        Long id = request.get().getId();
        if (request.get().getStatus() != OrderStates.PENDING) {
            // a repeated reply, or one that arrived after the timeout
            log.info("Reply {} (eventId {}) for request {}, which is {}; ignored",
                    reply.eventType(), reply.eventId(), id, request.get().getStatus());
            return Outcome.HANDLED;
        }
        switch (reply) {
            case CreditReserved r -> requestsService.reserve(id);
            case CreditReservationRejected r -> requestsService.reject(id, r.reason());
            // a credit event this service has no part in: only the two
            // replies are bound to the queue, so this was published to it
            // by hand. Acknowledged, but logged so it can be traced.
            default -> log.warn("No handler for credit event {} (eventId {}); acknowledged without action",
                    reply.getClass().getSimpleName(), reply.eventId());
        }
        return Outcome.HANDLED;
    }

    private static Optional<Long> parseId(String requestId) {
        try {
            return Optional.of(Long.valueOf(requestId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
