/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: tests for DeadlineExpirySweeper, following
 * PendingTimeoutSweeperTests: only CREATED / ACCEPTED requests past their
 * deadline expire.
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
class DeadlineExpirySweeperTests {

	private static final Long COURIER_ID = 7L;

	@Autowired
	private RequestsService requestsService;

	@Autowired
	private RequestsRepository requestsRepository;

	@Test
	void expiresOnlyCreatedAndAcceptedRequestsPastTheirDeadline() {
		Long createdOverdue = reservedRequest();
		Long acceptedOverdue = reservedRequest();
		requestsService.accept(acceptedOverdue, COURIER_ID);
		Long collectedOverdue = reservedRequest();
		requestsService.accept(collectedOverdue, COURIER_ID);
		requestsService.collect(collectedOverdue, COURIER_ID);
		Long pendingOverdue = newRequest();
		Long createdOnTime = reservedRequest();
		for (Long id : new Long[] {createdOverdue, acceptedOverdue, collectedOverdue, pendingOverdue}) {
			moveDeadlineToThePast(id);
		}

		new DeadlineExpirySweeper(requestsRepository, requestsService).expireOverdue();

		assertEquals(OrderStates.EXPIRED, status(createdOverdue));
		assertEquals(OrderStates.EXPIRED, status(acceptedOverdue));
		// past collection the item is on its way: it no longer expires
		assertEquals(OrderStates.COLLECTED, status(collectedOverdue));
		// PENDING has its own timeout
		assertEquals(OrderStates.PENDING, status(pendingOverdue));
		assertEquals(OrderStates.CREATED, status(createdOnTime));
	}

	private Long newRequest() {
		Requests request = new Requests();
		request.setRequesterId(1L);
		request.setSupplierId(2L);
		request.setSupplierName("The Deck");
		request.setDropoffLocation("COM3 Level 2");
		request.setItem("Chicken rice");
		request.setCreditAmount(3);
		request.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));
		return requestsService.create(request).getId();
	}

	private Long reservedRequest() {
		Long id = newRequest();
		requestsService.reserve(id);
		return id;
	}

	// create refuses a past deadline, so the row is changed afterwards
	private void moveDeadlineToThePast(Long id) {
		Requests stored = requestsRepository.findById(id).orElseThrow();
		stored.setDeadline(Instant.now().minusSeconds(60));
		requestsRepository.save(stored);
	}

	private OrderStates status(Long id) {
		return requestsRepository.findById(id).orElseThrow().getStatus();
	}
}
