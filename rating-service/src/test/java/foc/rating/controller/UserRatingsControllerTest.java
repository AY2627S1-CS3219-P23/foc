/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #253.
 * Scope: MockMvc tests of the two profile routes against H2, real
 * self-signed HS256 tokens (RatingControllerTest's harness): the empty
 * history, the average math, newest-first paging, the response's
 * deliberately narrow field set, the page/size 400s and the 401s. The
 * null-average and raw-double contracts are the author's decisions via
 * options Q&A.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), PR #273 review (@Sinnez1): the
 * int-overflow page is a 400, and the last addressable page still
 * answers.
 * Author review: [pending]
 */
package foc.rating.controller;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.rating.entity.RateableRequest;
import foc.rating.entity.Rating;
import foc.rating.repository.RateableRequestRepository;
import foc.rating.repository.RatingRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserRatingsControllerTest {

    // the test secret (src/test/resources/application.yaml)
    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    private static final String VIEWER = "usr-vwr-7007";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateableRequestRepository rateableRequests;

    @Autowired
    private RatingRepository ratings;

    private static String token(String userId) {
        return Jwts.builder()
                .subject(userId)
                .claim("role", "REQUESTER")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    // each test seeds its own ratee and requests: the cached context
    // shares one H2 database across test classes. The rateable_request
    // row comes first — the production schema's FK demands it.
    private void rate(String requestId, String raterId, String rateeId, int stars, String text,
            Instant createdAt) {
        rateableRequests.save(new RateableRequest(requestId, raterId, rateeId,
                Instant.parse("2026-09-19T09:30:00Z")));
        ratings.save(new Rating(requestId, raterId, rateeId, (short) stars, text, createdAt));
    }

    private ResultActions summary(String rateeId) throws Exception {
        return mockMvc.perform(get("/users/{id}/ratings/summary", rateeId)
                .header("Authorization", "Bearer " + token(VIEWER)));
    }

    private ResultActions reviews(String rateeId, String... params) throws Exception {
        var request = get("/users/{id}/ratings", rateeId)
                .header("Authorization", "Bearer " + token(VIEWER));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("A user with no ratings: null average (not 0), count 0, empty page")
    void emptyHistory() throws Exception {
        summary("usr-ratee-ur1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(nullValue()))
                .andExpect(jsonPath("$.count").value(0));

        reviews("usr-ratee-ur1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @DisplayName("The average is the raw mean of the stored stars, not rounded")
    void averageMath() throws Exception {
        Instant at = Instant.parse("2026-09-20T10:00:00Z");
        rate("req-ur2-a", "usr-rater-a", "usr-ratee-ur2", 5, "Great", at);
        rate("req-ur2-b", "usr-rater-b", "usr-ratee-ur2", 4, null, at);
        rate("req-ur2-c", "usr-rater-c", "usr-ratee-ur2", 4, null, at);

        summary("usr-ratee-ur2")
                .andExpect(status().isOk())
                // 13/3: the UI rounds for display, the API does not
                .andExpect(jsonPath("$.average").value(closeTo(13.0 / 3.0, 1e-9)))
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    @DisplayName("Reviews come newest first and page without overlap")
    void newestFirstAndPaged() throws Exception {
        rate("req-ur3-a", "usr-rater-a", "usr-ratee-ur3", 2, "oldest",
                Instant.parse("2026-09-20T10:00:00Z"));
        rate("req-ur3-b", "usr-rater-b", "usr-ratee-ur3", 3, "middle",
                Instant.parse("2026-09-21T10:00:00Z"));
        rate("req-ur3-c", "usr-rater-c", "usr-ratee-ur3", 4, "newest",
                Instant.parse("2026-09-22T10:00:00Z"));

        reviews("usr-ratee-ur3", "size", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].text").value("newest"))
                .andExpect(jsonPath("$.content[1].text").value("middle"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        reviews("usr-ratee-ur3", "size", "2", "page", "1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].text").value("oldest"))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    @DisplayName("A review carries only raterId, stars, text and createdAt")
    void reviewShape() throws Exception {
        Instant at = Instant.parse("2026-09-20T10:00:00Z");
        rate("req-ur4-a", "usr-rater-a", "usr-ratee-ur4", 5, "Fast and friendly", at);
        rate("req-ur4-b", "usr-rater-b", "usr-ratee-ur4", 3, null,
                Instant.parse("2026-09-21T10:00:00Z"));

        reviews("usr-ratee-ur4")
                .andExpect(status().isOk())
                // text is null when the rater left no write-up
                .andExpect(jsonPath("$.content[0].raterId").value("usr-rater-b"))
                .andExpect(jsonPath("$.content[0].text").value(nullValue()))
                .andExpect(jsonPath("$.content[1].raterId").value("usr-rater-a"))
                .andExpect(jsonPath("$.content[1].stars").value(5))
                .andExpect(jsonPath("$.content[1].text").value("Fast and friendly"))
                .andExpect(jsonPath("$.content[1].createdAt").isNotEmpty())
                // D32: ids only, and nothing identifies the request or
                // the rating itself
                .andExpect(jsonPath("$.content[1].rateeId").doesNotExist())
                .andExpect(jsonPath("$.content[1].requestId").doesNotExist())
                .andExpect(jsonPath("$.content[1].id").doesNotExist());
    }

    @Test
    @DisplayName("Another user's ratings stay out of the summary and the page")
    void otherRateesDoNotLeak() throws Exception {
        Instant at = Instant.parse("2026-09-20T10:00:00Z");
        rate("req-ur5-a", "usr-rater-a", "usr-ratee-ur5", 5, null, at);
        rate("req-ur5-b", "usr-rater-a", "usr-ratee-ur5-other", 1, null, at);

        summary("usr-ratee-ur5")
                .andExpect(jsonPath("$.average").value(closeTo(5.0, 1e-9)))
                .andExpect(jsonPath("$.count").value(1));
        reviews("usr-ratee-ur5")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].stars").value(5));
    }

    @Test
    @DisplayName("page and size outside the contract are 400 problem+json")
    void pageAndSizeBounds() throws Exception {
        reviews("usr-ratee-ur6", "page", "-1")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("page must be 0 or more"));
        reviews("usr-ratee-ur6", "size", "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"));
        reviews("usr-ratee-ur6", "size", "101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"));
    }

    @Test
    @DisplayName("A page whose offset overflows an int is 400, not spring-data's 500")
    void pageOffsetOverflow() throws Exception {
        // Integer.MAX_VALUE / 100 = 21474836: the last page whose
        // offset fits in an int at size 100
        reviews("usr-ratee-ur9", "page", String.valueOf(Integer.MAX_VALUE), "size", "100")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("page must be at most 21474836"));
        reviews("usr-ratee-ur9", "page", "21474837", "size", "100")
                .andExpect(status().isBadRequest());
        // the boundary itself still answers (empty, way past the data)
        reviews("usr-ratee-ur9", "page", "21474836", "size", "100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("A page that is not a number is 400 problem+json, not a 500")
    void pageTypeMismatch() throws Exception {
        reviews("usr-ratee-ur7", "page", "abc")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("page must be an integer"));
    }

    @Test
    @DisplayName("No token is 401 on both routes")
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/{id}/ratings/summary", "usr-ratee-ur8"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/{id}/ratings", "usr-ratee-ur8"))
                .andExpect(status().isUnauthorized());
    }
}
