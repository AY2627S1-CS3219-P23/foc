/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: integration tests for PendingTimeoutSweeper against in-memory
 * H2: a request PENDING past the waiting time is cancelled, a fresh
 * PENDING request and a CREATED one are left alone.
 * 2026-10-16: statuses compared as OrderStates constants.
 * Reviewed by: Ko-Khan (via pull request).
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
class PendingTimeoutSweeperTests {

	private static final long TIMEOUT_MS = 30_000;

	@Autowired
	private RequestsService requestsService;

	@Autowired
	private RequestsRepository requestsRepository;

	@Test
	void cancelsOnlyRequestsPendingPastTheWaitingTime() {
		Long overdue = newRequestCreatedSecondsAgo(31);
		Long fresh = newRequestCreatedSecondsAgo(0);
		Long reservedLongAgo = newRequestCreatedSecondsAgo(120);
		requestsService.reserve(reservedLongAgo);

		new PendingTimeoutSweeper(requestsRepository, requestsService, TIMEOUT_MS).cancelOverdue();

		assertEquals(OrderStates.CANCELLED, status(overdue));
		assertEquals(OrderStates.PENDING, status(fresh));
		assertEquals(OrderStates.CREATED, status(reservedLongAgo));
	}

	private Long newRequestCreatedSecondsAgo(long seconds) {
		Requests request = new Requests();
		request.setRequesterId(1L);
		request.setSupplierId(2L);
		request.setSupplierName("The Deck");
		request.setDropoffLocation("COM3 Level 2");
		request.setItem("Chicken rice");
		request.setCreditAmount(3);
		request.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));
		Long id = requestsService.create(request).getId();
		Requests stored = requestsRepository.findById(id).orElseThrow();
		stored.setCreatedAt(Instant.now().minusSeconds(seconds));
		requestsRepository.save(stored);
		return id;
	}

	private OrderStates status(Long id) {
		return requestsRepository.findById(id).orElseThrow().getStatus();
	}
}
