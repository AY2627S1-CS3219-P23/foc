/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: tests for the REST filter chain, scaffolded from
 * credit-service's SecurityConfigTest: health is open, every other
 * route needs a valid platform JWT (401 problem+json with a Bearer
 * challenge otherwise), no request creates a session, and the JWT
 * filter is not a bean. One addition is this service's own: the
 * SockJS handshake path /ws/info must stay open, because the #67
 * STOMP client authenticates at the CONNECT frame (D23), not the
 * HTTP upgrade — this is the regression canary for that feature.
 * 2026-10-17, PR #198 Codex review: the REST CORS policy must not
 * police the WS paths — pinned with a handshake-origin case (the /**
 * registration was 403ing an Origin the WS config allows) and a REST
 * preflight case proving the narrowed registration still answers.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

	// the test profile's secret (src/test/resources/application.yaml)
	private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext context;

	private static String token(String secret, Instant expiry) {
		return Jwts.builder()
				.subject("usr-req-1001")
				.issuedAt(Date.from(Instant.now()))
				.expiration(Date.from(expiry))
				.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
				.compact();
	}

	@Test
	@DisplayName("Health needs no token")
	void healthIsOpen() throws Exception {
		// no broker runs in tests, so the aggregate may be 503 DOWN; the
		// chain contract under test is only that security never 401s it
		MvcResult health = mockMvc.perform(get("/actuator/health")).andReturn();
		assertThat(health.getResponse().getStatus()).isNotEqualTo(401);
		assertThat(health.getResponse().getContentAsString()).contains("status");
	}

	@Test
	@DisplayName("The SockJS handshake stays open — the STOMP CONNECT frame authenticates, not HTTP")
	void webSocketHandshakeIsOpen() throws Exception {
		// /ws/info is the first request every SockJS client makes, and a
		// browser sends it with no Authorization header; a 401 here would
		// break the #67 push client outright
		mockMvc.perform(get("/ws/info")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("The REST CORS policy keeps its hands off the WS paths (PR #198 review)")
	void restCorsDoesNotPoliceWebSocketPaths() throws Exception {
		// an Origin the WS config allows (http://localhost:* in the test
		// yaml) but the single REST WEB_ALLOWED_ORIGIN does not — with the
		// CORS registration at /**, this was a 403 before the handshake
		mockMvc.perform(get("/ws/info").header("Origin", "http://localhost:4000"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("A REST preflight from the SPA origin still answers after the narrowed CORS mapping")
	void restPreflightStillAnswers() throws Exception {
		mockMvc.perform(options("/notifications/me")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "PATCH"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	@DisplayName("No token is 401 problem+json with a Bearer challenge, and no session")
	void missingTokenIsUnauthorized() throws Exception {
		MvcResult result = mockMvc.perform(get("/notifications/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("A valid bearer token is required"))
				.andReturn();
		// a rejected request must not leave a session behind (stateless)
		assertThat(result.getRequest().getSession(false)).isNull();
	}

	@Test
	@DisplayName("A token signed with another secret is 401")
	void wrongSecretIsUnauthorized() throws Exception {
		String forged = token("another-secret-that-is-also-32-bytes-long!", Instant.now().plusSeconds(3600));
		mockMvc.perform(get("/notifications/me").header("Authorization", "Bearer " + forged))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("An expired token is 401")
	void expiredTokenIsUnauthorized() throws Exception {
		String expired = token(SECRET, Instant.now().minusSeconds(60));
		mockMvc.perform(get("/notifications/me").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("\"Bearer \" with no token is 401, not 500")
	void emptyBearerIsUnauthorized() throws Exception {
		mockMvc.perform(get("/notifications/me").header("Authorization", "Bearer "))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("A valid token gets past security to the list, with no session")
	void validTokenPassesSecurity() throws Exception {
		String valid = token(SECRET, Instant.now().plusSeconds(3600));
		MvcResult result = mockMvc.perform(get("/notifications/me").header("Authorization", "Bearer " + valid))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getRequest().getSession(false)).isNull();
	}

	@Test
	@DisplayName("The JWT filter is not a bean, so the servlet container doesn't run it a second time")
	void jwtFilterRunsOnlyInTheSecurityChain() {
		assertThat(context.getBeanProvider(JwtAuthenticationFilter.class).getIfAvailable()).isNull();
	}
}
