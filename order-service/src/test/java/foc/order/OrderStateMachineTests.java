/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-11.
 * Scope: unit tests pinning the OrderStateMachine transition table
 * (every permitted transition, and every other state/event pair
 * rejected).
 * 2026-10-15: the saga's PENDING transitions and REJECTED.
 * 2026-10-16: ARRIVED between COLLECTED and COMPLETED.
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-26, Claude Code (Opus 5.5), issue #239: the dispute
 * transitions (DISPUTE, ESCALATE, RESOLVE), RESOLVED terminal, and no
 * COMPLETE from DISPUTED (DSP-14). Author review: Ryan Ang, pending pull
 * request review.
 */
package foc.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTests {

	private static final Set<String> PERMITTED = Set.of(
			"PENDING,RESERVE", "PENDING,REJECT", "PENDING,TIMEOUT",
			"CREATED,ACCEPT", "CREATED,CANCEL", "CREATED,EXPIRE",
			"ACCEPTED,COLLECT", "ACCEPTED,DROP", "ACCEPTED,EXPIRE",
			"COLLECTED,ARRIVE", "ARRIVED,COMPLETE",
			"COLLECTED,DISPUTE", "COLLECTED,ESCALATE", "ARRIVED,DISPUTE", "DISPUTED,RESOLVE");

	@ParameterizedTest
	@CsvSource({
			"PENDING, RESERVE, CREATED",
			"PENDING, REJECT, REJECTED",
			"PENDING, TIMEOUT, CANCELLED",
			"CREATED, ACCEPT, ACCEPTED",
			"CREATED, CANCEL, CANCELLED",
			"CREATED, EXPIRE, EXPIRED",
			"ACCEPTED, COLLECT, COLLECTED",
			"ACCEPTED, DROP, CREATED",
			"ACCEPTED, EXPIRE, EXPIRED",
			"COLLECTED, ARRIVE, ARRIVED",
			"ARRIVED, COMPLETE, COMPLETED",
			"COLLECTED, DISPUTE, DISPUTED",
			"COLLECTED, ESCALATE, DISPUTED",
			"ARRIVED, DISPUTE, DISPUTED",
			"DISPUTED, RESOLVE, RESOLVED"
	})
	void permittedTransitionLeadsToTarget(OrderStates from, OrderEvents event, OrderStates to) {
		assertTrue(OrderStateMachine.canFire(from, event));
		assertEquals(to, OrderStateMachine.next(from, event));
	}

	@Test
	void everyOtherStateEventPairIsRejected() {
		Set<String> seen = new HashSet<>();
		for (OrderStates state : OrderStates.values()) {
			for (OrderEvents event : OrderEvents.values()) {
				String pair = state + "," + event;
				if (PERMITTED.contains(pair)) {
					seen.add(pair);
					continue;
				}
				assertFalse(OrderStateMachine.canFire(state, event), pair);
				assertThrows(IllegalStateException.class, () -> OrderStateMachine.next(state, event), pair);
			}
		}
		assertEquals(PERMITTED, seen);
	}

	@Test
	void initialStateIsPending() {
		assertEquals(OrderStates.PENDING, OrderStateMachine.INITIAL_STATE);
	}

	@Test
	void onlyCompletedCancelledExpiredRejectedAndResolvedAreTerminal() {
		assertTrue(OrderStateMachine.isTerminal(OrderStates.COMPLETED));
		assertTrue(OrderStateMachine.isTerminal(OrderStates.RESOLVED));
		// it still has to be resolved; retry settlement treats it as active
		assertFalse(OrderStateMachine.isTerminal(OrderStates.DISPUTED));
		assertTrue(OrderStateMachine.isTerminal(OrderStates.CANCELLED));
		assertTrue(OrderStateMachine.isTerminal(OrderStates.EXPIRED));
		assertTrue(OrderStateMachine.isTerminal(OrderStates.REJECTED));
		assertFalse(OrderStateMachine.isTerminal(OrderStates.PENDING));
		assertFalse(OrderStateMachine.isTerminal(OrderStates.CREATED));
		assertFalse(OrderStateMachine.isTerminal(OrderStates.ACCEPTED));
		assertFalse(OrderStateMachine.isTerminal(OrderStates.COLLECTED));
		assertFalse(OrderStateMachine.isTerminal(OrderStates.ARRIVED));
	}

	// DSP-14: the auto-complete sweep fires COMPLETE, which DISPUTED refuses
	@Test
	void aDisputedRequestCannotBeCompleted() {
		assertFalse(OrderStateMachine.canFire(OrderStates.DISPUTED, OrderEvents.COMPLETE));
	}

}
