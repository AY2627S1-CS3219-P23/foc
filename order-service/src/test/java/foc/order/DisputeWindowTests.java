/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-26.
 * Scope: issue #239: the ARRIVED dispute window comes from the
 * auto-complete setting it is constructed with, not a fixed hour, so the
 * window and the auto-complete sweep always agree (team decision). A
 * plain unit test: RequestsService built by hand with a 2 h delay and
 * mocked collaborators, no Spring context.
 * Author review: Ryan Ang, pending pull request review.
 * 2026-10-29, Claude Code (Opus 5.5), issue #241: autoCompleteAt, the time
 * the window closes, from the same delay (team decision): arrivedAt +
 * delay while ARRIVED, null in any other state or with no arrivedAt;
 * an ARRIVED request with no arrivedAt cannot be disputed (409, team
 * decision). Author review: Ryan Ang, pending pull request review.
 * 2026-10-31, Claude Code (Opus 5.5), N6 chat: the service's new ChatCalls
 * argument passed as a mock. Author review: Ko-Khan, pending pull request
 * review.
 */
package foc.order;

import foc.contracts.events.request.RequestDisputed;
import foc.order.model.Requests;
import foc.order.repository.OutboxEventRepository;
import foc.order.repository.RequestDisputeRepository;
import foc.order.repository.RequestsRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisputeWindowTests {

	// longer than the 1 h default, as if ORDER_AUTO_COMPLETE_AFTER_MS were raised
	private static final Duration AUTO_COMPLETE_DELAY = Duration.ofHours(2);
	private static final Long REQUESTER_ID = 1L;
	private static final Long COURIER_ID = 7L;

	private final RequestsRepository requestsRepository = mock(RequestsRepository.class);
	private final EventOutbox outbox = mock(EventOutbox.class);
	private final RequestsService requestsService = new RequestsService(requestsRepository, outbox,
			mock(OutboxEventRepository.class), mock(RequestDisputeRepository.class), mock(ChatCalls.class),
			AUTO_COMPLETE_DELAY.toMillis());

	@Test
	void anArrivedRequestCanBeDisputedPastAnHourWhileTheLongerWindowIsOpen() {
		Requests request = arrivedMinutesAgo(1L, 90);

		requestsService.dispute(1L, REQUESTER_ID, "WRONG_ITEM", null);

		assertEquals(OrderStates.DISPUTED, request.getStatus());
		verify(outbox).enqueue(isA(RequestDisputed.class), eq(1L));
	}

	@Test
	void anArrivedRequestCannotBeDisputedOnceTheLongerWindowHasClosed() {
		Requests request = arrivedMinutesAgo(2L, 130);

		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> requestsService.dispute(2L, REQUESTER_ID, "WRONG_ITEM", null));

		Instant windowEnd = request.getArrivedAt().plus(AUTO_COMPLETE_DELAY);
		assertEquals("The dispute window closed at " + windowEnd.truncatedTo(ChronoUnit.SECONDS), e.getMessage());
		assertEquals(OrderStates.ARRIVED, request.getStatus());
		verify(outbox, never()).enqueue(any(), anyLong());
	}

	@Test
	void autoCompleteAtIsArrivedAtPlusTheConfiguredDelay() {
		Requests request = arrivedMinutesAgo(3L, 10);

		assertEquals(request.getArrivedAt().plus(AUTO_COMPLETE_DELAY), requestsService.autoCompleteAt(request));
	}

	@Test
	void autoCompleteAtIsNullUnlessArrivedWithAnArrivalTime() {
		Requests collected = arrivedMinutesAgo(4L, 10);
		collected.setStatus(OrderStates.COLLECTED);
		assertNull(requestsService.autoCompleteAt(collected));

		Requests completed = arrivedMinutesAgo(5L, 10);
		completed.setStatus(OrderStates.COMPLETED);
		assertNull(requestsService.autoCompleteAt(completed));

		Requests noArrivalTime = arrivedMinutesAgo(6L, 10);
		noArrivalTime.setArrivedAt(null);
		assertNull(requestsService.autoCompleteAt(noArrivalTime));
	}

	@Test
	void anArrivedRequestWithNoArrivalTimeCannotBeDisputed() {
		Requests request = arrivedMinutesAgo(7L, 10);
		request.setArrivedAt(null);

		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> requestsService.dispute(7L, REQUESTER_ID, "WRONG_ITEM", null));

		assertEquals("No arrival time is recorded for this request", e.getMessage());
		assertEquals(OrderStates.ARRIVED, request.getStatus());
		verify(outbox, never()).enqueue(any(), anyLong());
	}

	// An ARRIVED request the repository returns for this id.
	private Requests arrivedMinutesAgo(Long id, long minutes) {
		Requests request = new Requests();
		request.setId(id);
		request.setRequesterId(REQUESTER_ID);
		request.setCourierId(COURIER_ID);
		request.setSupplierId(2L);
		request.setSupplierName("The Deck");
		request.setDropoffLocation("COM3 Level 2");
		request.setItem("Chicken rice");
		request.setCreditAmount(3);
		request.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));
		request.setStatus(OrderStates.ARRIVED);
		request.setArrivedAt(Instant.now().minus(minutes, ChronoUnit.MINUTES));
		when(requestsRepository.findById(id)).thenReturn(Optional.of(request));
		return request;
	}
}
