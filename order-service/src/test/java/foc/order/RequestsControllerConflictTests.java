/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-20.
 * Scope: a step refused by the @Version check (it lost a race to another
 * step on the same request) is answered 409 "This request's state has
 * changed" (the author's decision). RequestsService is mocked to throw
 * what JPA throws then; RequestsServiceTests stages the real race.
 * Author review: Aung Ko Khant, pending pull request review.
 */
package foc.order;

import foc.order.model.Requests;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestsControllerConflictTests {

	// order.jwt.secret in the test application.yaml
	private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RequestsService requestsService;

	@Test
	void stepThatLostARaceIsConflictSayingTheStateChanged() throws Exception {
		when(requestsService.accept(anyLong(), anyLong()))
				.thenThrow(new ObjectOptimisticLockingFailureException(Requests.class, 1L));

		mockMvc.perform(post("/accept").contentType(MediaType.APPLICATION_JSON)
						.content("{\"requestId\": 1}")
						.header("Authorization", "Bearer " + token(7L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("This request's state has changed"));
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

}
