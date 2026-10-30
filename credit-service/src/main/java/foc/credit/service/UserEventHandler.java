/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267.
 * Scope: applies user.created and user.purged in one transaction each,
 * per the team's state table (USR-6): duplicates and reordering are
 * decided by the account's state, with no processed-events table.
 * Same day, PR review (team decision): a blank userId answers NO_USER
 * before any query, so it is dead-lettered instead of retried.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import foc.contracts.events.user.UserCreated;
import foc.contracts.events.user.UserEvent;
import foc.contracts.events.user.UserPurged;

/**
 * Applies one user event in one transaction (USR-6). Every outcome of
 * {@link CreditOperations#open} and {@link CreditOperations#close} is
 * acknowledged, a reordered pair included: none conflicts with what the
 * account holds. An event naming no user is {@link AccountResult#NO_USER}
 * and is dead-lettered. The listener only converts the message, calls
 * this and settles the delivery, so no broker type is imported here.
 */
@Service
public class UserEventHandler {

    private static final Logger log = LoggerFactory.getLogger(UserEventHandler.class);

    private final CreditOperations operations;

    public UserEventHandler(CreditOperations operations) {
        this.operations = operations;
    }

    // an exception rolls the transaction back and reaches the listener,
    // which sends the event to the retry queue
    @Transactional
    public AccountResult handle(UserEvent event) {
        // Before any query, as a resolution naming no courier is: the
        // converter refuses a missing userId, and a blank one names no
        // account, which no retry can change.
        if (event.userId().isBlank()) {
            return AccountResult.NO_USER;
        }
        return switch (event) {
            case UserCreated e -> operations.open(e.userId());
            case UserPurged e -> operations.close(e.userId());
            // A user event the Credit Service has no part in. Only the two
            // events above are bound to the queue, so this is one published
            // to it by hand: acknowledged, but logged so it can be traced.
            default -> {
                log.warn("No handler for user event {} (eventId {}); acknowledged without action",
                    event.getClass().getSimpleName(), event.eventId());
                yield AccountResult.DUPLICATE;
            }
        };
    }
}
