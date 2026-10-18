/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13.
 * Scope: tests for the RequestsController HTTP mappings - each endpoint
 * in the author's endpoints.txt, plus the 409 and 404 error responses.
 * 2026-10-15: /create now leaves the request PENDING; the courier flows
 * start from a request the Credit Service has reserved (RequestsService
 * .reserve stands in for its reply).
 * 2026-10-16: /arrive between /collect and /complete.
 * 2026-10-18: 409 when the requester accepts their own request.
 * 2026-10-16: statuses compared as OrderStates constants.
 * 2026-10-18: every call carries a bearer token signed with the test
 * secret (credit-service's test pattern); the requester and courier come
 * from it, a missing token is 401, and a step that is not the caller's
 * is 404.
 * Same day: a /create with missing or invalid fields is 400 listing them.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

import foc.order.model.Requests;
import foc.order.repository.RequestsRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestsControllerTests {

	// order.jwt.secret in the test application.yaml
	private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";

	private static final long REQUESTER = 1L;
	private static final long COURIER = 7L;
	private static final long STRANGER = 9L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RequestsRepository requestsRepository;

	@Autowired
	private RequestsService requestsService;

	@Test
	void createStoresRowFromBodyWithTheCallerAsRequester() throws Exception {
		Requests stored = stored(create());

		assertEquals(OrderStates.PENDING, stored.getStatus());
		assertEquals(REQUESTER, stored.getRequesterId());
		assertEquals(2L, stored.getSupplierId());
		assertEquals("The Deck", stored.getSupplierName());
		assertEquals("COM3 Level 2", stored.getDropoffLocation());
		assertEquals("Chicken rice", stored.getItem());
		assertEquals(3, stored.getCreditAmount());
		assertNotNull(stored.getDeadline());
	}

	@Test
	void createWithMissingOrInvalidFieldsIsBadRequestListingThem() throws Exception {
		long before = requestsRepository.count();

		send("/create", "{\"supplierId\": 2, \"creditAmount\": 0}", REQUESTER)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"Missing required fields: supplierName, dropoffLocation, item, deadline; creditAmount must be at least 1"));

		assertEquals(before, requestsRepository.count());
	}

	@Test
	void acceptCollectArriveCompleteWalkTheRequestToCompleted() throws Exception {
		Long id = createReserved();

		send("/accept", idBody(id), COURIER)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Request " + id + " accepted"));
		assertEquals(COURIER, stored(id).getCourierId());

		send("/collect", idBody(id), COURIER).andExpect(status().isOk());
		send("/arrive", idBody(id), COURIER)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Request " + id + " arrived"));
		assertEquals(OrderStates.ARRIVED, stored(id).getStatus());
		send("/complete", idBody(id), REQUESTER).andExpect(status().isOk());

		assertEquals(OrderStates.COMPLETED, stored(id).getStatus());
	}

	@Test
	void dropReturnsAcceptedRequestToCreated() throws Exception {
		Long id = createReserved();
		send("/accept", idBody(id), COURIER);

		send("/drop", idBody(id), COURIER).andExpect(status().isOk());

		assertEquals(OrderStates.CREATED, stored(id).getStatus());
		assertNull(stored(id).getCourierId());
	}

	@Test
	void cancelStoresCancelled() throws Exception {
		Long id = createReserved();

		send("/cancel", idBody(id), REQUESTER).andExpect(status().isOk());

		assertEquals(OrderStates.CANCELLED, stored(id).getStatus());
	}

	@Test
	void illegalTransitionIsConflictWithMessage() throws Exception {
		Long id = createReserved();

		send("/complete", idBody(id), REQUESTER)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Cannot COMPLETE a request that is CREATED"));

		assertEquals(OrderStates.CREATED, stored(id).getStatus());
	}

	@Test
	void requesterAcceptingTheirOwnRequestIsConflict() throws Exception {
		Long id = createReserved();

		send("/accept", idBody(id), REQUESTER)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("A requester cannot accept their own request"));

		assertEquals(OrderStates.CREATED, stored(id).getStatus());
		assertNull(stored(id).getCourierId());
	}

	@Test
	void pendingRequestCannotBeAcceptedYet() throws Exception {
		Long id = create();

		send("/accept", idBody(id), COURIER)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Cannot ACCEPT a request that is PENDING"));

		assertEquals(OrderStates.PENDING, stored(id).getStatus());
	}

	@Test
	void stepThatIsNotTheCallersIsNotFound() throws Exception {
		Long id = createReserved();
		send("/accept", idBody(id), COURIER);

		// courier-only steps, by the requester and by a stranger
		send("/collect", idBody(id), REQUESTER)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Request not found"));
		send("/drop", idBody(id), STRANGER).andExpect(status().isNotFound());
		// requester-only steps, by the courier and by a stranger
		send("/cancel", idBody(id), COURIER).andExpect(status().isNotFound());
		send("/complete", idBody(id), STRANGER).andExpect(status().isNotFound());

		assertEquals(OrderStates.ACCEPTED, stored(id).getStatus());
		assertEquals(COURIER, stored(id).getCourierId());
	}

	@Test
	void unknownRequestIsNotFoundWithMessage() throws Exception {
		send("/collect", idBody(999_999L), COURIER)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Request not found"));
	}

	@Test
	void missingOrInvalidTokenIsUnauthorized() throws Exception {
		Long id = createReserved();

		mockMvc.perform(post("/accept").contentType(MediaType.APPLICATION_JSON).content(idBody(id)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/accept").contentType(MediaType.APPLICATION_JSON).content(idBody(id))
						.header("Authorization", "Bearer not-a-token"))
				.andExpect(status().isUnauthorized());

		assertEquals(OrderStates.CREATED, stored(id).getStatus());
	}

	// Posts /create as the requester and returns the id of the row it added.
	private Long create() throws Exception {
		send("/create", """
				{"supplierId": 2, "supplierName": "The Deck",
				 "dropoffLocation": "COM3 Level 2", "item": "Chicken rice",
				 "creditAmount": 3, "deadline": "2099-01-01T00:00:00Z"}""", REQUESTER)
				.andExpect(status().isOk());
		return requestsRepository.findAll().stream()
				.map(Requests::getId).max(Comparator.naturalOrder()).orElseThrow();
	}

	// A created request whose reward is reserved, as after credit.reserved.
	private Long createReserved() throws Exception {
		Long id = create();
		requestsService.reserve(id);
		return id;
	}

	// Posts the body as the given user.
	private ResultActions send(String path, String json, long userId) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json)
				.header("Authorization", "Bearer " + token(userId)));
	}

	// A token as user-service mints it: sub = user id, a role, an expiry.
	private static String token(long userId) {
		return Jwts.builder()
				.subject(Long.toString(userId))
				.claim("role", "USER")
				.issuedAt(Date.from(Instant.now()))
				.expiration(Date.from(Instant.now().plusSeconds(3600)))
				.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
				.compact();
	}

	private static String idBody(Long id) {
		return "{\"requestId\": " + id + "}";
	}

	private Requests stored(Long id) {
		return requestsRepository.findById(id).orElseThrow();
	}

}
