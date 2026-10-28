/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-11.
 * Scope: plain-Java transition table over the author's OrderStates /
 * OrderEvents enums. States, events and their guard comments are the
 * author's; the DROP target (ACCEPTED -> CREATED) and the plain-Java
 * form (no state machine library) were chosen by the author.
 * 2026-10-15: the saga's PENDING transitions (docs/credit-service.md
 * D7-D8): requests now start PENDING, and only RESERVE, REJECT and
 * TIMEOUT leave it (the author's decision).
 * 2026-10-16: the author's ARRIVED state between COLLECTED and
 * COMPLETED; COMPLETE is now only allowed from ARRIVED (the author's
 * decision).
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-26, Claude Code (Opus 5.5), issue #239: the dispute transitions
 * of docs/disputes.md (team decision). Author review: Ryan Ang, pending
 * pull request review.
 */
package foc.order;

import java.util.EnumMap;
import java.util.Map;

import static foc.order.OrderEvents.*;
import static foc.order.OrderStates.*;

public final class OrderStateMachine {

    // State a request is in once CREATE has happened. CREATE has no
    // source state, so it never appears in the table below.
    public static final OrderStates INITIAL_STATE = PENDING;

    private static final Map<OrderStates, Map<OrderEvents, OrderStates>> TRANSITIONS =
            new EnumMap<>(OrderStates.class);

    static {
        // The Credit Service's reply, or no reply in time.
        permit(PENDING, RESERVE, CREATED);
        permit(PENDING, REJECT, REJECTED);
        permit(PENDING, TIMEOUT, CANCELLED);

        permit(CREATED, ACCEPT, ACCEPTED);
        permit(CREATED, CANCEL, CANCELLED);
        permit(CREATED, EXPIRE, EXPIRED);

        permit(ACCEPTED, COLLECT, COLLECTED);
        permit(ACCEPTED, DROP, CREATED);
        permit(ACCEPTED, EXPIRE, EXPIRED);

        permit(COLLECTED, ARRIVE, ARRIVED);
        // the deadline has passed: by the requester, or by the expiry
        // sweep a grace period later (RequestsService checks the times)
        permit(COLLECTED, DISPUTE, DISPUTED);
        permit(COLLECTED, ESCALATE, DISPUTED);

        permit(ARRIVED, COMPLETE, COMPLETED);
        // within the auto-complete window (RequestsService checks it)
        permit(ARRIVED, DISPUTE, DISPUTED);

        // no COMPLETE: a disputed request is never auto-completed (DSP-14)
        permit(DISPUTED, RESOLVE, RESOLVED);

        // COMPLETED, CANCELLED, EXPIRED, REJECTED and RESOLVED are
        // terminal: no transitions out.
    }

    private OrderStateMachine() {
    }

    public static boolean canFire(OrderStates current, OrderEvents event) {
        return TRANSITIONS.getOrDefault(current, Map.of()).containsKey(event);
    }

    public static OrderStates next(OrderStates current, OrderEvents event) {
        OrderStates target = TRANSITIONS.getOrDefault(current, Map.of()).get(event);
        if (target == null) {
            throw new IllegalStateException("Cannot " + event + " a request that is " + current);
        }
        return target;
    }

    public static boolean isTerminal(OrderStates state) {
        return !TRANSITIONS.containsKey(state);
    }

    private static void permit(OrderStates from, OrderEvents event, OrderStates to) {
        TRANSITIONS.computeIfAbsent(from, s -> new EnumMap<>(OrderEvents.class)).put(event, to);
    }

}
