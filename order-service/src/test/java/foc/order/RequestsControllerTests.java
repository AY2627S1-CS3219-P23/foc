/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13.
 * Scope: tests for the RequestsController HTTP mappings - each endpoint
 * in the author's endpoints.txt, plus the 409 and 404 error responses.
 * 2026-10-15: /create now leaves the request PENDING; the courier flows
 * start from a request the Credit Service has reserved (RequestsService
 * .reserve stands in for its reply).
 * 2026-10-16: /arrive between /collect and /complete.
 * 2026-10-16: statuses compared as OrderStates constants.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

import foc.order.model.Requests;
import foc.order.repository.RequestsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestsControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RequestsRepository requestsRepository;

	@Autowired
	private RequestsService requestsService;

	@Test
	void createStoresRowFromBody() throws Exception {
		Requests stored = stored(create());

		assertEquals(OrderStates.PENDING, stored.getStatus());
		assertEquals(1L, stored.getRequesterId());
		assertEquals(2L, stored.getSupplierId());
		assertEquals("The Deck", stored.getSupplierName());
		assertEquals("COM3 Level 2", stored.getDropoffLocation());
		assertEquals("Chicken rice", stored.getItem());
		assertEquals(3, stored.getCreditAmount());
		assertNotNull(stored.getDeadline());
	}

	@Test
	void acceptCollectArriveCompleteWalkTheRequestToCompleted() throws Exception {
		Long id = createReserved();

		send("/accept", acceptBody(id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Request " + id + " accepted"));
		assertEquals(7L, stored(id).getCourierId());

		send("/collect", idBody(id)).andExpect(status().isOk());
		send("/arrive", idBody(id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Request " + id + " arrived"));
		assertEquals(OrderStates.ARRIVED, stored(id).getStatus());
		send("/complete", idBody(id)).andExpect(status().isOk());

		assertEquals(OrderStates.COMPLETED, stored(id).getStatus());
	}

	@Test
	void dropReturnsAcceptedRequestToCreated() throws Exception {
		Long id = createReserved();
		send("/accept", acceptBody(id));

		send("/drop", idBody(id)).andExpect(status().isOk());

		assertEquals(OrderStates.CREATED, stored(id).getStatus());
		assertNull(stored(id).getCourierId());
	}

	@Test
	void cancelStoresCancelled() throws Exception {
		Long id = createReserved();

		send("/cancel", idBody(id)).andExpect(status().isOk());

		assertEquals(OrderStates.CANCELLED, stored(id).getStatus());
	}

	@Test
	void illegalTransitionIsConflictWithMessage() throws Exception {
		Long id = createReserved();

		send("/complete", idBody(id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Cannot COMPLETE a request that is CREATED"));

		assertEquals(OrderStates.CREATED, stored(id).getStatus());
	}

	@Test
	void pendingRequestCannotBeAcceptedYet() throws Exception {
		Long id = create();

		send("/accept", acceptBody(id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Cannot ACCEPT a request that is PENDING"));

		assertEquals(OrderStates.PENDING, stored(id).getStatus());
	}

	@Test
	void unknownRequestIsNotFoundWithMessage() throws Exception {
		send("/collect", idBody(999_999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Request not found"));
	}

	// Posts /create and returns the id of the row it added.
	private Long create() throws Exception {
		send("/create", """
				{"requesterId": 1, "supplierId": 2, "supplierName": "The Deck",
				 "dropoffLocation": "COM3 Level 2", "item": "Chicken rice",
				 "creditAmount": 3, "deadline": "2099-01-01T00:00:00Z"}""")
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

	private ResultActions send(String path, String json) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String acceptBody(Long id) {
		return "{\"requestId\": " + id + ", \"courierId\": 7}";
	}

	private static String idBody(Long id) {
		return "{\"requestId\": " + id + "}";
	}

	private Requests stored(Long id) {
		return requestsRepository.findById(id).orElseThrow();
	}

}
