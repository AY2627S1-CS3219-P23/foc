/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: RESERVE, REJECT and TIMEOUT added to the author's enum for the
 * saga with the Credit Service (team design, docs/credit-service.md
 * D7-D8); the names are the author's. The other constants and their
 * comments are the author's.
 * 2026-10-16: ARRIVE added for the author's ARRIVED state
 * (COLLECTED -> ARRIVED, then COMPLETE).
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

public enum OrderEvents {
    CREATE,//user creates the order
    ACCEPT,//a courier accepts the order, order must be in create initially, or accepted before getting DROP
    COLLECT,//courier collects order from supplier, ACCEPTED -> COLLECTED
    CANCEL, // Requester cancelling request before it has been accpeted, (publish event, release reserved credits back to requester)
    DROP, // Courier cancelling accpeted request, only before collect stage
    EXPIRE,// Only before request has been collected!
    ARRIVE, // courier reached the drop-off location, COLLECTED -> ARRIVED; tells the requester
    COMPLETE,//Reuqester and courier meet, publishes event (credit payout)
    RESERVE, // Credit Service reserved the reward (credit.reserved), PENDING -> CREATED
    REJECT, // Credit Service refused the reward (credit.reservation-rejected), PENDING -> REJECTED
    TIMEOUT // no reply from the Credit Service in time, PENDING -> CANCELLED
}
