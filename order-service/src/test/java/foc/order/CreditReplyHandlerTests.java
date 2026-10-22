/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: integration tests for CreditReplyHandler against in-memory H2:
 * each reply moves a PENDING request on and writes its event, a reply
 * for a request that is no longer PENDING changes nothing, and a reply
 * naming no known request is reported for dead-lettering.
 * 2026-10-16: statuses compared as OrderStates constants.
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-21, Claude Code (Opus 5.5), issue #234: the test rejection carries
 * the new reasonCode component (team decision, DSP-12); author review:
 * Ryan Ang, pending pull request review.
 * 2026-10-22, Claude Code (Opus 5.5), issue #238: runs against
 * Testcontainers Postgres built by Flyway (extends PostgresTestContainer)
 * instead of H2. Same day, PR #243 review follow-up: the rejection
 * helper takes the reasonCode with the reason, so every test rejection
 * is a pair credit-service would publish. Author review: Ryan Ang,
 * pending pull request review.
 */
package foc.order;

import foc.contracts.events.credit.CreditReservationRejected;
import foc.contracts.events.credit.CreditReserved;
import foc.order.model.Requests;
import foc.order.repository.OutboxEventRepository;
import foc.order.repository.RequestsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CreditReplyHandlerTests extends PostgresTestContainer {

	@Autowired
	private CreditReplyHandler handler;

	@Autowired
	private RequestsService requestsService;

	@Autowired
	private RequestsRepository requestsRepository;

	@Autowired
	private OutboxEventRepository outboxEvents;

	@Test
	void reservedMovesPendingRequestToCreated() {
		Long id = newRequest();
		long eventsBefore = outboxEvents.count();

		assertEquals(CreditReplyHandler.Outcome.HANDLED, handler.handle(reserved(id.toString())));

		assertEquals(OrderStates.CREATED, status(id));
		assertEquals(eventsBefore + 1, outboxEvents.count());
	}

	@Test
	void rejectedMovesPendingRequestToRejected() {
		Long id = newRequest();

		assertEquals(CreditReplyHandler.Outcome.HANDLED,
				handler.handle(rejected(id.toString(), "insufficient available credits",
						CreditReservationRejected.INSUFFICIENT_CREDITS)));

		assertEquals(OrderStates.REJECTED, status(id));
	}

	@Test
	void replyForRequestNoLongerPendingIsIgnored() {
		Long id = newRequest();
		requestsService.timeout(id);
		long eventsBefore = outboxEvents.count();

		// a late reservation after the timeout, then a repeated rejection
		assertEquals(CreditReplyHandler.Outcome.HANDLED, handler.handle(reserved(id.toString())));
		assertEquals(CreditReplyHandler.Outcome.HANDLED, handler.handle(rejected(id.toString(),
				"insufficient available credits", CreditReservationRejected.INSUFFICIENT_CREDITS)));

		assertEquals(OrderStates.CANCELLED, status(id));
		assertEquals(eventsBefore, outboxEvents.count());
	}

	@Test
	void replyForUnknownRequestIsReported() {
		assertEquals(CreditReplyHandler.Outcome.UNKNOWN_REQUEST, handler.handle(reserved("999999")));
		assertEquals(CreditReplyHandler.Outcome.UNKNOWN_REQUEST, handler.handle(reserved("req-not-a-number")));
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

	private OrderStates status(Long id) {
		return requestsRepository.findById(id).orElseThrow().getStatus();
	}

	private static CreditReserved reserved(String requestId) {
		return new CreditReserved(UUID.randomUUID().toString(), Instant.now(), "credit-service", requestId,
				List.of(), requestId, "1", 3);
	}

	// reason and reasonCode passed as a pair credit-service would publish
	private static CreditReservationRejected rejected(String requestId, String reason, String reasonCode) {
		return new CreditReservationRejected(UUID.randomUUID().toString(), Instant.now(), "credit-service",
				requestId, List.of(), requestId, "1", 3, reason, reasonCode);
	}
}
