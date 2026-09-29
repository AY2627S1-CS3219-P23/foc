/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-29.
 * Scope: integration tests for issue #106's security filter chain —
 * missing/invalid bearer tokens get a 401 problem+json, an
 * authenticated non-admin gets a 403 problem+json on a would-be admin
 * route, and a valid token of any role can reach the (currently
 * read-only) browse endpoints. The role-gate's 200 path for an actual
 * admin CRUD endpoint is left for #6, which adds those routes.
 * Reviewed by: [pending]
 */
package foc.supplier.config;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    // matches src/test/resources/application.yaml's supplier.jwt.secret
    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    private static String token(String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("1")
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    @Test
    void healthCheckNeedsNoToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void listSuppliersRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/suppliers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", equalTo(401)));
    }

    @Test
    void listSuppliersRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/suppliers").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", equalTo(401)));
    }

    @Test
    void listSuppliersAcceptsAnyAuthenticatedRole() throws Exception {
        mockMvc.perform(get("/suppliers").header("Authorization", "Bearer " + token("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminIsForbiddenFromWriteMethods() throws Exception {
        mockMvc.perform(post("/suppliers").header("Authorization", "Bearer " + token("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", equalTo(403)));
    }

    @Test
    void adminPassesTheRoleGateOnWriteMethods() throws Exception {
        // no POST /suppliers handler exists yet (#6): reaching the
        // dispatcher (405, not 403) proves the role gate let it through.
        mockMvc.perform(post("/suppliers").header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isMethodNotAllowed());
    }
}
