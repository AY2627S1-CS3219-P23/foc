/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-09.
 * Scope: tests for the scaffolded filter chain: health is open, every
 * other route needs a valid platform JWT (401 problem+json otherwise),
 * and a valid token gets past security. Tokens are minted the way
 * user-service's JwtIssuer mints them.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.credit.PostgresTestContainer;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest extends PostgresTestContainer {

    // the test profile's secret (application-test.yaml)
    private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";

    @Autowired
    private MockMvc mockMvc;

    private static String token(String secret, Instant expiry) {
        return Jwts.builder()
                .subject("42")
                .claim("role", "USER")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    @DisplayName("Health needs no token")
    void healthIsOpen() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("No token is 401 problem+json")
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/credits/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("A valid bearer token is required"));
    }

    @Test
    @DisplayName("A token signed with another secret is 401")
    void wrongSecretIsUnauthorized() throws Exception {
        String forged = token("another-secret-that-is-also-32-bytes-long!", Instant.now().plusSeconds(3600));
        mockMvc.perform(get("/credits/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An expired token is 401")
    void expiredTokenIsUnauthorized() throws Exception {
        String expired = token(SECRET, Instant.now().minusSeconds(60));
        mockMvc.perform(get("/credits/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("\"Bearer \" with no token is 401, not 500")
    void emptyBearerIsUnauthorized() throws Exception {
        mockMvc.perform(get("/credits/me").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A valid token gets past security (404: no endpoints yet)")
    void validTokenPassesSecurity() throws Exception {
        String valid = token(SECRET, Instant.now().plusSeconds(3600));
        mockMvc.perform(get("/credits/me").header("Authorization", "Bearer " + valid))
                .andExpect(status().isNotFound());
    }
}
