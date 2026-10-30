/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: MockMvc tests of the two routes against H2, real self-signed
 * HS256 tokens (SecurityConfigTest's helper, order-service's
 * controller-test style): the happy path in both directions, the four
 * refusals from the issue, the validation boundaries, /mine's
 * null-until-rated wrapper, and the advice's 409 for the unique-key
 * race the pre-check loses.
 * Author review: [pending]
 */
package foc.rating.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.rating.entity.RateableRequest;
import foc.rating.repository.RateableRequestRepository;
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
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class RatingControllerTest {

    // the test secret (src/test/resources/application.yaml)
    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    private static final String REQUESTER = "usr-req-1001";
    private static final String COURIER = "usr-cou-2002";
    private static final String STRANGER = "usr-str-9999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateableRequestRepository rateableRequests;

    private static String token(String userId) {
        return Jwts.builder()
                .subject(userId)
                .claim("role", "REQUESTER")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    // each test seeds its own request: the cached context shares one H2
    // database across test classes
    private String completedRequest(String requestId) {
        rateableRequests.save(new RateableRequest(requestId, REQUESTER, COURIER,
                Instant.parse("2026-09-19T09:30:00Z")));
        return requestId;
    }

    private ResultActions submit(String caller, String json) throws Exception {
        return mockMvc.perform(post("/ratings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .header("Authorization", "Bearer " + token(caller)));
    }

    private ResultActions mine(String caller, String requestId) throws Exception {
        return mockMvc.perform(get("/ratings/mine")
                .param("requestId", requestId)
                .header("Authorization", "Bearer " + token(caller)));
    }

    private static String body(String requestId, String stars, String text) {
        return "{\"requestId\":\"" + requestId + "\""
                + (stars == null ? "" : ",\"stars\":" + stars)
                + (text == null ? "" : ",\"text\":\"" + text + "\"")
                + "}";
    }

    @Test
    @DisplayName("The requester rates the courier: 201 with the stored rating")
    void requesterRatesCourier() throws Exception {
        String requestId = completedRequest("req-ctl-1");

        submit(REQUESTER, body(requestId, "5", "Fast and friendly"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.raterId").value(REQUESTER))
                .andExpect(jsonPath("$.rateeId").value(COURIER))
                .andExpect(jsonPath("$.stars").value(5))
                .andExpect(jsonPath("$.text").value("Fast and friendly"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("The courier rates the requester: the ratee flips")
    void courierRatesRequester() throws Exception {
        String requestId = completedRequest("req-ctl-2");

        submit(COURIER, body(requestId, "4", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.raterId").value(COURIER))
                .andExpect(jsonPath("$.rateeId").value(REQUESTER))
                .andExpect(jsonPath("$.stars").value(4))
                // no write-up is a valid rating (stars required, text optional)
                .andExpect(jsonPath("$.text").value(nullValue()));
    }

    @Test
    @DisplayName("No token is 401")
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/ratings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("req-ctl-3", "5", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An unknown request is 404 problem+json")
    void unknownRequestIsNotFound() throws Exception {
        submit(REQUESTER, body("req-ctl-never-completed", "5", null))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No completed request with that id"));
    }

    @Test
    @DisplayName("A caller who is neither party is 403 problem+json")
    void strangerIsForbidden() throws Exception {
        String requestId = completedRequest("req-ctl-4");

        submit(STRANGER, body(requestId, "5", null))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Only a party of the request may rate or view its ratings"));
    }

    @Test
    @DisplayName("A repeat rating is 409 problem+json; the first survives unchanged")
    void repeatRatingConflicts() throws Exception {
        String requestId = completedRequest("req-ctl-5");
        submit(REQUESTER, body(requestId, "5", "First")).andExpect(status().isCreated());

        submit(REQUESTER, body(requestId, "1", "Changed my mind"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("You have already rated this request"));

        // immutable: the stored rating still carries the first submission
        mine(REQUESTER, requestId)
                .andExpect(jsonPath("$.rating.stars").value(5))
                .andExpect(jsonPath("$.rating.text").value("First"));
    }

    @Test
    @DisplayName("Each direction is its own rating: the courier may still rate after the requester")
    void directionsAreIndependent() throws Exception {
        String requestId = completedRequest("req-ctl-6");
        submit(REQUESTER, body(requestId, "5", null)).andExpect(status().isCreated());

        submit(COURIER, body(requestId, "3", null)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Missing stars is 400")
    void missingStarsIsBadRequest() throws Exception {
        submit(REQUESTER, body(completedRequest("req-ctl-7"), null, null))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("stars is required")));
    }

    @Test
    @DisplayName("Stars outside 1-5 is 400")
    void starsOutOfRangeIsBadRequest() throws Exception {
        String requestId = completedRequest("req-ctl-8");
        submit(REQUESTER, body(requestId, "0", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("stars must be between 1 and 5")));
        submit(REQUESTER, body(requestId, "6", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("stars must be between 1 and 5")));
    }

    @Test
    @DisplayName("A write-up over the cap is 400; one at the cap is accepted")
    void textCapIsEnforcedAtTheBoundary() throws Exception {
        String requestId = completedRequest("req-ctl-9");
        submit(REQUESTER, body(requestId, "5", "x".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("text must be at most 1000 characters")));

        submit(REQUESTER, body(requestId, "5", "x".repeat(1000)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A missing body is 400 problem+json")
    void missingBodyIsBadRequest() throws Exception {
        mockMvc.perform(post("/ratings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token(REQUESTER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request body is missing or malformed"));
    }

    @Test
    @DisplayName("/mine before rating: 200 with a null rating, so the UI shows the button")
    void mineIsNullUntilRated() throws Exception {
        String requestId = completedRequest("req-ctl-10");

        mine(COURIER, requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(nullValue()));
    }

    @Test
    @DisplayName("/mine after rating: the caller's own rating, not the other party's")
    void mineReturnsTheCallersOwnRating() throws Exception {
        String requestId = completedRequest("req-ctl-11");
        submit(REQUESTER, body(requestId, "2", "Late")).andExpect(status().isCreated());

        mine(REQUESTER, requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating.stars").value(2))
                .andExpect(jsonPath("$.rating.text").value("Late"))
                .andExpect(jsonPath("$.rating.raterId").value(REQUESTER));
        // the courier has not rated, whatever the requester did
        mine(COURIER, requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(nullValue()));
    }

    @Test
    @DisplayName("/mine mirrors the submit refusals: 404 unknown, 403 stranger, 400 no requestId")
    void mineIsGatedLikeSubmit() throws Exception {
        String requestId = completedRequest("req-ctl-12");

        mine(REQUESTER, "req-ctl-never-completed").andExpect(status().isNotFound());
        mine(STRANGER, requestId).andExpect(status().isForbidden());
        mockMvc.perform(get("/ratings/mine").header("Authorization", "Bearer " + token(REQUESTER)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("requestId is required"));
    }

    @Test
    @DisplayName("The advice maps the unique-key race's exception to the same 409 as the pre-check")
    void uniqueKeyRaceMapsToConflict() {
        // two submits racing past the pre-check end in a
        // DataIntegrityViolationException (RatingServiceTests proves the
        // constraint); the advice answers it like the pre-check's 409
        ProblemDetail problem = new ProblemDetailAdvice().handleDuplicateRating(
                new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("You have already rated this request");
    }
}
