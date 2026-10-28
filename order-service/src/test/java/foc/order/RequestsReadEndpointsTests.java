/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: tests for the author's read endpoints over HTTP with signed
 * tokens (RequestsControllerTests' pattern): open errands are CREATED
 * only and trimmed, the detail is for the parties and admins, /mine by
 * role and state, the admin view limited to ADMIN / OWNER, paging and
 * parameter errors. Each test uses its own user ids, since the H2
 * database is shared by every test in the run.
 * Same day: the filters (supplier, reward range, deadline and created
 * ranges), the sort parameter, and their 400s. Then: the open list hides
 * requests past their deadline, and accepting one is a 409.
 * 2026-10-20: state takes several states, comma-separated.
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-22, Claude Code (Opus 5.5), issue #238: runs against
 * Testcontainers Postgres built by Flyway (extends PostgresTestContainer)
 * instead of H2. Author review: Ryan Ang, pending pull request review.
 * 2026-10-23, Claude Code (Opus 5.5), issue #240: the admin list (state,
 * closeReason, the shared filters; 403/400), closeReason in lists and the
 * detail, the batch status lookup (null for an unknown id, {} for none,
 * its 400s) and retry settlement over HTTP. Author review: Ryan Ang,
 * pending pull request review.
 * 2026-10-26, Claude Code (Opus 5.5), issue #239: the dispute object on
 * the lists and the detail (null if never disputed, resolution fields
 * once resolved) and the admin dispute queue (state=DISPUTED). Author
 * review: Ryan Ang, pending pull request review.
 */
package foc.order;

import foc.order.repository.RequestsRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestsReadEndpointsTests extends PostgresTestContainer {

	// order.jwt.secret in the test application.yaml
	private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";

	// fresh user ids for every test, far from the other test classes' 1-9
	private static final AtomicLong NEXT_USER = new AtomicLong(100_000);

	private long requester;
	private long courier;
	private long stranger;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RequestsRepository requestsRepository;

	@Autowired
	private RequestsService requestsService;

	@Autowired
	private JsonMapper jsonMapper;

	@BeforeEach
	void freshUsers() {
		requester = NEXT_USER.incrementAndGet();
		courier = NEXT_USER.incrementAndGet();
		stranger = NEXT_USER.incrementAndGet();
	}

	@Test
	void openRequestsListOnlyCreatedOnesNewestFirstAndTrimmed() throws Exception {
		Long older = createdRequest();
		Long pending = create();
		Long accepted = createdRequest();
		requestsService.accept(accepted, courier);
		Long newer = createdRequest();

		JsonNode page = getJson("/requests?size=100", stranger);
		List<Long> ids = ids(page);

		assertTrue(ids.indexOf(newer) >= 0 && ids.indexOf(newer) < ids.indexOf(older), "newest first: " + ids);
		assertFalse(ids.contains(pending), "PENDING is hidden");
		assertFalse(ids.contains(accepted), "ACCEPTED is not open");
		JsonNode item = item(page, newer);
		assertEquals("CREATED", item.get("status").stringValue());
		assertEquals("The Deck", item.get("supplierName").stringValue());
		assertEquals(3, item.get("creditAmount").intValue());
		assertNull(item.get("requesterId"), "lists are trimmed");
		assertNull(item.get("courierId"), "lists are trimmed");
	}

	@Test
	void openRequestsHideThosePastTheirDeadline() throws Exception {
		Long current = createdRequest();
		Long overdue = createdRequest();
		var stored = requestsRepository.findById(overdue).orElseThrow();
		stored.setDeadline(Instant.now().minusSeconds(60));
		requestsRepository.save(stored);

		List<Long> ids = ids(getJson("/requests?size=100", stranger));

		assertTrue(ids.contains(current));
		assertFalse(ids.contains(overdue), "past its deadline, though still CREATED until the sweep runs");
		// the requester still sees it in their own list
		assertTrue(ids(getJson("/requests/mine?role=requester", requester)).contains(overdue));
		// and accepting it is refused
		send(post("/accept").contentType(MediaType.APPLICATION_JSON).content("{\"requestId\": " + overdue + "}"), courier)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("The request's deadline has passed"));
	}

	@Test
	void openRequestsRefuseAnyStateButCreated() throws Exception {
		send(get("/requests?state=ACCEPTED"), stranger)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("state must be CREATED"));
		send(get("/requests?state=nonsense"), stranger).andExpect(status().isBadRequest());
		send(get("/requests?state=created"), stranger).andExpect(status().isOk());
	}

	@Test
	void detailIsForTheRequesterTheCourierAndAdmins() throws Exception {
		Long id = createdRequest();
		requestsService.accept(id, courier);

		JsonNode detail = getJson("/requests/" + id, requester);
		assertEquals(requester, detail.get("requesterId").longValue());
		assertEquals(courier, detail.get("courierId").longValue());
		assertEquals("ACCEPTED", detail.get("status").stringValue());
		assertNotNull(detail.get("acceptedAt").stringValue());
		assertNull(detail.get("version"), "the lock version is not exposed");

		send(get("/requests/" + id), courier).andExpect(status().isOk());
		send(get("/requests/" + id), stranger, "ADMIN").andExpect(status().isOk());
		send(get("/requests/" + id), stranger)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Request not found"));
		send(get("/requests/999999999"), requester).andExpect(status().isNotFound());
	}

	@Test
	void mineListsTheCallersRequestsByRoleAndState() throws Exception {
		Long created = createdRequest();
		Long accepted = createdRequest();
		requestsService.accept(accepted, courier);
		Long pending = create();

		assertEquals(List.of(pending, accepted, created), ids(getJson("/requests/mine?role=requester", requester)));
		assertEquals(List.of(pending), ids(getJson("/requests/mine?role=requester&state=PENDING", requester)));
		assertEquals(List.of(accepted), ids(getJson("/requests/mine?role=courier", courier)));
		assertEquals(List.of(), ids(getJson("/requests/mine?role=courier", requester)));
	}

	@Test
	void stateTakesSeveralStatesCommaSeparated() throws Exception {
		Long created = createdRequest();
		Long accepted = createdRequest();
		requestsService.accept(accepted, courier);
		Long cancelled = createdRequest();
		requestsService.cancel(cancelled, requester);
		Long pending = create();

		// all active, then all finished: one paged query each
		assertEquals(List.of(pending, accepted, created), ids(getJson(
				"/requests/mine?role=requester&state=PENDING,CREATED,ACCEPTED,COLLECTED,ARRIVED", requester)));
		assertEquals(List.of(cancelled), ids(getJson(
				"/requests/mine?role=requester&state=COMPLETED,CANCELLED,EXPIRED,REJECTED", requester)));
		// spaces and case are forgiven, like a single state
		assertEquals(List.of(accepted, created),
				ids(getJson("/requests/mine?role=requester&state=created,%20ACCEPTED", requester)));
		JsonNode page = getJson("/requests/mine?role=requester&state=CREATED,CANCELLED&size=1", requester);
		assertEquals(2, page.get("totalElements").longValue(), "paging counts across the states");
		assertEquals(List.of(accepted),
				ids(getJson("/users/" + courier + "/requests?state=ACCEPTED,COLLECTED", stranger, "ADMIN")));
	}

	@Test
	void openRequestsTakeOnlyCreatedEvenInAList() throws Exception {
		send(get("/requests?state=CREATED,ACCEPTED"), stranger)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("state must be CREATED"));
		send(get("/requests?state=CREATED,created"), stranger).andExpect(status().isOk());
	}

	@Test
	void mineNeedsAValidRoleAndState() throws Exception {
		send(get("/requests/mine"), requester)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("role is required: requester or courier"));
		send(get("/requests/mine?role=admin"), requester)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("role must be requester or courier"));
		send(get("/requests/mine?role=requester&state=LOST"), requester).andExpect(status().isBadRequest());
		send(get("/requests/mine?role=requester&state=CREATED,LOST"), requester)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith("comma-separated")));
	}

	@Test
	void listsArePagedAndCheckTheirPageParameters() throws Exception {
		for (int i = 0; i < 3; i++) {
			create();
		}

		JsonNode first = getJson("/requests/mine?role=requester&size=2", requester);
		assertEquals(2, first.get("content").size());
		assertEquals(0, first.get("page").intValue());
		assertEquals(2, first.get("size").intValue());
		assertEquals(3, first.get("totalElements").longValue());
		assertEquals(2, first.get("totalPages").intValue());
		assertEquals(1, getJson("/requests/mine?role=requester&size=2&page=1", requester).get("content").size());

		send(get("/requests/mine?role=requester&size=0"), requester)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
		send(get("/requests?size=101"), requester).andExpect(status().isBadRequest());
		send(get("/requests?page=-1"), requester)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("page must be 0 or more"));
	}

	@Test
	void adminViewListsAUsersRequestsInEitherRole() throws Exception {
		Long asRequester = createdRequest();
		Long other = NEXT_USER.incrementAndGet();
		Long asCourier = createdRequestBy(other);
		requestsService.accept(asCourier, requester);

		assertEquals(List.of(asCourier, asRequester), ids(getJson("/users/" + requester + "/requests", stranger, "ADMIN")));
		assertEquals(List.of(asCourier),
				ids(getJson("/users/" + requester + "/requests?state=ACCEPTED", stranger, "OWNER")));
	}

	@Test
	void adminViewIsForbiddenToOrdinaryUsers() throws Exception {
		send(get("/users/" + requester + "/requests"), requester).andExpect(status().isForbidden());
		mockMvc.perform(get("/users/" + requester + "/requests")).andExpect(status().isUnauthorized());
	}

	@Test
	void mineFiltersBySupplierAndRewardRange() throws Exception {
		Long cheapDeck = createWith(2, 1, "2099-01-01T00:00:00Z");
		Long dearDeck = createWith(2, 9, "2099-01-01T00:00:00Z");
		Long midTechno = createWith(5, 4, "2099-01-01T00:00:00Z");

		assertEquals(List.of(dearDeck, cheapDeck), ids(getJson("/requests/mine?role=requester&supplierId=2", requester)));
		assertEquals(List.of(midTechno, dearDeck),
				ids(getJson("/requests/mine?role=requester&minCredit=4", requester)));
		assertEquals(List.of(midTechno), ids(getJson("/requests/mine?role=requester&minCredit=2&maxCredit=4", requester)));
		assertEquals(List.of(cheapDeck),
				ids(getJson("/requests/mine?role=requester&supplierId=2&maxCredit=1", requester)));
	}

	@Test
	void mineFiltersByDeadlineAndCreatedRanges() throws Exception {
		Long soon = createWith(2, 3, "2098-01-01T00:00:00Z");
		Long later = createWith(2, 3, "2099-06-01T00:00:00Z");
		Long createdLongAgo = createWith(2, 3, "2099-01-01T00:00:00Z");
		setCreatedAt(createdLongAgo, Instant.parse("2020-01-01T00:00:00Z"));

		assertEquals(List.of(later, createdLongAgo),
				ids(getJson("/requests/mine?role=requester&deadlineAfter=2098-06-01T00:00:00Z", requester)));
		assertEquals(List.of(soon),
				ids(getJson("/requests/mine?role=requester&deadlineBefore=2099-01-01T00:00:00Z", requester)));
		// the lower bound is inclusive, the upper exclusive
		assertEquals(List.of(createdLongAgo),
				ids(getJson("/requests/mine?role=requester&deadlineAfter=2099-01-01T00:00:00Z"
						+ "&deadlineBefore=2099-06-01T00:00:00Z", requester)));
		assertEquals(List.of(createdLongAgo),
				ids(getJson("/requests/mine?role=requester&createdTo=2021-01-01T00:00:00%2B08:00", requester)));
		assertEquals(List.of(later, soon),
				ids(getJson("/requests/mine?role=requester&createdFrom=2021-01-01T00:00:00Z", requester)));
	}

	@Test
	void sortByDeadlinePutsTheNearestFirst() throws Exception {
		Long far = createWith(2, 3, "2099-06-01T00:00:00Z");
		Long near = createWith(2, 3, "2098-01-01T00:00:00Z");
		Long middle = createWith(2, 3, "2099-01-01T00:00:00Z");

		assertEquals(List.of(middle, near, far), ids(getJson("/requests/mine?role=requester", requester)));
		assertEquals(List.of(middle, near, far), ids(getJson("/requests/mine?role=requester&sort=createdAt", requester)));
		assertEquals(List.of(near, middle, far), ids(getJson("/requests/mine?role=requester&sort=deadline", requester)));
	}

	@Test
	void openRequestsAndTheAdminViewTakeTheFiltersToo() throws Exception {
		long supplier = NEXT_USER.incrementAndGet(); // a supplier id no other test uses
		Long match = createWith(supplier, 5, "2099-01-01T00:00:00Z");
		requestsService.reserve(match);
		Long tooCheap = createWith(supplier, 1, "2099-01-01T00:00:00Z");
		requestsService.reserve(tooCheap);

		assertEquals(List.of(match), ids(getJson("/requests?supplierId=" + supplier + "&minCredit=2", stranger)));
		assertEquals(List.of(match),
				ids(getJson("/users/" + requester + "/requests?minCredit=2&sort=deadline", stranger, "ADMIN")));
	}

	@Test
	void badFilterValuesAreBadRequests() throws Exception {
		String mine = "/requests/mine?role=requester&";
		expectBadRequest(mine + "minCredit=0", "minCredit must be at least 1");
		expectBadRequest(mine + "minCredit=5&maxCredit=2", "minCredit must not be above maxCredit");
		expectBadRequest(mine + "deadlineAfter=2099-01-01T00:00:00Z&deadlineBefore=2099-01-01T00:00:00Z",
				"deadlineAfter must be before deadlineBefore");
		expectBadRequest(mine + "createdFrom=2026-02-01T00:00:00Z&createdTo=2026-01-01T00:00:00Z",
				"createdFrom must be before createdTo");
		expectBadRequest(mine + "sort=price", "sort must be createdAt or deadline");
		expectBadRequest(mine + "minCredit=abc", "Invalid value for minCredit");
		expectBadRequest(mine + "createdFrom=2026-01-01", "Invalid value for createdFrom");
		expectBadRequest("/requests?supplierId=deck", "Invalid value for supplierId");
		expectBadRequest(mine + "page=first", "Invalid value for page");
	}

	// --- admin tools (issue #240, DSP-5 and DSP-10)

	@Test
	void adminListFiltersEveryUsersRequestsByStateAndCloseReason() throws Exception {
		long supplier = NEXT_USER.incrementAndGet(); // scopes the all-users list to this test
		Long cancelled = createdRequestWith(requester, supplier);
		requestsService.cancel(cancelled, requester);
		Long timedOut = createWith(stranger, supplier, 5, "2099-01-01T00:00:00Z");
		requestsService.timeout(timedOut);
		Long open = createdRequestWith(requester, supplier);
		String all = "/requests/admin?supplierId=" + supplier;

		assertEquals(List.of(open, timedOut, cancelled), ids(getJson(all, stranger, "ADMIN")));
		assertEquals(List.of(timedOut, cancelled), ids(getJson(all + "&state=CANCELLED", stranger, "ADMIN")));
		assertEquals(List.of(timedOut),
				ids(getJson(all + "&closeReason=RESERVATION_TIMEOUT", stranger, "OWNER")));
		// several, comma-separated, case forgiven, like state
		assertEquals(List.of(timedOut, cancelled), ids(getJson(
				all + "&closeReason=requester_cancelled,%20RESERVATION_TIMEOUT", stranger, "ADMIN")));
		// a blank closeReason matches every request, as a blank state does
		assertEquals(List.of(open, timedOut, cancelled), ids(getJson(all + "&closeReason=", stranger, "ADMIN")));
		// the shared filters apply too
		assertEquals(List.of(timedOut), ids(getJson(all + "&state=CANCELLED&minCredit=5", stranger, "ADMIN")));
	}

	@Test
	void listsAndTheDetailShowTheCloseReason() throws Exception {
		Long cancelled = createdRequest();
		requestsService.cancel(cancelled, requester);
		Long open = createdRequest();

		JsonNode page = getJson("/requests/mine?role=requester", requester);
		assertEquals("REQUESTER_CANCELLED", item(page, cancelled).get("closeReason").stringValue());
		assertTrue(item(page, open).get("closeReason").isNull(), "present, null while active");
		assertEquals("REQUESTER_CANCELLED", getJson("/requests/" + cancelled, requester).get("closeReason").stringValue());
		assertTrue(getJson("/requests/" + open, requester).get("closeReason").isNull());
	}

	@Test
	void listsAndTheDetailShowTheDispute() throws Exception {
		Long disputed = arrivedRequest();
		requestsService.dispute(disputed, requester, "WRONG_ITEM", "Fish soup");
		Long open = createdRequest();

		JsonNode page = getJson("/requests/mine?role=requester", requester);
		JsonNode dispute = item(page, disputed).get("dispute");
		assertEquals("WRONG_ITEM", dispute.get("category").stringValue());
		assertEquals("Fish soup", dispute.get("disputeNote").stringValue());
		assertNotNull(dispute.get("raisedAt").stringValue());
		assertFalse(dispute.get("autoEscalated").booleanValue());
		assertTrue(dispute.get("resolvedAt").isNull(), "present, null until resolved");
		assertTrue(item(page, open).get("dispute").isNull(), "present, null if never disputed");
		assertTrue(getJson("/requests/" + open, requester).get("dispute").isNull());

		requestsService.resolve(disputed, stranger, 1, 2, "Partly refunded");

		JsonNode detail = getJson("/requests/" + disputed, courier);
		assertEquals("RESOLVED", detail.get("status").stringValue());
		assertEquals("DISPUTE_RESOLVED", detail.get("closeReason").stringValue());
		JsonNode resolved = detail.get("dispute");
		assertEquals("WRONG_ITEM", resolved.get("category").stringValue());
		assertEquals(stranger, resolved.get("resolvedBy").longValue());
		assertNotNull(resolved.get("resolvedAt").stringValue());
		assertEquals(1, resolved.get("courierAmount").intValue());
		assertEquals(2, resolved.get("requesterAmount").intValue());
		assertEquals("Partly refunded", resolved.get("resolutionNote").stringValue());
	}

	@Test
	void theAdminDisputeQueueIsTheDisputedStateWithEachDispute() throws Exception {
		long supplier = NEXT_USER.incrementAndGet(); // scopes the all-users list to this test
		Long disputed = createdRequestWith(requester, supplier);
		requestsService.accept(disputed, courier);
		requestsService.collect(disputed, courier);
		requestsService.arrive(disputed, courier);
		requestsService.dispute(disputed, requester, "DAMAGED", null);
		Long open = createdRequestWith(requester, supplier);

		JsonNode queue = getJson("/requests/admin?state=DISPUTED&supplierId=" + supplier, stranger, "ADMIN");

		assertEquals(List.of(disputed), ids(queue));
		assertEquals("DAMAGED", item(queue, disputed).get("dispute").get("category").stringValue());
		assertTrue(ids(getJson("/requests/admin?supplierId=" + supplier, stranger, "ADMIN")).contains(open));
	}

	@Test
	void adminListIsForAdminsAndChecksItsParameters() throws Exception {
		send(get("/requests/admin"), requester).andExpect(status().isForbidden());
		mockMvc.perform(get("/requests/admin")).andExpect(status().isUnauthorized());
		send(get("/requests/admin?closeReason=LOST"), stranger, "ADMIN")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(
						"closeReason must be one or more of REQUESTER_CANCELLED, RESERVATION_TIMEOUT")));
		send(get("/requests/admin?state=LOST"), stranger, "ADMIN").andExpect(status().isBadRequest());
		send(get("/requests/admin?minCredit=0"), stranger, "ADMIN")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("minCredit must be at least 1"));
	}

	@Test
	void statusesAnswersEachIdOnceWithNullForAnUnknownOne() throws Exception {
		Long open = createdRequest();
		Long cancelled = createdRequest();
		requestsService.cancel(cancelled, requester);

		JsonNode statuses = postJson("/requests/admin/statuses",
				"{\"ids\": [" + open + ", \"" + cancelled + "\", 999999999, " + open + "]}", "ADMIN");

		assertEquals(3, statuses.size());
		assertEquals("CREATED", statuses.get(open.toString()).stringValue());
		assertEquals("CANCELLED", statuses.get(cancelled.toString()).stringValue());
		assertTrue(statuses.get("999999999").isNull(), "an unknown id is present, as null");
		assertEquals(0, postJson("/requests/admin/statuses", "{\"ids\": []}", "OWNER").size());
	}

	@Test
	void statusesChecksItsIdsAndIsForAdmins() throws Exception {
		String tooMany = "{\"ids\": [" + String.join(", ", java.util.Collections.nCopies(101, "1")) + "]}";
		expectStatusesBadRequest(tooMany, "ids must have at most 100 entries");
		expectStatusesBadRequest("{\"ids\": [\"abc\"]}", "ids must be request ids (numbers)");
		expectStatusesBadRequest("{\"ids\": [null]}", "ids must be request ids (numbers)");
		expectStatusesBadRequest("{}", "ids is required");
		send(post("/requests/admin/statuses").contentType(MediaType.APPLICATION_JSON).content("{\"ids\": []}"),
				requester).andExpect(status().isForbidden());
	}

	@Test
	void retrySettlementOverHttp() throws Exception {
		Long cancelled = createdRequest();
		requestsService.cancel(cancelled, requester);
		Long open = createdRequest();

		sendRetry(cancelled, "ADMIN")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Request " + cancelled + " settlement queued for retry"));
		sendRetry(open, "OWNER")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Request " + open + " is not finished"));
		sendRetry(999_999_999L, "ADMIN").andExpect(status().isNotFound());
		sendRetry(cancelled, "USER").andExpect(status().isForbidden());
	}

	// --- helpers

	private ResultActions sendRetry(Long requestId, String role) throws Exception {
		return send(post("/retry-settlement").contentType(MediaType.APPLICATION_JSON)
				.content("{\"requestId\": " + requestId + "}"), stranger, role);
	}

	private void expectStatusesBadRequest(String body, String message) throws Exception {
		send(post("/requests/admin/statuses").contentType(MediaType.APPLICATION_JSON).content(body), stranger, "ADMIN")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(message));
	}

	private JsonNode postJson(String path, String body, String role) throws Exception {
		String response = send(post(path).contentType(MediaType.APPLICATION_JSON).content(body), stranger, role)
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response);
	}

	// A CREATED request by the user from the supplier.
	private Long createdRequestWith(long user, long supplierId) throws Exception {
		Long id = createWith(user, supplierId, 3, "2099-01-01T00:00:00Z");
		requestsService.reserve(id);
		return id;
	}

	private void expectBadRequest(String path, String message) throws Exception {
		send(get(path), requester)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(message));
	}

	// Creates a request as the requester with the given supplier, reward and deadline.
	private Long createWith(long supplierId, int credit, String deadline) throws Exception {
		return createWith(requester, supplierId, credit, deadline);
	}

	// Likewise, as the given user.
	private Long createWith(long user, long supplierId, int credit, String deadline) throws Exception {
		send(post("/create").contentType(MediaType.APPLICATION_JSON).content(
				"{\"supplierId\": " + supplierId + ", \"supplierName\": \"The Deck\", "
						+ "\"dropoffLocation\": \"COM3 Level 2\", \"item\": \"Chicken rice\", "
						+ "\"creditAmount\": " + credit + ", \"deadline\": \"" + deadline + "\"}"), user)
				.andExpect(status().isOk());
		return requestsRepository.findAll().stream()
				.map(r -> r.getId()).max(Comparator.naturalOrder()).orElseThrow();
	}

	private void setCreatedAt(Long id, Instant createdAt) {
		var stored = requestsRepository.findById(id).orElseThrow();
		stored.setCreatedAt(createdAt);
		requestsRepository.save(stored);
	}

	// Creates a request as the requester over HTTP; it stays PENDING.
	private Long create() throws Exception {
		return createBy(requester);
	}

	private Long createBy(long user) throws Exception {
		send(post("/create").contentType(MediaType.APPLICATION_JSON).content("""
				{"supplierId": 2, "supplierName": "The Deck",
				 "dropoffLocation": "COM3 Level 2", "item": "Chicken rice",
				 "creditAmount": 3, "deadline": "2099-01-01T00:00:00Z"}"""), user)
				.andExpect(status().isOk());
		return requestsRepository.findAll().stream()
				.map(r -> r.getId()).max(Comparator.naturalOrder()).orElseThrow();
	}

	// A request the courier has just marked arrived.
	private Long arrivedRequest() throws Exception {
		Long id = createdRequest();
		requestsService.accept(id, courier);
		requestsService.collect(id, courier);
		requestsService.arrive(id, courier);
		return id;
	}

	// A request whose reward is reserved, as after credit.reserved.
	private Long createdRequest() throws Exception {
		return createdRequestBy(requester);
	}

	private Long createdRequestBy(long user) throws Exception {
		Long id = createBy(user);
		requestsService.reserve(id);
		return id;
	}

	private JsonNode getJson(String path, long user) throws Exception {
		return getJson(path, user, "USER");
	}

	private JsonNode getJson(String path, long user, String role) throws Exception {
		// a URI, not a template: a template would encode %2B (an offset's +) again
		String body = send(get(URI.create(path)), user, role).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body);
	}

	private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			long user) throws Exception {
		return send(request, user, "USER");
	}

	private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			long user, String role) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + token(user, role)));
	}

	private static List<Long> ids(JsonNode page) {
		List<Long> ids = new ArrayList<>();
		page.get("content").forEach(item -> ids.add(item.get("id").longValue()));
		return ids;
	}

	private static JsonNode item(JsonNode page, Long id) {
		for (JsonNode item : page.get("content")) {
			if (item.get("id").longValue() == id) {
				return item;
			}
		}
		throw new AssertionError("request " + id + " not in the page");
	}

	// A token as user-service mints it: sub = user id, a role, an expiry.
	private static String token(long userId, String role) {
		return Jwts.builder()
				.subject(Long.toString(userId))
				.claim("role", role)
				.issuedAt(Date.from(Instant.now()))
				.expiration(Date.from(Instant.now().plusSeconds(3600)))
				.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
				.compact();
	}
}
