/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13, issue #172.
 * Scope: the read API through the security chain against a real
 * Postgres, row by row of docs/credit-service.md "Read API contract":
 * the balance, the history page, its order, filters and paging, each
 * row of "Errors", and both instant formats.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import foc.credit.PostgresTestContainer;
import foc.credit.entity.CreditHistoryEntry;
import foc.credit.entity.CreditHistoryType;
import foc.credit.repository.CreditAccountRepository;
import foc.credit.repository.CreditHistoryEntryRepository;
import foc.credit.service.CreditOperations;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// @Transactional: the history rows written here are made up for the
// filters and would not add up to a balance, so nothing is committed
// (CreditOperationsServiceTest's checks read the whole database). The
// request runs on the test's thread and joins its transaction.
//
// That would also hide a read that cannot write outside a test: a
// read-only transaction of its own would join this one and pass. So the
// two first-read cases marked NOT_SUPPORTED run with no test
// transaction and commit, as a real first request does. What they
// commit is a provisioned account, which keeps every invariant.
//
// Every caller's first read creates the account with its sign-up
// credits, so an unfiltered history starts with a PROVISION row of 5
// dated now, newer than the 2026-10-10 rows the tests add.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CreditControllerTest extends PostgresTestContainer {

    // the test profile's secret (application-test.yaml)
    private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";
    private static final Instant DAY = Instant.parse("2026-10-10T08:00:00Z");

    private final String caller = "u-" + UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreditOperations operations;

    @Autowired
    private CreditAccountRepository accounts;

    @Autowired
    private CreditHistoryEntryRepository history;

    // ---- GET /credits/me

    @Test
    void aFirstReadCreatesTheAccountWithItsSignUpCredits() throws Exception {
        balance()
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.available").value(5))
            .andExpect(jsonPath("$.reserved").value(0))
            .andExpect(jsonPath("$.total").value(5));
    }

    @Test
    void aSecondReadGrantsNothingMore() throws Exception {
        balance().andExpect(status().isOk());

        balance()
            .andExpect(jsonPath("$.available").value(5))
            .andExpect(jsonPath("$.total").value(5));
    }

    @Test
    void theBalanceShowsHeldCreditsAndTheTotalIsTheirSum() throws Exception {
        operations.reserve("r-" + UUID.randomUUID(), caller, 3);

        balance()
            .andExpect(jsonPath("$.available").value(2))
            .andExpect(jsonPath("$.reserved").value(3))
            .andExpect(jsonPath("$.total").value(5));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aFirstBalanceReadCommitsTheAccountOutsideAnyTestTransaction() throws Exception {
        balance()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(5))
            .andExpect(jsonPath("$.reserved").value(0))
            .andExpect(jsonPath("$.total").value(5));

        assertThat(accounts.existsById(caller)).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aFirstHistoryReadCommitsTheAccountOutsideAnyTestTransaction() throws Exception {
        history()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].type", contains("PROVISION")))
            .andExpect(jsonPath("$.totalElements").value(1));

        assertThat(accounts.existsById(caller)).isTrue();
    }

    // ---- GET /credits/me/history

    @Test
    void aFirstReadOfTheHistoryCreatesTheAccountAndShowsItsProvision() throws Exception {
        history()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].type").value("PROVISION"))
            .andExpect(jsonPath("$.content[0].amount").value(5))
            .andExpect(jsonPath("$.content[0].requestRef").value(nullValue()))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1));

        balance().andExpect(jsonPath("$.available").value(5));
    }

    @Test
    void aRowCarriesTheContractsFields() throws Exception {
        long id = row(CreditHistoryType.RESERVE, 3, "r-1", DAY);

        history("maxAmount", "3")
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].length()").value(5))
            .andExpect(jsonPath("$.content[0].id").value(id))
            .andExpect(jsonPath("$.content[0].type").value("RESERVE"))
            .andExpect(jsonPath("$.content[0].amount").value(3))
            .andExpect(jsonPath("$.content[0].requestRef").value("r-1"))
            .andExpect(jsonPath("$.content[0].occurredAt").value("2026-10-10T08:00:00Z"));
    }

    @Test
    void rowsComeNewestFirstAndTheIdSettlesRowsOfOneInstant() throws Exception {
        row(CreditHistoryType.RESERVE, 1, "r-1", DAY);
        row(CreditHistoryType.RELEASE, 2, "r-1", DAY.plusSeconds(60));
        // the same instant as the row before: the later ID comes first
        row(CreditHistoryType.RESERVE, 3, "r-2", DAY.plusSeconds(60));

        history().andExpect(jsonPath("$.content[*].amount", contains(5, 3, 2, 1)));
    }

    @Test
    void aSortParameterIsIgnored() throws Exception {
        row(CreditHistoryType.RESERVE, 1, "r-1", DAY);
        row(CreditHistoryType.RESERVE, 2, "r-2", DAY.plusSeconds(60));

        history("sort", "amount,asc").andExpect(jsonPath("$.content[*].amount", contains(5, 2, 1)));
    }

    @Test
    void anotherUsersRowsAreNeverShown() throws Exception {
        String other = "u-" + UUID.randomUUID();
        operations.getOrCreate(other);
        history.save(new CreditHistoryEntry(other, CreditHistoryType.TRANSFER_IN, 9, "r-9", DAY));

        history().andExpect(jsonPath("$.content[*].amount", contains(5)));
    }

    @Test
    void minAmountAndMaxAmountAreInclusive() throws Exception {
        rowsOfAmounts(1, 2, 3, 4);

        history("minAmount", "3").andExpect(jsonPath("$.content[*].amount", contains(5, 4, 3)));
        history("maxAmount", "2").andExpect(jsonPath("$.content[*].amount", contains(2, 1)));
        history("minAmount", "2", "maxAmount", "3").andExpect(jsonPath("$.content[*].amount", contains(3, 2)));
    }

    @Test
    void anExactAmountIsMinAmountEqualToMaxAmount() throws Exception {
        rowsOfAmounts(1, 2, 3);

        history("minAmount", "2", "maxAmount", "2").andExpect(jsonPath("$.content[*].amount", contains(2)));
    }

    @Test
    void fromIsInclusiveAndToIsExclusive() throws Exception {
        row(CreditHistoryType.RESERVE, 1, "r-1", DAY.minusSeconds(1));
        row(CreditHistoryType.RESERVE, 2, "r-2", DAY);
        row(CreditHistoryType.RESERVE, 3, "r-3", DAY.plusSeconds(3600));

        history("from", "2026-10-10T08:00:00Z", "to", "2026-10-10T09:00:00Z")
            .andExpect(jsonPath("$.content[*].amount", contains(2)));
        history("from", "2026-10-10T08:00:00Z", "to", "2026-10-10T09:00:01Z")
            .andExpect(jsonPath("$.content[*].amount", contains(3, 2)));
        // to alone: everything before it, which leaves out today's provision
        history("to", "2026-10-10T08:00:00Z").andExpect(jsonPath("$.content[*].amount", contains(1)));
    }

    @Test
    void anOffsetInstantIsTheSameRangeAsItsUtcForm() throws Exception {
        row(CreditHistoryType.RESERVE, 1, "r-1", DAY.minusSeconds(1));
        row(CreditHistoryType.RESERVE, 2, "r-2", DAY);

        history("from", "2026-10-10T08:00:00Z", "to", "2026-10-11T08:00:00Z")
            .andExpect(jsonPath("$.content[*].amount", contains(2)));
        // 16:00 at +08:00 is 08:00 UTC
        history("from", "2026-10-10T16:00:00+08:00", "to", "2026-10-11T16:00:00+08:00")
            .andExpect(jsonPath("$.content[*].amount", contains(2)));
    }

    @Test
    void theAmountAndDateFiltersCombine() throws Exception {
        row(CreditHistoryType.RESERVE, 4, "r-1", DAY.minusSeconds(1));
        row(CreditHistoryType.RESERVE, 4, "r-2", DAY);
        row(CreditHistoryType.RESERVE, 1, "r-3", DAY);

        history("minAmount", "2", "from", "2026-10-10T08:00:00Z", "to", "2026-10-10T09:00:00Z")
            .andExpect(jsonPath("$.content[*].requestRef", contains("r-2")));
    }

    @Test
    void filtersThatMatchNothingGiveAnEmptyPage() throws Exception {
        history("minAmount", "100")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", empty()))
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void theHistoryIsPagedBySizeAndPage() throws Exception {
        rowsOfAmounts(1, 2, 3, 4);

        history("size", "2")
            .andExpect(jsonPath("$.content[*].amount", contains(5, 4)))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3));
        history("size", "2", "page", "1").andExpect(jsonPath("$.content[*].amount", contains(3, 2)));
        history("size", "2", "page", "2").andExpect(jsonPath("$.content[*].amount", contains(1)));
    }

    @Test
    void aPagePastTheLastIsAnEmptyPageNotAnError() throws Exception {
        rowsOfAmounts(1, 2);

        history("size", "2", "page", "7")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", empty()))
            .andExpect(jsonPath("$.page").value(7))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    @ParameterizedTest
    @CsvSource({ "1", "100" })
    void theSmallestAndLargestSizeAreAccepted(String size) throws Exception {
        history("size", size)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(Integer.parseInt(size)));
    }

    // ---- "Errors": each is a 400 problem+json that names the parameter

    @ParameterizedTest
    @CsvSource({
        "minAmount, abc,        minAmount must be an integer",
        "minAmount, 1.5,        minAmount must be an integer",
        "minAmount, 0,          minAmount must be 1 or more",
        "minAmount, -3,         minAmount must be 1 or more",
        "maxAmount, abc,        maxAmount must be an integer",
        "maxAmount, 2.0,        maxAmount must be an integer",
        "maxAmount, 0,          maxAmount must be 1 or more",
        "page,      -1,         page must be 0 or more",
        "page,      abc,        page must be an integer",
        "size,      0,          size must be between 1 and 100",
        "size,      101,        size must be between 1 and 100",
        "size,      abc,        size must be an integer",
    })
    void anInvalidNumberIsRefused(String name, String value, String detail) throws Exception {
        assertBadRequest(history(name, value), detail);
    }

    @ParameterizedTest
    @CsvSource({
        // a date alone: the server would have to pick a time zone
        "2026-10-10",
        // no offset
        "2026-10-10T08:00:00",
        // an unencoded + in a query string arrives as a space
        "2026-10-10T16:00:00 08:00",
        "yesterday",
    })
    void anInstantThatIsNotFullWithAnOffsetIsRefused(String value) throws Exception {
        String expected = " must be a full ISO-8601 instant with an offset, such as 2026-10-10T08:00:00Z";
        assertBadRequest(history("from", value), "from" + expected);
        assertBadRequest(history("to", value), "to" + expected);
    }

    @Test
    void minAmountAboveMaxAmountIsRefused() throws Exception {
        assertBadRequest(history("minAmount", "4", "maxAmount", "3"), "minAmount must not be above maxAmount");
    }

    @ParameterizedTest
    @CsvSource({
        // equal: to is exclusive, so the range would be empty
        "2026-10-10T08:00:00Z, 2026-10-10T08:00:00Z",
        "2026-10-10T08:00:00Z, 2026-10-10T16:00:00+08:00",
        "2026-10-11T08:00:00Z, 2026-10-10T08:00:00Z",
    })
    void fromNotBeforeToIsRefused(String from, String to) throws Exception {
        assertBadRequest(history("from", from, "to", to), "from must be before to");
    }

    @Test
    void aRefusedRequestCreatesNoAccount() throws Exception {
        assertBadRequest(history("size", "0"), "size must be between 1 and 100");

        assertThat(accounts.existsById(caller)).isFalse();
    }

    @Test
    void theHistoryNeedsAToken() throws Exception {
        mockMvc.perform(get("/credits/me/history"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---- helpers

    private ResultActions balance() throws Exception {
        return mockMvc.perform(get("/credits/me").header("Authorization", "Bearer " + token(caller)));
    }

    // name, value, name, value, ...
    private ResultActions history(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/credits/me/history")
            .header("Authorization", "Bearer " + token(caller));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private static void assertBadRequest(ResultActions result, String detail) throws Exception {
        result
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value(detail));
    }

    // one row each, a minute apart from DAY on, so a larger amount is newer
    private void rowsOfAmounts(int... amounts) {
        for (int amount : amounts) {
            row(CreditHistoryType.RESERVE, amount, "r-" + amount, DAY.plusSeconds(60L * amount));
        }
    }

    // the account first: a history row references it
    private long row(CreditHistoryType type, int amount, String requestRef, Instant occurredAt) {
        operations.getOrCreate(caller);
        return history.save(new CreditHistoryEntry(caller, type, amount, requestRef, occurredAt)).getId();
    }

    private static String token(String userId) {
        return Jwts.builder()
            .subject(userId)
            .claim("role", "USER")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
            .compact();
    }
}
