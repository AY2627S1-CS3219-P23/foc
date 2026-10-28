/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: tests for DeadlineExpirySweeper, following
 * PendingTimeoutSweeperTests: only CREATED / ACCEPTED requests past their
 * deadline expire.
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-22, Claude Code (Opus 5.5), issue #238: runs against
 * Testcontainers Postgres built by Flyway (extends PostgresTestContainer)
 * instead of H2. Author review: Ryan Ang, pending pull request review.
 * 2026-10-26, Claude Code (Opus 5.5), issue #239: the sweep's dispute
 * escalation (only COLLECTED past the deadline plus the grace period;
 * the stored dispute and its event) and one sweep doing both jobs.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.order;

import foc.order.model.RequestDispute;
import foc.order.model.Requests;
import foc.order.repository.OutboxEventRepository;
import foc.order.repository.RequestDisputeRepository;
import foc.order.repository.RequestsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DeadlineExpirySweeperTests extends PostgresTestContainer {

	private static final Long COURIER_ID = 7L;
	private static final long GRACE_MS = 3_600_000;

	@Autowired
	private RequestsService requestsService;

	@Autowired
	private RequestsRepository requestsRepository;

	@Autowired
	private RequestDisputeRepository disputes;

	@Autowired
	private OutboxEventRepository outboxEvents;

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

		sweeper().expireOverdue();

		assertEquals(OrderStates.EXPIRED, status(createdOverdue));
		assertEquals(OrderStates.EXPIRED, status(acceptedOverdue));
		// past collection the item is on its way: it no longer expires
		assertEquals(OrderStates.COLLECTED, status(collectedOverdue));
		// PENDING has its own timeout
		assertEquals(OrderStates.PENDING, status(pendingOverdue));
		assertEquals(OrderStates.CREATED, status(createdOnTime));
	}

	@Test
	void escalatesOnlyRequestsStillCollectedTheGracePeriodAfterTheirDeadline() {
		Long overdue = collectedRequest();
		setDeadline(overdue, Instant.now().minusMillis(GRACE_MS).minusSeconds(60));
		Long withinGrace = collectedRequest();
		setDeadline(withinGrace, Instant.now().minusSeconds(60));
		Long arrived = collectedRequest();
		requestsService.arrive(arrived, COURIER_ID);
		setDeadline(arrived, Instant.now().minusMillis(GRACE_MS).minusSeconds(60));

		sweeper().escalateOverdue();

		assertEquals(OrderStates.DISPUTED, status(overdue));
		RequestDispute dispute = disputes.findById(overdue).orElseThrow();
		assertEquals(DisputeCategory.NOT_DELIVERED, dispute.getCategory());
		assertEquals("auto-escalated: not delivered by deadline", dispute.getDisputeNote());
		assertTrue(dispute.isAutoEscalated());
		assertNull(dispute.getRaisedBy());
		assertTrue(outboxEvents.findAll().stream()
				.anyMatch(e -> overdue.equals(e.getRequestId()) && e.getEventType().equals("request.disputed")));
		// the requester may still dispute it themselves during the grace period
		assertEquals(OrderStates.COLLECTED, status(withinGrace));
		// arrived: the requester confirms or disputes it
		assertEquals(OrderStates.ARRIVED, status(arrived));
	}

	@Test
	void oneSweepExpiresAndEscalates() {
		Long created = reservedRequest();
		moveDeadlineToThePast(created);
		Long collected = collectedRequest();
		setDeadline(collected, Instant.now().minusMillis(GRACE_MS).minusSeconds(60));

		sweeper().sweep();

		assertEquals(OrderStates.EXPIRED, status(created));
		assertEquals(OrderStates.DISPUTED, status(collected));
	}

	private DeadlineExpirySweeper sweeper() {
		return new DeadlineExpirySweeper(requestsRepository, requestsService, GRACE_MS);
	}

	private Long collectedRequest() {
		Long id = reservedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id, COURIER_ID);
		return id;
	}

	private void setDeadline(Long id, Instant deadline) {
		Requests stored = requestsRepository.findById(id).orElseThrow();
		stored.setDeadline(deadline);
		requestsRepository.save(stored);
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
