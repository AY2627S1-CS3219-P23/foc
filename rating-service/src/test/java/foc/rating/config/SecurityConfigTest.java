/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-26, issue #250.
 * Scope: tests for the walking skeleton's filter chain, scaffolded from
 * notification-service's SecurityConfigTest: health is open, every
 * other route needs a valid platform JWT (401 problem+json with a
 * Bearer challenge otherwise), no request creates a session, and the
 * JWT filter is not a bean. No rating routes exist yet, so the
 * valid-token case asserts the request gets past security to a 404.
 * Reviewed by: [pending]
 */
package foc.rating.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.rating.security.JwtAuthenticationFilter;
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

	// the test secret (src/test/resources/application.yaml)
	private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext context;

	private static String token(String secret, Instant expiry) {
		return Jwts.builder()
				.subject("usr-req-1001")
				.claim("role", "REQUESTER")
				.issuedAt(Date.from(Instant.now()))
				.expiration(Date.from(expiry))
				.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
				.compact();
	}

	@Test
	@DisplayName("Health needs no token")
	void healthIsOpen() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	@DisplayName("No token is 401 problem+json with a Bearer challenge, and no session")
	void missingTokenIsUnauthorized() throws Exception {
		MvcResult result = mockMvc.perform(get("/ratings"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("A valid bearer token is required"))
				.andReturn();
		// a rejected request must not leave a session behind (stateless)
		assertThat(result.getRequest().getSession(false)).isNull();
	}

	@Test
	@DisplayName("A token that is not a JWT is 401")
	void invalidTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/ratings").header("Authorization", "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("A token signed with another secret is 401")
	void wrongSecretIsUnauthorized() throws Exception {
		String forged = token("another-secret-that-is-also-32-bytes-long!", Instant.now().plusSeconds(3600));
		mockMvc.perform(get("/ratings").header("Authorization", "Bearer " + forged))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("An expired token is 401")
	void expiredTokenIsUnauthorized() throws Exception {
		String expired = token(SECRET, Instant.now().minusSeconds(60));
		mockMvc.perform(get("/ratings").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("\"Bearer \" with no token is 401, not 500")
	void emptyBearerIsUnauthorized() throws Exception {
		mockMvc.perform(get("/ratings").header("Authorization", "Bearer "))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("A valid token gets past security, with no session")
	void validTokenPassesSecurity() throws Exception {
		// no rating routes exist yet, so past security means a plain 404
		// (not the chain's 401/403)
		String valid = token(SECRET, Instant.now().plusSeconds(3600));
		MvcResult result = mockMvc.perform(get("/ratings").header("Authorization", "Bearer " + valid))
				.andExpect(status().isNotFound())
				.andReturn();
		assertThat(result.getRequest().getSession(false)).isNull();
	}

	@Test
	@DisplayName("The JWT filter is not a bean, so the servlet container doesn't run it a second time")
	void jwtFilterRunsOnlyInTheSecurityChain() {
		assertThat(context.getBeanProvider(JwtAuthenticationFilter.class).getIfAvailable()).isNull();
	}
}
