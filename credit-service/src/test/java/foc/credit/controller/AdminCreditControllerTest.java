/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-17, issue #174 (admin dashboard
 * pool box).
 * Scope: the admin routes against a real Postgres, on
 * CreditControllerTest's harness: ADMIN and OWNER allowed, USER 403 and
 * no token 401, the summary's fields read from the database, the runs
 * newest month first with paging, and the paging 400s.
 * 2026-10-25, Claude Code (Opus 5.5), issue #236: the long-held
 * reservations route: HELD only, oldest first, the cap, the cutoff at
 * its boundary, PT0S, and the olderThan 400s.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
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
import foc.credit.entity.Reserved;
import foc.credit.repository.CreditAccountRepository;
import foc.credit.service.CreditOperations;
import foc.credit.service.ReservationQueryService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// @Transactional: the pool balance and the runs written here are made up
// and would not add up (CreditOperationsServiceTest's checks read the
// whole database), so nothing is committed. Runs use the years 9998 and
// 9999, later than any a test writes as a real month, so they are the
// newest whatever else the shared database holds. Reservations are
// dated 1980 and 1990, before any a test makes now, so they are the
// oldest; the cutoff cases pass a 1990 "now" for the same reason.
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
    private ReservationQueryService reservations;

    @Autowired
    private JdbcTemplate jdbc;

    private final String requester = "u-req-" + UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(strings = { "ADMIN", "OWNER" })
    void adminsAndOwnersMayReadEveryRoute(String role) throws Exception {
        mockMvc.perform(get("/credits/admin/pool").header("Authorization", bearer(role)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/credits/admin/redistributions").header("Authorization", bearer(role)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/credits/admin/reservations/held").header("Authorization", bearer(role)))
            .andExpect(status().isOk());
    }

    @Test
    void aUserIsForbidden() throws Exception {
        mockMvc.perform(get("/credits/admin/pool").header("Authorization", bearer("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/credits/admin/redistributions").header("Authorization", bearer("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/credits/admin/reservations/held").header("Authorization", bearer("USER")))
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

    @Test
    void heldReservationsComeOldestFirstWithTheirFields() throws Exception {
        String a = reservation("1990-01-01T00:00:00Z", "HELD", 1);
        String b = reservation("1990-01-01T00:00:00Z", "HELD", 2);
        String c = reservation("1990-01-02T00:00:00Z", "HELD", 3);
        String released = reservation("1989-12-31T00:00:00Z", "RELEASED", 4);
        // the same instant is settled by the request ID
        String first = a.compareTo(b) < 0 ? a : b;
        String second = a.compareTo(b) < 0 ? b : a;

        mockMvc.perform(get("/credits/admin/reservations/held").header("Authorization", bearer("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].requestId").value(first))
            .andExpect(jsonPath("$.items[1].requestId").value(second))
            .andExpect(jsonPath("$.items[2].requestId").value(c))
            .andExpect(jsonPath("$.items[2].requesterId").value(requester))
            .andExpect(jsonPath("$.items[2].amount").value(3))
            .andExpect(jsonPath("$.items[2].heldSince").value("1990-01-02T00:00:00Z"))
            .andExpect(jsonPath("$.items[?(@.requestId == '" + released + "')]").isEmpty());
    }

    @Test
    void aReservationHeldUnderAnHourIsLeftOutByDefault() throws Exception {
        String recent = reservation(Instant.now().toString(), "HELD", 1);

        mockMvc.perform(get("/credits/admin/reservations/held").header("Authorization", bearer("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.requestId == '" + recent + "')]").isEmpty());
    }

    @Test
    void heldReservationsAreCappedAtAHundred() throws Exception {
        for (int i = 0; i < ReservationQueryService.MAX_HELD + 1; i++) {
            reservation("1980-01-01T00:00:00Z", "HELD", 1);
        }

        mockMvc.perform(get("/credits/admin/reservations/held").header("Authorization", bearer("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(ReservationQueryService.MAX_HELD));
    }

    @Test
    void theCutoffIncludesAReservationMadeExactlyThen() {
        String atCutoff = reservation("1990-01-01T00:00:00Z", "HELD", 1);
        String after = reservation("1990-01-01T00:00:01Z", "HELD", 1);
        Instant now = Instant.parse("1990-01-01T01:00:00Z");

        assertThat(idsOf(reservations.heldLongerThan(Duration.ofHours(1), now)))
            .contains(atCutoff).doesNotContain(after);
    }

    @Test
    void zeroListsEveryReservationHeldSoFar() {
        String earlier = reservation("1990-01-01T00:59:59Z", "HELD", 1);
        String atNow = reservation("1990-01-01T01:00:00Z", "HELD", 1);
        String later = reservation("1990-01-01T01:00:01Z", "HELD", 1);
        Instant now = Instant.parse("1990-01-01T01:00:00Z");

        assertThat(idsOf(reservations.heldLongerThan(Duration.ZERO, now)))
            .contains(earlier, atNow).doesNotContain(later);
    }

    @Test
    void zeroIsAccepted() throws Exception {
        mockMvc.perform(get("/credits/admin/reservations/held").param("olderThan", "PT0S")
                .header("Authorization", bearer("ADMIN")))
            .andExpect(status().isOk());
    }

    // a cutoff before the epoch could leave Instant's or PostgreSQL's range
    @Test
    void aDurationReachingPastTheEpochListsNothing() throws Exception {
        reservation("1990-01-01T00:00:00Z", "HELD", 1);

        mockMvc.perform(get("/credits/admin/reservations/held").param("olderThan", "P100000000D")
                .header("Authorization", bearer("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void aNegativeOlderThanIsRefused() throws Exception {
        mockMvc.perform(get("/credits/admin/reservations/held").param("olderThan", "-PT1H")
                .header("Authorization", bearer("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("olderThan must not be negative"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1h", "PT", "60" })
    void anOlderThanThatIsNotADurationIsRefused(String olderThan) throws Exception {
        mockMvc.perform(get("/credits/admin/reservations/held").param("olderThan", olderThan)
                .header("Authorization", bearer("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("olderThan must be an ISO-8601 duration such as PT1H"));
    }

    // a reservation of this test's requester, made at createdAt
    private String reservation(String createdAt, String status, int amount) {
        operations.getOrCreate(requester);
        String ref = "r-" + UUID.randomUUID();
        jdbc.update("insert into credit_reservation (request_ref, requester_id, amount, status, created_at)"
                + " values (?, ?, ?, ?, ?)",
            ref, requester, amount, status, Timestamp.from(Instant.parse(createdAt)));
        return ref;
    }

    private static List<String> idsOf(List<Reserved> held) {
        return held.stream().map(Reserved::getRequestRef).toList();
    }

    private void run(String period, int distributed, int remainder, String ranAt) {
        jdbc.update("insert into redistribution_run (period, distributed, remainder, ran_at) values (?, ?, ?, ?)",
            period, distributed, remainder, Timestamp.from(Instant.parse(ranAt)));
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
