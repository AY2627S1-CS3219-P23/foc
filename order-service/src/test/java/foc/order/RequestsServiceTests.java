/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-12.
 * Scope: integration tests for RequestsService against in-memory H2 —
 * each event persists the new status and its courier/timestamp columns,
 * and an illegal event leaves the stored row unchanged.
 * 2026-10-15: requests start PENDING; reserve, reject and timeout, and
 * the outbox rows the saga transitions write (event type, the author's
 * field mapping, and none for the other transitions).
 * 2026-10-16: arrive stores arrivedAt and writes request.courier-arrived;
 * complete now needs ARRIVED. A save that never sets createdAt still
 * stores one (PR review fix).
 * 2026-10-16: statuses compared as OrderStates constants; the generated
 * schema rejects a status that is not an OrderStates name.
 * 2026-10-17: complete writes request.completed (courier, both parties);
 * a refused complete writes none. cancel writes request.cancelled and
 * expire request.expired (courier in parties when one had accepted).
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

import foc.order.model.Requests;
import foc.order.repository.OutboxEventRepository;
import foc.order.repository.RequestsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RequestsServiceTests {

	private static final Long COURIER_ID = 7L;

	@Autowired
	private RequestsService requestsService;

	@Autowired
	private RequestsRepository requestsRepository;

	@Autowired
	private OutboxEventRepository outboxEvents;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void createStoresRequestAsPending() {
		Requests stored = stored(newRequest());

		assertEquals(OrderStates.PENDING, stored.getStatus());
		assertNotNull(stored.getCreatedAt());
		assertNull(stored.getCourierId());
	}

	@Test
	void savingWithoutCreatedAtStillStampsIt() {
		Requests request = new Requests();
		request.setRequesterId(1L);
		request.setSupplierId(2L);
		request.setDropoffLocation("COM3 Level 2");
		request.setItem("Chicken rice");
		request.setCreditAmount(3);
		request.setStatus(OrderStates.PENDING);
		request.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));

		Long id = requestsRepository.saveAndFlush(request).getId();

		assertNotNull(stored(id).getCreatedAt());
	}

	@Test
	void databaseRejectsAStatusThatIsNotAnOrderState() {
		Long id = newRequest();

		assertThrows(DataIntegrityViolationException.class,
				() -> jdbcTemplate.update("update requests set status = 'BOGUS' where id = ?", id));

		assertEquals(OrderStates.PENDING, stored(id).getStatus());
	}

	// The check in Requests.status is written by hand; this fails if a
	// state is added to OrderStates but not to the check.
	@Test
	void databaseAcceptsEveryOrderState() {
		Long id = newRequest();

		for (OrderStates state : OrderStates.values()) {
			jdbcTemplate.update("update requests set status = ? where id = ?", state.name(), id);
			assertEquals(state, stored(id).getStatus());
		}
	}

	@Test
	void createWritesRequestSubmittedWithTheMappedFields() {
		Long id = newRequest();

		List<JsonNode> events = eventsFor(id);
		assertEquals(1, events.size());
		JsonNode e = events.get(0);
		assertEquals("request.submitted", e.get("eventType").stringValue());
		assertEquals("order-service", e.get("producer").stringValue());
		assertEquals(id.toString(), e.get("correlationId").stringValue());
		assertEquals("1", e.get("parties").get(0).stringValue());
		assertEquals("1", e.get("requesterId").stringValue());
		assertEquals("The Deck", e.get("pickupLocation").stringValue());
		assertEquals("COM3 Level 2", e.get("dropoffLocation").stringValue());
		assertEquals("Chicken rice", e.get("note").stringValue());
		assertEquals(3, e.get("reward").intValue());
		assertNotNull(e.get("eventId").stringValue());
		assertNotNull(e.get("occurredAt").stringValue());
	}

	@Test
	void reserveMovesPendingToCreatedAndWritesRequestCreated() {
		Long id = newRequest();

		requestsService.reserve(id);

		assertEquals(OrderStates.CREATED, stored(id).getStatus());
		assertEquals(List.of("request.submitted", "request.created"), eventTypes(id));
	}

	@Test
	void rejectMovesPendingToRejectedAndWritesTheReason() {
		Long id = newRequest();

		requestsService.reject(id, "insufficient available credits");

		assertEquals(OrderStates.REJECTED, stored(id).getStatus());
		JsonNode rejected = eventsFor(id).get(1);
		assertEquals("request.rejected", rejected.get("eventType").stringValue());
		assertEquals("insufficient available credits", rejected.get("reason").stringValue());
	}

	@Test
	void timeoutMovesPendingToCancelledAndWritesRequestCancelledWithNoCourier() {
		Long id = newRequest();

		requestsService.timeout(id);

		assertEquals(OrderStates.CANCELLED, stored(id).getStatus());
		JsonNode cancelled = eventsFor(id).get(1);
		assertEquals("request.cancelled", cancelled.get("eventType").stringValue());
		assertTrue(cancelled.get("courierId").isNull());
	}

	@Test
	void pendingRequestCannotBeAcceptedCancelledOrExpired() {
		Long id = newRequest();

		assertThrows(IllegalStateException.class, () -> requestsService.accept(id, COURIER_ID));
		assertThrows(IllegalStateException.class, () -> requestsService.cancel(id));
		assertThrows(IllegalStateException.class, () -> requestsService.expire(id));

		assertEquals(OrderStates.PENDING, stored(id).getStatus());
		assertEquals(List.of("request.submitted"), eventTypes(id));
	}

	@Test
	void onlyTheSagaArriveAndCompleteWriteEvents() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.drop(id);
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id);
		requestsService.arrive(id);
		requestsService.complete(id);

		assertEquals(List.of("request.submitted", "request.created", "request.courier-arrived",
				"request.completed"), eventTypes(id));
	}

	@Test
	void completeWritesRequestCompletedForTheCreditService() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id);
		requestsService.arrive(id);

		requestsService.complete(id);

		JsonNode completed = eventsFor(id).get(3);
		assertEquals("request.completed", completed.get("eventType").stringValue());
		assertEquals(id.toString(), completed.get("requestId").stringValue());
		assertEquals(id.toString(), completed.get("correlationId").stringValue());
		// the Credit Service pays this courier
		assertEquals(COURIER_ID.toString(), completed.get("courierId").stringValue());
		assertEquals("1", completed.get("requesterId").stringValue());
		assertEquals(2, completed.get("parties").size());
		assertEquals("1", completed.get("parties").get(0).stringValue());
		assertEquals(COURIER_ID.toString(), completed.get("parties").get(1).stringValue());
		assertEquals("The Deck", completed.get("pickupLocation").stringValue());
		assertEquals("COM3 Level 2", completed.get("dropoffLocation").stringValue());
		assertEquals("Chicken rice", completed.get("note").stringValue());
	}

	@Test
	void failedCompleteWritesNoEvent() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id);

		assertThrows(IllegalStateException.class, () -> requestsService.complete(id));

		assertFalse(eventTypes(id).contains("request.completed"));
	}

	@Test
	void acceptStoresCourierAndAcceptedAt() {
		Long id = newCreatedRequest();

		requestsService.accept(id, COURIER_ID);

		Requests stored = stored(id);
		assertEquals(OrderStates.ACCEPTED, stored.getStatus());
		assertEquals(COURIER_ID, stored.getCourierId());
		assertNotNull(stored.getAcceptedAt());
	}

	@Test
	void collectArriveCompleteStoresTimestampsAndCompleted() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);

		requestsService.collect(id);
		assertEquals(OrderStates.COLLECTED, stored(id).getStatus());
		assertNotNull(stored(id).getCollectedAt());

		requestsService.arrive(id);
		assertEquals(OrderStates.ARRIVED, stored(id).getStatus());
		assertNotNull(stored(id).getArrivedAt());

		requestsService.complete(id);
		assertEquals(OrderStates.COMPLETED, stored(id).getStatus());
	}

	@Test
	void arriveWritesCourierArrivedForTheRequester() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id);

		requestsService.arrive(id);

		JsonNode arrived = eventsFor(id).get(2);
		assertEquals("request.courier-arrived", arrived.get("eventType").stringValue());
		assertEquals(1, arrived.get("parties").size());
		assertEquals("1", arrived.get("parties").get(0).stringValue());
		assertEquals("1", arrived.get("requesterId").stringValue());
		assertEquals(COURIER_ID.toString(), arrived.get("courierId").stringValue());
		assertEquals("The Deck", arrived.get("pickupLocation").stringValue());
		assertEquals("COM3 Level 2", arrived.get("dropoffLocation").stringValue());
		assertEquals("Chicken rice", arrived.get("note").stringValue());
	}

	@Test
	void collectedRequestCannotBeCompletedBeforeArriving() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id);

		assertThrows(IllegalStateException.class, () -> requestsService.complete(id));

		assertEquals(OrderStates.COLLECTED, stored(id).getStatus());
	}

	@Test
	void dropReopensRequestAndClearsCourier() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);

		requestsService.drop(id);

		Requests stored = stored(id);
		assertEquals(OrderStates.CREATED, stored.getStatus());
		assertNull(stored.getCourierId());
		assertNull(stored.getAcceptedAt());
	}

	@Test
	void cancelStoresCancelled() {
		Long id = newCreatedRequest();

		requestsService.cancel(id);

		assertEquals(OrderStates.CANCELLED, stored(id).getStatus());
	}

	@Test
	void cancelWritesRequestCancelledForTheRequester() {
		Long id = newCreatedRequest();

		requestsService.cancel(id);

		JsonNode cancelled = eventsFor(id).get(2);
		assertEquals("request.cancelled", cancelled.get("eventType").stringValue());
		assertEquals(id.toString(), cancelled.get("requestId").stringValue());
		// the Credit Service releases the reward to this requester
		assertEquals("1", cancelled.get("requesterId").stringValue());
		assertTrue(cancelled.get("courierId").isNull());
		assertEquals(List.of("1"), parties(cancelled));
	}

	@Test
	void expireStoresExpiredFromCreatedAndFromAccepted() {
		Long created = newCreatedRequest();
		Long accepted = newCreatedRequest();
		requestsService.accept(accepted, COURIER_ID);

		requestsService.expire(created);
		requestsService.expire(accepted);

		assertEquals(OrderStates.EXPIRED, stored(created).getStatus());
		assertEquals(OrderStates.EXPIRED, stored(accepted).getStatus());
	}

	@Test
	void expireFromCreatedWritesRequestExpiredForTheRequester() {
		Long id = newCreatedRequest();

		requestsService.expire(id);

		JsonNode expired = eventsFor(id).get(2);
		assertEquals("request.expired", expired.get("eventType").stringValue());
		assertEquals(id.toString(), expired.get("requestId").stringValue());
		assertEquals("1", expired.get("requesterId").stringValue());
		assertEquals(List.of("1"), parties(expired));
	}

	@Test
	void expireFromAcceptedAlsoTellsTheCourier() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);

		requestsService.expire(id);

		JsonNode expired = eventsFor(id).get(2);
		assertEquals("request.expired", expired.get("eventType").stringValue());
		assertEquals("1", expired.get("requesterId").stringValue());
		assertEquals(List.of("1", COURIER_ID.toString()), parties(expired));
	}

	@Test
	void illegalEventThrowsAndLeavesRowUnchanged() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);

		assertThrows(IllegalStateException.class, () -> requestsService.cancel(id));
		assertThrows(IllegalStateException.class, () -> requestsService.accept(id, 8L));
		assertThrows(IllegalStateException.class, () -> requestsService.reserve(id));

		Requests stored = stored(id);
		assertEquals(OrderStates.ACCEPTED, stored.getStatus());
		assertEquals(COURIER_ID, stored.getCourierId());
		// the refused cancel wrote no request.cancelled
		assertEquals(List.of("request.submitted", "request.created"), eventTypes(id));
	}

	@Test
	void unknownRequestThrows() {
		assertThrows(NoSuchElementException.class, () -> requestsService.cancel(-1L));
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

	// A request whose reward the Credit Service has reserved.
	private Long newCreatedRequest() {
		Long id = newRequest();
		requestsService.reserve(id);
		return id;
	}

	private Requests stored(Long id) {
		return requestsRepository.findById(id).orElseThrow();
	}

	// The outbox events for one request, oldest first.
	private List<JsonNode> eventsFor(Long id) {
		return outboxEvents.findAll().stream()
				.sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
				.map(e -> jsonMapper.readTree(e.getPayload()))
				.filter(e -> id.toString().equals(e.get("requestId").stringValue()))
				.toList();
	}

	private static List<String> parties(JsonNode event) {
		List<String> parties = new ArrayList<>();
		event.get("parties").forEach(p -> parties.add(p.stringValue()));
		return parties;
	}

	private List<String> eventTypes(Long id) {
		return eventsFor(id).stream().map(e -> e.get("eventType").stringValue()).toList();
	}

}
