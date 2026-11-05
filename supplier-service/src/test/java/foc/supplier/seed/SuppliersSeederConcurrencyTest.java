/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): Flyway baseline / advisory-locked seeding for
 * multi-replica starts (team decision, Leong Wei Zhi via options Q&A).
 * Two seeding runs at once against one empty Postgres (two replicas
 * starting together) leave exactly one copy of the CSV. To make the race
 * certain rather than likely, the test holds the seeding lock itself
 * until both runs are queued on it in pg_locks, then releases it.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.supplier.seed;

import static org.assertj.core.api.Assertions.assertThat;

import foc.supplier.PostgresTestContainer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {PostgresTestContainer.FLYWAY_ON, PostgresTestContainer.DDL_VALIDATE})
class SuppliersSeederConcurrencyTest extends PostgresTestContainer {

    @Autowired
    private SuppliersSeeder seeder;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // data rows in the seed CSV (header excluded)
    static long csvRowCount() throws Exception {
        ClassPathResource csv = new ClassPathResource("csv/supplier-seed-data.csv");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csv.getInputStream(), Charset.forName("windows-1252")))) {
            return reader.lines().filter(line -> !line.isBlank()).count() - 1;
        }
    }

    @Test
    void twoSimultaneousSeedsOnAnEmptyDatabaseLoadTheCsvOnce() throws Exception {
        // the context's own startup seeded already: empty the tables again
        jdbcTemplate.execute("TRUNCATE supplier_bookmarks, supplier_categories, suppliers RESTART IDENTITY");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM suppliers", Long.class)).isZero();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (Statement st = holder.createStatement()) {
                st.execute("SELECT pg_advisory_xact_lock(" + SeedLock.KEY_CLASS + ", " + SeedLock.KEY_SUPPLIERS_CSV + ")");
            }

            Future<?> first = pool.submit(() -> { seeder.run(); return null; });
            Future<?> second = pool.submit(() -> { seeder.run(); return null; });

            // both runs are waiting on the seeding lock, neither has counted yet
            awaitWaitersOnSeedLock(2);

            holder.rollback(); // releases the lock: the two runs now race
            holder.setAutoCommit(true);

            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        long expected = csvRowCount();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM suppliers", Long.class)).isEqualTo(expected);
        assertThat(jdbcTemplate.queryForObject("SELECT count(DISTINCT name) FROM suppliers", Long.class)).isEqualTo(expected);
    }

    private void awaitWaitersOnSeedLock(int waiters) throws InterruptedException {
        // two-int advisory locks show in pg_locks as classid/objid with objsubid 2
        String sql = "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted"
                + " AND classid = " + SeedLock.KEY_CLASS + " AND objid = " + SeedLock.KEY_SUPPLIERS_CSV
                + " AND objsubid = 2";
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (jdbcTemplate.queryForObject(sql, Long.class) < waiters) {
            assertThat(Instant.now()).as("both seeding runs queued on the lock").isBefore(deadline);
            Thread.sleep(50);
        }
    }
}
