/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #254.
 * Scope: MockMvc tests of the report routes against H2, real self-signed
 * HS256 tokens (RatingControllerTest's harness): the 201 and its shape,
 * the self-report and body 400s, the role gate's 401/403/200, and
 * newest-first paging. Future-dated seeds keep the ordering assertions
 * clear of rows other tests leave in the shared H2 database — the
 * author's decision.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), PR #274 review (claude[bot]): the
 * padded self-id case — trimming must keep it a 400 and store trimmed
 * ids.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), main merge: the page-offset
 * overflow 400 (PR #273's review case, applied to this list).
 * Author review: [pending]
 */
package foc.rating.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.rating.entity.ReportReason;
import foc.rating.entity.UserReport;
import foc.rating.repository.UserReportRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTest {

    // the test secret (src/test/resources/application.yaml)
    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    private static final String REPORTER = "usr-rep-5005";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserReportRepository reports;

    private static String token(String userId, String role) {
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private ResultActions file(String reporterId, String body) throws Exception {
        return mockMvc.perform(post("/reports")
                .header("Authorization", "Bearer " + token(reporterId, "REQUESTER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions list(String role, String... params) throws Exception {
        var request = get("/reports").header("Authorization", "Bearer " + token("usr-adm-1", role));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("A signed-in user files a report and gets the stored row back")
    void fileHappyPath() throws Exception {
        file(REPORTER, """
                {"reportedUserId": "usr-bad-r1", "reason": "NO_SHOW",
                 "details": "Never arrived at the pickup point", "requestId": "req-r1"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.reportedUserId").value("usr-bad-r1"))
                // the reporter is the JWT subject, never a body field
                .andExpect(jsonPath("$.reporterId").value(REPORTER))
                .andExpect(jsonPath("$.requestId").value("req-r1"))
                .andExpect(jsonPath("$.reason").value("NO_SHOW"))
                .andExpect(jsonPath("$.details").value("Never arrived at the pickup point"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("Blank details and requestId are stored as null, not empty strings")
    void blankOptionalsBecomeNull() throws Exception {
        file(REPORTER, """
                {"reportedUserId": "usr-bad-r2", "reason": "OTHER", "details": "   ", "requestId": ""}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.details").value(nullValue()))
                .andExpect(jsonPath("$.requestId").value(nullValue()));
    }

    @Test
    @DisplayName("Reporting oneself is 400 problem+json")
    void selfReport() throws Exception {
        file(REPORTER, "{\"reportedUserId\": \"" + REPORTER + "\", \"reason\": \"OTHER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("You cannot report yourself"));
    }

    @Test
    @DisplayName("A padded self-id is still a self-report, and stored ids come back trimmed")
    void paddedSelfReport() throws Exception {
        // @NotBlank admits padding; it must not dodge the self-check
        file(REPORTER, "{\"reportedUserId\": \" " + REPORTER + " \", \"reason\": \"OTHER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You cannot report yourself"));

        // ...nor store an id that matches no user
        file(REPORTER, "{\"reportedUserId\": \" usr-bad-r9 \", \"reason\": \"OTHER\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportedUserId").value("usr-bad-r9"));
    }

    @Test
    @DisplayName("A reason outside the fixed list is 400 problem+json")
    void unknownReason() throws Exception {
        // Jackson refuses the value before validation runs, so the
        // unreadable-body message answers
        file(REPORTER, "{\"reportedUserId\": \"usr-bad-r3\", \"reason\": \"RUDE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Request body is missing or malformed"));
    }

    @Test
    @DisplayName("Missing reportedUserId and reason are refused together, sorted by field")
    void missingRequiredFields() throws Exception {
        file(REPORTER, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("reason is required. reportedUserId is required."));
    }

    @Test
    @DisplayName("Details beyond the 1000-character cap are 400 problem+json")
    void detailsTooLong() throws Exception {
        file(REPORTER, "{\"reportedUserId\": \"usr-bad-r4\", \"reason\": \"OTHER\", \"details\": \""
                + "x".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("details must be at most 1000 characters."));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ADMIN", "OWNER" })
    @DisplayName("Admins and the owner may read the report list")
    void adminsMayList(String role) throws Exception {
        list(role)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("A non-admin reading the list is 403; no token is 401 on both routes")
    void roleGate() throws Exception {
        list("USER").andExpect(status().isForbidden());
        list("REQUESTER").andExpect(status().isForbidden());
        mockMvc.perform(get("/reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportedUserId\": \"usr-bad-r5\", \"reason\": \"OTHER\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Reports come newest first and page without overlap")
    void newestFirstAndPaged() throws Exception {
        // future-dated, so these three outsort every row other tests
        // put in the shared H2 database
        reports.save(new UserReport("usr-bad-r6", "usr-rep-a", null, ReportReason.NO_SHOW,
                "oldest", Instant.parse("2027-01-01T10:00:00Z")));
        reports.save(new UserReport("usr-bad-r6", "usr-rep-b", null, ReportReason.UNSAFE_HANDLING,
                "middle", Instant.parse("2027-01-02T10:00:00Z")));
        reports.save(new UserReport("usr-bad-r6", "usr-rep-c", null, ReportReason.ABUSIVE_BEHAVIOUR,
                "newest", Instant.parse("2027-01-03T10:00:00Z")));

        list("ADMIN", "size", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].details").value("newest"))
                .andExpect(jsonPath("$.content[1].details").value("middle"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2));

        list("ADMIN", "size", "2", "page", "1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].details").value("oldest"))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    @DisplayName("page and size outside the contract are 400 problem+json")
    void pageAndSizeBounds() throws Exception {
        list("ADMIN", "page", "-1")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("page must be 0 or more"));
        list("ADMIN", "size", "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"));
        list("ADMIN", "size", "101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"));
        // past Integer.MAX_VALUE / size the offset overflows: the
        // contract's 400, not spring-data's 500 (PR #273 review)
        list("ADMIN", "size", "20", "page", String.valueOf(Integer.MAX_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("page must be at most " + (Integer.MAX_VALUE / 20)));
    }
}
