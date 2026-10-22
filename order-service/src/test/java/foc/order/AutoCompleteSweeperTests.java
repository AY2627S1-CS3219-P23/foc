/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: tests for AutoCompleteSweeper, following
 * PendingTimeoutSweeperTests: only requests ARRIVED for longer than the
 * delay are completed.
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-22, Claude Code (Opus 5.5), issue #238: runs against
 * Testcontainers Postgres built by Flyway (extends PostgresTestContainer)
 * instead of H2. Author review: Ryan Ang, pending pull request review.
 */
package foc.order;

import foc.order.model.Requests;
import foc.order.repository.RequestsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AutoCompleteSweeperTests extends PostgresTestContainer {

	private static final long DELAY_MS = 3_600_000;
	private static final Long COURIER_ID = 7L;

	@Autowired
	private RequestsService requestsService;

	@Autowired
	private RequestsRepository requestsRepository;

	@Test
	void completesOnlyRequestsArrivedLongerThanTheDelay() {
		Long overdue = arrivedSecondsAgo(3_601);
		Long recent = arrivedSecondsAgo(60);
		Long collected = collectedRequest();

		new AutoCompleteSweeper(requestsRepository, requestsService, DELAY_MS).completeOverdue();

		assertEquals(OrderStates.COMPLETED, status(overdue));
		assertEquals(OrderStates.ARRIVED, status(recent));
		assertEquals(OrderStates.COLLECTED, status(collected));
	}

	private Long collectedRequest() {
		Requests request = new Requests();
		request.setRequesterId(1L);
		request.setSupplierId(2L);
		request.setSupplierName("The Deck");
		request.setDropoffLocation("COM3 Level 2");
		request.setItem("Chicken rice");
		request.setCreditAmount(3);
		request.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));
		Long id = requestsService.create(request).getId();
		requestsService.reserve(id);
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id, COURIER_ID);
		return id;
	}

	private Long arrivedSecondsAgo(long seconds) {
		Long id = collectedRequest();
		requestsService.arrive(id, COURIER_ID);
		Requests stored = requestsRepository.findById(id).orElseThrow();
		stored.setArrivedAt(Instant.now().minusSeconds(seconds));
		requestsRepository.save(stored);
		return id;
	}

	private OrderStates status(Long id) {
		return requestsRepository.findById(id).orElseThrow().getStatus();
	}
}
