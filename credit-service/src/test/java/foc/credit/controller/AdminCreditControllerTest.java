/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-17, issue #174 (admin dashboard
 * pool box).
 * Scope: the admin routes against a real Postgres, on
 * CreditControllerTest's harness: ADMIN and OWNER allowed, USER 403 and
 * no token 401, the summary's fields read from the database, the runs
 * newest month first with paging, and the paging 400s.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import foc.credit.PostgresTestContainer;
import foc.credit.repository.CreditAccountRepository;
import foc.credit.service.CreditOperations;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// @Transactional: the pool balance and the runs written here are made up
// and would not add up (CreditOperationsServiceTest's checks read the
// whole database), so nothing is committed. Runs use the years 9998 and
// 9999, later than any a test writes as a real month, so they are the
// newest whatever else the shared database holds.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCreditControllerTest extends PostgresTestContainer {

    // the test profile's secret (application-test.yaml)
    private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreditOperations operations;

    @Autowired
    private CreditAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = { "ADMIN", "OWNER" })
    void adminsAndOwnersMayReadBothRoutes(String role) throws Exception {
        mockMvc.perform(get("/credits/admin/pool").header("Authorization", bearer(role)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/credits/admin/redistributions").header("Authorization", bearer(role)))
            .andExpect(status().isOk());
    }

    @Test
    void aUserIsForbidden() throws Exception {
        mockMvc.perform(get("/credits/admin/pool").header("Authorization", bearer("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/credits/admin/redistributions").header("Authorization", bearer("USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void noTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/credits/admin/pool")).andExpect(status().isUnauthorized());
    }

    @Test
    void theSummaryReadsThePoolTheAccountsAndTheLatestRun() throws Exception {
        // at least two accounts, so 2 * count + 1 shares out as 2 each and 1 over
        operations.getOrCreate("u-" + UUID.randomUUID());
        operations.getOrCreate("u-" + UUID.randomUUID());
        long count = accounts.count();
        jdbc.update("update common_pool set balance = ?", 2 * count + 1);
        run("9998-12", 4, 0, "2026-09-30T16:00:00Z");
        run("9999-01", 7, 1, "2026-10-31T16:00:00Z");

        mockMvc.perform(get("/credits/admin/pool").header("Authorization", bearer("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.poolBalance").value(2 * count + 1))
            .andExpect(jsonPath("$.accounts").value(count))
            .andExpect(jsonPath("$.share").value(2))
            .andExpect(jsonPath("$.remainder").value(1))
            .andExpect(jsonPath("$.lastRun.period").value("9999-01"))
            .andExpect(jsonPath("$.lastRun.distributed").value(7))
            .andExpect(jsonPath("$.lastRun.remainder").value(1))
            .andExpect(jsonPath("$.lastRun.ranAt").value("2026-10-31T16:00:00Z"))
            .andExpect(jsonPath("$.nextRunAt").isString());
    }

    @Test
    void runsComeNewestMonthFirstAndPage() throws Exception {
        run("9998-12", 4, 0, "2026-09-30T16:00:00Z");
        run("9999-01", 7, 1, "2026-10-31T16:00:00Z");
        run("9999-02", 9, 2, "2026-11-30T16:00:00Z");

        mockMvc.perform(get("/credits/admin/redistributions").param("size", "2")
                .header("Authorization", bearer("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].period").value(contains("9999-02", "9999-01")))
            .andExpect(jsonPath("$.content[0].distributed").value(9))
            .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void aPagePastTheLastIsEmpty() throws Exception {
        mockMvc.perform(get("/credits/admin/redistributions").param("page", "100000")
                .header("Authorization", bearer("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void aNegativePageIsRefused() throws Exception {
        mockMvc.perform(get("/credits/admin/redistributions").param("page", "-1")
                .header("Authorization", bearer("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("page must be 0 or more"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "101" })
    void aSizeOutsideOneToAHundredIsRefused(String size) throws Exception {
        mockMvc.perform(get("/credits/admin/redistributions").param("size", size)
                .header("Authorization", bearer("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"));
    }

    private void run(String period, int distributed, int remainder, String ranAt) {
        jdbc.update("insert into redistribution_run (period, distributed, remainder, ran_at) values (?, ?, ?, ?)",
            period, distributed, remainder, java.sql.Timestamp.from(Instant.parse(ranAt)));
    }

    private static String bearer(String role) {
        return "Bearer " + Jwts.builder()
            .subject("u-admin-" + UUID.randomUUID())
            .claim("role", role)
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
            .compact();
    }
}
