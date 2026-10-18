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
 * ranges), the sort parameter, and their 400s.
 * Reviewed by: Ko-Khan (via pull request).
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
class RequestsReadEndpointsTests {

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
	void mineNeedsAValidRoleAndState() throws Exception {
		send(get("/requests/mine"), requester)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("role is required: requester or courier"));
		send(get("/requests/mine?role=admin"), requester)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("role must be requester or courier"));
		send(get("/requests/mine?role=requester&state=LOST"), requester).andExpect(status().isBadRequest());
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

	// --- helpers

	private void expectBadRequest(String path, String message) throws Exception {
		send(get(path), requester)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(message));
	}

	// Creates a request as the requester with the given supplier, reward and deadline.
	private Long createWith(long supplierId, int credit, String deadline) throws Exception {
		send(post("/create").contentType(MediaType.APPLICATION_JSON).content(
				"{\"supplierId\": " + supplierId + ", \"supplierName\": \"The Deck\", "
						+ "\"dropoffLocation\": \"COM3 Level 2\", \"item\": \"Chicken rice\", "
						+ "\"creditAmount\": " + credit + ", \"deadline\": \"" + deadline + "\"}"), requester)
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
