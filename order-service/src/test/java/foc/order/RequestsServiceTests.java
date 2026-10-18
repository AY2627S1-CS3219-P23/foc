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
 * 2026-10-18: a requester accepting their own request is refused.
 * Same day: ownership checks (requester-only, courier-only steps; a
 * stranger is told the request does not exist).
 * Same day: create's field checks (all missing fields listed, reward
 * at least 1, deadline in the future); supplierName then made required.
 * Same day: request.accepted / request.collected (requester and courier
 * in parties), completeAutomatically; events looked up by type.
 * Same day: accept after the deadline is refused.
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

	private static final Long REQUESTER_ID = 1L;
	private static final Long COURIER_ID = 7L;
	private static final Long STRANGER_ID = 9L;

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
	void createWithMissingFieldsListsThemAllAndStoresNothing() {
		long before = requestsRepository.count();
		long eventsBefore = outboxEvents.count();
		Requests request = new Requests();
		request.setSupplierId(2L);
		request.setItem("   ");

		InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> requestsService.create(request));

		assertEquals("Missing required fields: requesterId, supplierName, dropoffLocation, item, creditAmount, deadline",
				e.getMessage());
		assertEquals(before, requestsRepository.count());
		assertEquals(eventsBefore, outboxEvents.count());
	}

	@Test
	void createRejectsARewardBelowOneAndADeadlineNotInTheFuture() {
		Requests request = validRequest();
		request.setCreditAmount(0);
		request.setDeadline(Instant.now().minusSeconds(60));

		InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> requestsService.create(request));

		assertEquals("creditAmount must be at least 1; deadline must be in the future", e.getMessage());
	}

	@Test
	void createReportsMissingAndInvalidFieldsTogether() {
		Requests request = validRequest();
		request.setItem(null);
		request.setCreditAmount(-2);

		InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> requestsService.create(request));

		assertEquals("Missing required fields: item; creditAmount must be at least 1", e.getMessage());
	}

	@Test
	void supplierNameIsRequired() {
		Requests request = validRequest();
		request.setSupplierName(" ");

		InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> requestsService.create(request));

		assertEquals("Missing required fields: supplierName", e.getMessage());
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
		assertThrows(IllegalStateException.class, () -> requestsService.cancel(id, REQUESTER_ID));
		assertThrows(IllegalStateException.class, () -> requestsService.expire(id));

		assertEquals(OrderStates.PENDING, stored(id).getStatus());
		assertEquals(List.of("request.submitted"), eventTypes(id));
	}

	@Test
	void everyStepButDropWritesItsEvent() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.drop(id, COURIER_ID);
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id, COURIER_ID);
		requestsService.arrive(id, COURIER_ID);
		requestsService.complete(id, REQUESTER_ID);

		// the drop between the two accepts writes nothing (Order F5.2)
		assertEquals(List.of("request.submitted", "request.created", "request.accepted", "request.accepted",
				"request.collected", "request.courier-arrived", "request.completed"), eventTypes(id));
	}

	@Test
	void completeWritesRequestCompletedForTheCreditService() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id, COURIER_ID);
		requestsService.arrive(id, COURIER_ID);

		requestsService.complete(id, REQUESTER_ID);

		JsonNode completed = event(id, "request.completed");
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
		requestsService.collect(id, COURIER_ID);

		assertThrows(IllegalStateException.class, () -> requestsService.complete(id, REQUESTER_ID));

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
	void requesterCannotAcceptTheirOwnRequest() {
		Long id = newCreatedRequest();

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> requestsService.accept(id, 1L));

		assertEquals("A requester cannot accept their own request", e.getMessage());
		Requests stored = stored(id);
		assertEquals(OrderStates.CREATED, stored.getStatus());
		assertNull(stored.getCourierId());
		assertNull(stored.getAcceptedAt());
	}

	@Test
	void acceptAndCollectWriteTheirEventsForRequesterAndCourier() {
		Long id = newCreatedRequest();

		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id, COURIER_ID);

		for (String type : List.of("request.accepted", "request.collected")) {
			JsonNode e = event(id, type);
			assertEquals(id.toString(), e.get("requestId").stringValue(), type);
			assertEquals("1", e.get("requesterId").stringValue(), type);
			assertEquals(COURIER_ID.toString(), e.get("courierId").stringValue(), type);
			assertEquals(List.of("1", COURIER_ID.toString()), parties(e), type);
			assertEquals("The Deck", e.get("pickupLocation").stringValue(), type);
			assertEquals("Chicken rice", e.get("note").stringValue(), type);
		}
	}

	@Test
	void completeAutomaticallyCompletesWithoutTheRequesterAndPaysTheCourier() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id, COURIER_ID);
		requestsService.arrive(id, COURIER_ID);

		requestsService.completeAutomatically(id);

		assertEquals(OrderStates.COMPLETED, stored(id).getStatus());
		assertEquals(COURIER_ID.toString(), event(id, "request.completed").get("courierId").stringValue());
	}

	@Test
	void acceptAfterTheDeadlineIsRefusedAndChangesNothing() {
		Long id = newCreatedRequest();
		Requests stored = stored(id);
		stored.setDeadline(Instant.now().minusSeconds(60));
		requestsRepository.save(stored);

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> requestsService.accept(id, COURIER_ID));

		assertEquals("The request's deadline has passed", e.getMessage());
		assertEquals(OrderStates.CREATED, stored(id).getStatus());
		assertNull(stored(id).getCourierId());
		assertFalse(eventTypes(id).contains("request.accepted"));
	}

	@Test
	void collectArriveCompleteStoresTimestampsAndCompleted() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);

		requestsService.collect(id, COURIER_ID);
		assertEquals(OrderStates.COLLECTED, stored(id).getStatus());
		assertNotNull(stored(id).getCollectedAt());

		requestsService.arrive(id, COURIER_ID);
		assertEquals(OrderStates.ARRIVED, stored(id).getStatus());
		assertNotNull(stored(id).getArrivedAt());

		requestsService.complete(id, REQUESTER_ID);
		assertEquals(OrderStates.COMPLETED, stored(id).getStatus());
	}

	@Test
	void arriveWritesCourierArrivedForTheRequester() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id, COURIER_ID);

		requestsService.arrive(id, COURIER_ID);

		JsonNode arrived = event(id, "request.courier-arrived");
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
		requestsService.collect(id, COURIER_ID);

		assertThrows(IllegalStateException.class, () -> requestsService.complete(id, REQUESTER_ID));

		assertEquals(OrderStates.COLLECTED, stored(id).getStatus());
	}

	@Test
	void dropReopensRequestAndClearsCourier() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);

		requestsService.drop(id, COURIER_ID);

		Requests stored = stored(id);
		assertEquals(OrderStates.CREATED, stored.getStatus());
		assertNull(stored.getCourierId());
		assertNull(stored.getAcceptedAt());
	}

	@Test
	void cancelStoresCancelled() {
		Long id = newCreatedRequest();

		requestsService.cancel(id, REQUESTER_ID);

		assertEquals(OrderStates.CANCELLED, stored(id).getStatus());
	}

	@Test
	void cancelWritesRequestCancelledForTheRequester() {
		Long id = newCreatedRequest();

		requestsService.cancel(id, REQUESTER_ID);

		JsonNode cancelled = event(id, "request.cancelled");
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

		JsonNode expired = event(id, "request.expired");
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

		JsonNode expired = event(id, "request.expired");
		assertEquals("request.expired", expired.get("eventType").stringValue());
		assertEquals("1", expired.get("requesterId").stringValue());
		assertEquals(List.of("1", COURIER_ID.toString()), parties(expired));
	}

	@Test
	void illegalEventThrowsAndLeavesRowUnchanged() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);

		assertThrows(IllegalStateException.class, () -> requestsService.cancel(id, REQUESTER_ID));
		assertThrows(IllegalStateException.class, () -> requestsService.accept(id, 8L));
		assertThrows(IllegalStateException.class, () -> requestsService.reserve(id));

		Requests stored = stored(id);
		assertEquals(OrderStates.ACCEPTED, stored.getStatus());
		assertEquals(COURIER_ID, stored.getCourierId());
		// the refused events wrote nothing after the accept
		assertEquals(List.of("request.submitted", "request.created", "request.accepted"), eventTypes(id));
	}

	@Test
	void onlyTheRequesterCanCancelOrComplete() {
		Long cancellable = newCreatedRequest();
		assertThrows(NoSuchElementException.class, () -> requestsService.cancel(cancellable, COURIER_ID));
		assertEquals(OrderStates.CREATED, stored(cancellable).getStatus());

		Long arrived = newCreatedRequest();
		requestsService.accept(arrived, COURIER_ID);
		requestsService.collect(arrived, COURIER_ID);
		requestsService.arrive(arrived, COURIER_ID);
		assertThrows(NoSuchElementException.class, () -> requestsService.complete(arrived, COURIER_ID));
		assertThrows(NoSuchElementException.class, () -> requestsService.complete(arrived, STRANGER_ID));
		assertEquals(OrderStates.ARRIVED, stored(arrived).getStatus());
	}

	@Test
	void onlyTheAssignedCourierCanCollectArriveOrDrop() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);

		for (Long notTheCourier : List.of(REQUESTER_ID, STRANGER_ID)) {
			assertThrows(NoSuchElementException.class, () -> requestsService.collect(id, notTheCourier));
			assertThrows(NoSuchElementException.class, () -> requestsService.drop(id, notTheCourier));
		}
		requestsService.collect(id, COURIER_ID);
		assertThrows(NoSuchElementException.class, () -> requestsService.arrive(id, REQUESTER_ID));
		assertThrows(NoSuchElementException.class, () -> requestsService.arrive(id, STRANGER_ID));

		Requests stored = stored(id);
		assertEquals(OrderStates.COLLECTED, stored.getStatus());
		assertEquals(COURIER_ID, stored.getCourierId());
	}

	@Test
	void nobodyIsTheCourierBeforeAcceptance() {
		Long id = newCreatedRequest();

		assertThrows(NoSuchElementException.class, () -> requestsService.collect(id, COURIER_ID));
		assertThrows(NoSuchElementException.class, () -> requestsService.drop(id, REQUESTER_ID));
	}

	// A stranger gets 404 whatever the state, so the answer reveals neither
	// that the request exists nor where it is in its life.
	@Test
	void ownershipIsCheckedBeforeTheState() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);

		assertThrows(NoSuchElementException.class, () -> requestsService.cancel(id, STRANGER_ID));
		assertThrows(NoSuchElementException.class, () -> requestsService.complete(id, STRANGER_ID));
		// the requester, for whom the state is what refuses it
		assertThrows(IllegalStateException.class, () -> requestsService.cancel(id, REQUESTER_ID));
	}

	@Test
	void unknownRequestThrows() {
		assertThrows(NoSuchElementException.class, () -> requestsService.cancel(-1L, REQUESTER_ID));
	}

	private Long newRequest() {
		return requestsService.create(validRequest()).getId();
	}

	// A new request with every field set and valid, not yet stored.
	private static Requests validRequest() {
		Requests request = new Requests();
		request.setRequesterId(1L);
		request.setSupplierId(2L);
		request.setSupplierName("The Deck");
		request.setDropoffLocation("COM3 Level 2");
		request.setItem("Chicken rice");
		request.setCreditAmount(3);
		request.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));
		return request;
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

	// The one event of this type for the request.
	private JsonNode event(Long id, String type) {
		List<JsonNode> matching = eventsFor(id).stream()
				.filter(e -> type.equals(e.get("eventType").stringValue())).toList();
		assertEquals(1, matching.size(), type);
		return matching.get(0);
	}

	private List<String> eventTypes(Long id) {
		return eventsFor(id).stream().map(e -> e.get("eventType").stringValue()).toList();
	}

}
