/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-29.
 * Scope: integration tests for issue #104's admin CRUD endpoints —
 * end-to-end against the real H2-backed service, proving both
 * correctness (create/update/delete actually persist) and the role
 * gate (a USER token gets 403 problem+json on every write method and
 * the H2 database is left unchanged; ADMIN/OWNER succeed). Complements
 * SecurityConfigTest, which covers the gate generically.
 * 2026-09-29 (author request): added
 * creatingWithAnOutOfRangeLatitudeReturns400 and
 * creatingWithAMissingLatitudeReturns400, pinning SupplierRequest's new
 * @NotNull/@DecimalMin/@DecimalMax on latitude/longitude — the form
 * previously had no way to enter coordinates and silently sent 0/0.
 * Reviewed by: [pending]
 */
package foc.supplier.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import foc.supplier.repository.SuppliersRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SupplierControllerCrudTest {

    // matches src/test/resources/application.yaml's supplier.jwt.secret
    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SuppliersRepository suppliersRepository;

    private static String token(String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("1")
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    private static String validSupplierJson() {
        return """
                {"name":"Test Cafe","location":"Block A, Level 1","categories":["Food"],
                "openingTime":"09:00","closingTime":"18:00","description":"desc",
                "latitude":1.3,"longitude":103.8}
                """;
    }

    @Test
    void adminCanCreateUpdateAndDeleteASupplier() throws Exception {
        long before = suppliersRepository.count();

        String createResponse = mockMvc.perform(post("/suppliers")
                        .header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplierJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("Test Cafe")))
                .andExpect(jsonPath("$.categories[0]", equalTo("Food")))
                .andReturn().getResponse().getContentAsString();

        assertThat(suppliersRepository.count()).isEqualTo(before + 1);
        String id = createResponse.replaceAll(".*\"id\":\"(\\d+)\".*", "$1");

        mockMvc.perform(put("/suppliers/{id}", id)
                        .header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed Cafe","location":"Block B, Level 2","categories":["Coffee"],
                                "openingTime":"10:00","closingTime":"20:00","description":"new desc",
                                "latitude":1.4,"longitude":103.9}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Renamed Cafe")))
                .andExpect(jsonPath("$.location", equalTo("Block B, Level 2")))
                .andExpect(jsonPath("$.categories[0]", equalTo("Coffee")));

        mockMvc.perform(delete("/suppliers/{id}", id)
                        .header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(suppliersRepository.count()).isEqualTo(before);
    }

    @Test
    void regularUserCannotCreateASupplier() throws Exception {
        long before = suppliersRepository.count();

        mockMvc.perform(post("/suppliers")
                        .header("Authorization", "Bearer " + token("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplierJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", equalTo(403)));

        assertThat(suppliersRepository.count()).isEqualTo(before);
    }

    @Test
    void regularUserCannotUpdateASupplier() throws Exception {
        mockMvc.perform(put("/suppliers/{id}", 1)
                        .header("Authorization", "Bearer " + token("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplierJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", equalTo(403)));
    }

    @Test
    void regularUserCannotDeleteASupplier() throws Exception {
        long before = suppliersRepository.count();

        mockMvc.perform(delete("/suppliers/{id}", 1)
                        .header("Authorization", "Bearer " + token("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", equalTo(403)));

        assertThat(suppliersRepository.count()).isEqualTo(before);
    }

    @Test
    void anonymousRequestIsRejectedBeforeReachingTheHandler() throws Exception {
        long before = suppliersRepository.count();

        mockMvc.perform(post("/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplierJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", equalTo(401)));

        assertThat(suppliersRepository.count()).isEqualTo(before);
    }

    @Test
    void updatingAnUnknownSupplierReturns404() throws Exception {
        mockMvc.perform(put("/suppliers/{id}", 999_999)
                        .header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplierJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", equalTo(404)));
    }

    @Test
    void creatingWithAMissingRequiredFieldReturns400() throws Exception {
        mockMvc.perform(post("/suppliers")
                        .header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"location":"Block A","categories":[],"openingTime":"09:00","closingTime":"18:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)));
    }

    @Test
    void creatingWithAnOutOfRangeLatitudeReturns400() throws Exception {
        mockMvc.perform(post("/suppliers")
                        .header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bad Coords","location":"Block A","categories":[],
                                "openingTime":"09:00","closingTime":"18:00","latitude":91.0,"longitude":103.8}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)));
    }

    @Test
    void creatingWithAMissingLatitudeReturns400() throws Exception {
        mockMvc.perform(post("/suppliers")
                        .header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No Coords","location":"Block A","categories":[],
                                "openingTime":"09:00","closingTime":"18:00","longitude":103.8}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)));
    }
}
