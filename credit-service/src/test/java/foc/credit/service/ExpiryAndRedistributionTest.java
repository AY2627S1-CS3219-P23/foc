/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-16, issue #173.
 * Scope: the expiry and redistribution services against a real
 * Postgres: expired lots moved to the pool, held credits untouched and
 * expired after their release, the pool shared out with the remainder
 * one each, a pool smaller than the accounts (a share of 0 writes
 * nothing), an empty month recorded, a second run for a month changing
 * nothing (repeated and concurrent), and after each test the checks of
 * docs/credit-service.md "How their consistency is maintained", copied
 * from CreditOperationsServiceTest.
 * PR #193 review (Leong Wei Zhi), same day: the small-pool test makes
 * its own accounts instead of relying on what earlier tests left.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import foc.credit.PostgresTestContainer;
import foc.credit.service.RedistributionService.RedistributionOutcome;

// Not @Transactional: each run commits, as it does on the schedule.
// Redistribution shares the pool out to every account in the database,
// other test classes' included, so the assertions are about changes.
@SpringBootTest
class ExpiryAndRedistributionTest extends PostgresTestContainer {

    // periods no real run uses, one per redistribution in this class
    private static final AtomicInteger NEXT_YEAR = new AtomicInteger(3000);

    private final String courier = "u-cou-" + UUID.randomUUID();
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private CreditOperations operations;

    @Autowired
    private LotExpiryService expiry;

    @Autowired
    private RedistributionService redistribution;

    @Autowired
    private JdbcTemplate jdbc;

    // "How their consistency is maintained": each check must return no rows
    @AfterEach
    void theBooksBalance() {
        assertThat(jdbc.queryForList("""
            select r.request_ref from credit_reservation r
            left join credit_reservation_slice s on s.request_ref = r.request_ref
            where r.status = 'HELD'
            group by r.request_ref, r.amount
            having r.amount <> coalesce(sum(s.amount), 0)
            """)).as("HELD reservations whose amount differs from their slices").isEmpty();
        assertThat(jdbc.queryForList("""
            select a.user_id from credit_account a
            left join credit_reservation r on r.requester_id = a.user_id and r.status = 'HELD'
            group by a.user_id, a.reserved
            having a.reserved <> coalesce(sum(r.amount), 0)
            """)).as("accounts whose reserved differs from their HELD reservations").isEmpty();
        assertThat(jdbc.queryForList("""
            select a.user_id from credit_account a
            left join credit_lot l on l.owner_id = a.user_id
            group by a.user_id, a.available
            having a.available <> coalesce(sum(l.remaining), 0)
            """)).as("accounts whose available differs from their lots").isEmpty();
        assertThat(jdbc.queryForList("""
            select a.user_id from credit_account a
            left join credit_history h on h.user_id = a.user_id
            group by a.user_id, a.available, a.reserved
            having a.available <> coalesce(sum(case
                    when h.type in ('PROVISION', 'RELEASE', 'TRANSFER_IN', 'REDISTRIBUTION') then h.amount
                    when h.type in ('RESERVE', 'EXPIRY') then -h.amount
                    else 0 end), 0)
                or a.reserved <> coalesce(sum(case
                    when h.type = 'RESERVE' then h.amount
                    when h.type in ('RELEASE', 'TRANSFER_OUT') then -h.amount
                    else 0 end), 0)
            """)).as("accounts whose balances differ from their history").isEmpty();
        assertThat(jdbc.queryForObject("""
            select (select coalesce(sum(available + reserved), 0) from credit_account)
                 + (select balance from common_pool)
                 - (select coalesce(sum(amount), 0) from credit_history where type = 'PROVISION')
            """, Long.class)).as("credits in existence minus credits provisioned").isZero();
    }

    // ---- expiry (F6.1) ----

    @Test
    void expiredEarnedCreditsMoveToThePool() {
        earn(courier, 3);
        expireEarnedLotsOf(courier);
        int poolBefore = poolBalance();

        assertThat(expiry.ownersWithExpiredLots(now)).contains(courier);
        assertThat(expiry.expireLotsOf(courier, now)).isEqualTo(3);

        assertThat(available(courier)).isEqualTo(5);
        assertThat(lotsOf(courier)).containsExactly("5 never", "0 expiring");
        assertThat(historyOf(courier)).containsExactly("PROVISION 5", "TRANSFER_IN 3", "EXPIRY 3");
        assertThat(poolBalance()).isEqualTo(poolBefore + 3);
        assertThat(expiry.ownersWithExpiredLots(now)).doesNotContain(courier);
    }

    @Test
    void lotsNotYetExpiredAndTheSignUpLotStay() {
        earn(courier, 3);

        assertThat(expiry.ownersWithExpiredLots(now)).doesNotContain(courier);
        assertThat(expiry.expireLotsOf(courier, now)).isZero();

        assertThat(available(courier)).isEqualTo(8);
        assertThat(historyOf(courier)).containsExactly("PROVISION 5", "TRANSFER_IN 3");
    }

    @Test
    void aSecondRunOnTheSameLotsMovesNothing() {
        earn(courier, 3);
        expireEarnedLotsOf(courier);
        expiry.expireLotsOf(courier, now);
        int poolAfterFirst = poolBalance();

        assertThat(expiry.expireLotsOf(courier, now)).isZero();

        assertThat(poolBalance()).isEqualTo(poolAfterFirst);
        assertThat(historyOf(courier)).containsExactly("PROVISION 5", "TRANSFER_IN 3", "EXPIRY 3");
    }

    @Test
    void heldCreditsAreUntouchedAndExpireOnceReleased() {
        earn(courier, 3);
        // takes the 3 expiring credits first, then 1 of the sign-up lot
        String spending = "r-" + UUID.randomUUID();
        operations.reserve(spending, courier, 4);
        expireEarnedLotsOf(courier);

        assertThat(expiry.expireLotsOf(courier, now)).isZero();
        assertThat(jdbc.queryForObject("select reserved from credit_account where user_id = ?",
            Integer.class, courier)).isEqualTo(4);

        operations.release(spending, courier);
        assertThat(available(courier)).isEqualTo(8);

        assertThat(expiry.expireLotsOf(courier, now)).isEqualTo(3);
        assertThat(available(courier)).isEqualTo(5);
        assertThat(lotsOf(courier)).containsExactly("5 never", "0 expiring");
    }

    // ---- redistribution (F6.1.1, F6.1.2) ----

    @Test
    void thePoolIsSharedEquallyWithTheRemainderOneEach() {
        fillPool();
        Map<String, Integer> before = availableByUser();
        int pool = poolBalance();
        int count = before.size();
        YearMonth period = newPeriod();

        RedistributionOutcome outcome = redistribution.redistribute(period, now);

        int share = pool / count;
        int remainder = pool % count;
        assertThat(outcome).isEqualTo(
            new RedistributionOutcome(period.toString(), false, count, pool, share, remainder));
        Map<String, Integer> after = availableByUser();
        Map<String, Integer> received = new HashMap<>();
        before.forEach((user, available) -> received.put(user, after.get(user) - available));
        assertThat(received.values()).allMatch(amount -> amount == share || amount == share + 1);
        assertThat(received.values().stream().filter(amount -> amount == share + 1)).hasSize(remainder);
        assertThat(poolBalance()).isZero();
        assertThat(jdbc.queryForMap("select distributed, remainder from redistribution_run where period = ?",
            period.toString())).containsEntry("distributed", pool).containsEntry("remainder", remainder);

        // a share is a new lot, its expiry started the day it is received (D4)
        String someone = received.entrySet().stream()
            .filter(entry -> entry.getValue() > 0).findAny().orElseThrow().getKey();
        assertThat(jdbc.queryForMap("""
            select remaining, expires_at from credit_lot where owner_id = ? and earned_at = ?
            """, someone, java.sql.Timestamp.from(now)))
            .containsEntry("remaining", received.get(someone))
            .containsEntry("expires_at",
                java.sql.Timestamp.from(now.atOffset(ZoneOffset.UTC).plusMonths(3).toInstant()));
        assertThat(jdbc.queryForObject("""
            select type || ' ' || amount from credit_history where user_id = ? order by id desc limit 1
            """, String.class, someone)).isEqualTo("REDISTRIBUTION " + received.get(someone));
    }

    @Test
    void aPoolSmallerThanTheAccountsGivesOneEachAndSkipsTheRest() {
        // drains whatever earlier tests left, then 3 credits for many more accounts;
        // fillPool makes 2 accounts, and these 4 more hold whatever the test order is
        redistribution.redistribute(newPeriod(), now.minusSeconds(1));
        fillPool();
        for (int i = 0; i < 4; i++) {
            operations.getOrCreate("u-small-" + UUID.randomUUID());
        }
        Map<String, Integer> before = availableByUser();
        assertThat(poolBalance()).isEqualTo(3);
        assertThat(before.size()).as("accounts").isGreaterThan(3);

        // a share of 0 writes no lot or history row (both need an amount above 0),
        // so the run commits instead of failing every month
        RedistributionOutcome outcome = redistribution.redistribute(newPeriod(), now);

        assertThat(outcome.share()).isZero();
        assertThat(outcome.remainder()).isEqualTo(3);
        assertThat(outcome.distributed()).isEqualTo(3);
        assertThat(poolBalance()).isZero();
        Map<String, Integer> after = availableByUser();
        assertThat(before.keySet().stream().filter(user -> after.get(user) - before.get(user) == 1)).hasSize(3);
        assertThat(before.keySet().stream().filter(user -> after.get(user).equals(before.get(user))))
            .hasSize(before.size() - 3);
        assertThat(jdbc.queryForObject("select count(*) from credit_lot where earned_at = ?",
            Integer.class, java.sql.Timestamp.from(now))).isEqualTo(3);
        assertThat(jdbc.queryForObject(
            "select count(*) from credit_history where type = 'REDISTRIBUTION' and occurred_at = ?",
            Integer.class, java.sql.Timestamp.from(now))).isEqualTo(3);
    }

    @Test
    void aSecondRunForTheSameMonthChangesNothing() {
        YearMonth period = newPeriod();
        fillPool();
        redistribution.redistribute(period, now);
        // expired credits arrive in the pool after the month's run
        fillPool();
        int pool = poolBalance();
        Map<String, Integer> before = availableByUser();

        assertThat(redistribution.redistribute(period, now).alreadyRan()).isTrue();

        assertThat(poolBalance()).isEqualTo(pool);
        assertThat(availableByUser()).isEqualTo(before);
    }

    @Test
    void anEmptyPoolStillRecordsTheMonth() {
        // drains whatever earlier tests left in the pool
        redistribution.redistribute(newPeriod(), now);
        Map<String, Integer> before = availableByUser();
        YearMonth period = newPeriod();

        RedistributionOutcome outcome = redistribution.redistribute(period, now);

        assertThat(outcome.alreadyRan()).isFalse();
        assertThat(outcome.distributed()).isZero();
        assertThat(availableByUser()).isEqualTo(before);
        assertThat(jdbc.queryForMap("select distributed, remainder from redistribution_run where period = ?",
            period.toString())).containsEntry("distributed", 0).containsEntry("remainder", 0);
    }

    @Test
    void twoRunsForOneMonthAtOnceShareThePoolOnce() throws Exception {
        fillPool();
        int pool = poolBalance();
        YearMonth period = newPeriod();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> runs = List.of(
                threads.submit(() -> runWhenReleased(start, period)),
                threads.submit(() -> runWhenReleased(start, period)));
            start.countDown();

            // one shares the pool; the other finds the month recorded,
            // or loses on the period's primary key
            List<Object> results = List.of(runs.get(0).get(30, TimeUnit.SECONDS),
                runs.get(1).get(30, TimeUnit.SECONDS));
            assertThat(results).filteredOn(result -> result instanceof RedistributionOutcome outcome
                && !outcome.alreadyRan()).hasSize(1);
            assertThat(results).filteredOn(result -> result instanceof RedistributionOutcome outcome
                && outcome.distributed() == pool).hasSize(1);
        } finally {
            threads.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from redistribution_run where period = ?",
            Integer.class, period.toString())).isEqualTo(1);
        assertThat(poolBalance()).isZero();
    }

    private Object runWhenReleased(CountDownLatch start, YearMonth period) throws InterruptedException {
        start.await();
        try {
            return redistribution.redistribute(period, now);
        } catch (DataIntegrityViolationException lostOnThePrimaryKey) {
            return lostOnThePrimaryKey;
        }
    }

    // a fresh requester's reward becomes the user's earned lot
    private void earn(String user, int amount) {
        String request = "r-" + UUID.randomUUID();
        operations.reserve(request, "u-req-" + UUID.randomUUID(), amount);
        operations.transfer(request, user);
    }

    private void expireEarnedLotsOf(String user) {
        jdbc.update("update credit_lot set expires_at = ? where owner_id = ? and expires_at is not null",
            java.sql.Timestamp.from(now.minus(1, ChronoUnit.DAYS)), user);
    }

    // 3 credits through a real expiry, so the books still balance
    private void fillPool() {
        String user = "u-pool-" + UUID.randomUUID();
        earn(user, 3);
        expireEarnedLotsOf(user);
        expiry.expireLotsOf(user, now);
    }

    private static YearMonth newPeriod() {
        return YearMonth.of(NEXT_YEAR.getAndIncrement(), 1);
    }

    private int poolBalance() {
        return jdbc.queryForObject("select balance from common_pool", Integer.class);
    }

    private int available(String user) {
        return jdbc.queryForObject("select available from credit_account where user_id = ?", Integer.class, user);
    }

    private Map<String, Integer> availableByUser() {
        Map<String, Integer> available = new HashMap<>();
        jdbc.query("select user_id, available from credit_account",
            row -> {
                available.put(row.getString("user_id"), row.getInt("available"));
            });
        return available;
    }

    private List<String> lotsOf(String user) {
        return jdbc.queryForList("""
            select remaining || case when expires_at is null then ' never' else ' expiring' end
            from credit_lot where owner_id = ? order by id
            """, String.class, user);
    }

    private List<String> historyOf(String user) {
        return jdbc.queryForList("select type || ' ' || amount from credit_history where user_id = ? order by id",
            String.class, user);
    }
}
