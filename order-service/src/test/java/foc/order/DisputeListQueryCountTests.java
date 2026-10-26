/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-26.
 * Scope: issue #239: the lists load a page's disputes in one query, not
 * one per row (team decision), checked by counting the statements
 * Hibernate prepares for a page with few and with many disputes. Follows
 * RequestsReadEndpointsTests' signed-token pattern.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.order;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import foc.order.model.Requests;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Statistics on for this class only (its own application context).
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class DisputeListQueryCountTests extends PostgresTestContainer {

	// order.jwt.secret in the test application.yaml
	private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";

	// fresh user ids, far from the other test classes'
	private static final AtomicLong NEXT_USER = new AtomicLong(200_000);

	private static final long COURIER = 7L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RequestsService requestsService;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void aPageOfDisputedRequestsTakesAsManyQueriesAsAPageWithOne() throws Exception {
		long fewDisputes = requesterWithDisputes(1);
		long manyDisputes = requesterWithDisputes(6);

		long few = statementsToList(fewDisputes, 1);
		long many = statementsToList(manyDisputes, 6);

		assertEquals(few, many, "statements must not grow with the disputed rows");
	}

	@Test
	void aPageWithNoDisputedRequestsLoadsNoDisputes() throws Exception {
		long user = NEXT_USER.incrementAndGet();
		createdRequest(user);
		long withDispute = requesterWithDisputes(1);

		// the page query alone, against the page query plus the disputes query
		assertEquals(statementsToList(withDispute, 1) - 1, statementsToList(user, 0));
	}

	// Lists the user's requests as an admin would, checks every row carries
	// its dispute, and returns the statements Hibernate prepared for it.
	private long statementsToList(long user, int disputed) throws Exception {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		String body = mockMvc.perform(get("/users/" + user + "/requests")
						.header("Authorization", "Bearer " + token(NEXT_USER.incrementAndGet(), "ADMIN")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		long statements = statistics.getPrepareStatementCount();

		JsonNode content = jsonMapper.readTree(body).get("content");
		int withDispute = 0;
		for (JsonNode item : content) {
			if (!item.get("dispute").isNull()) {
				withDispute++;
			}
		}
		assertEquals(disputed, withDispute);
		return statements;
	}

	// A new requester with this many disputed requests.
	private long requesterWithDisputes(int count) {
		long user = NEXT_USER.incrementAndGet();
		for (int i = 0; i < count; i++) {
			Long id = createdRequest(user);
			requestsService.accept(id, COURIER);
			requestsService.collect(id, COURIER);
			requestsService.arrive(id, COURIER);
			requestsService.dispute(id, user, "NOT_DELIVERED", null);
		}
		return user;
	}

	private Long createdRequest(long user) {
		Requests request = new Requests();
		request.setRequesterId(user);
		request.setSupplierId(2L);
		request.setSupplierName("The Deck");
		request.setDropoffLocation("COM3 Level 2");
		request.setItem("Chicken rice");
		request.setCreditAmount(3);
		request.setDeadline(Instant.now().plusSeconds(3600));
		Long id = requestsService.create(request).getId();
		requestsService.reserve(id);
		return id;
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
