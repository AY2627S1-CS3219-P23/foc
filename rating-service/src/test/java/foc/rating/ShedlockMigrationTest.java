/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (DP10).
 * Scope: V6 (ShedLock's lock table, team decision, Leong Wei Zhi via
 * options Q&A) on Postgres, in the way FlywayMigrationTests runs the
 * migrations: the rating-service context is on H2 with Flyway off, so
 * the table and a lock taken with the database's clock are checked here.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.rating;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ShedlockMigrationTest extends PostgresTestContainer {

    // not the application's schema, so the table starts empty
    private static final String SCHEMA = "migration_shedlock";

    @Test
    void v6CreatesTheLockTableAndALockIsHeldUntilReleased() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        List<String> columns = jdbc.queryForList("select column_name from information_schema.columns"
                + " where table_schema = ? and table_name = 'shedlock'", String.class, SCHEMA);
        assertThat(columns).containsExactlyInAnyOrder("name", "lock_until", "locked_at", "locked_by");

        // built as SchedulerLockConfig builds it, pointed at the test schema
        LockProvider lockProvider = new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbc)
                .withTableName(SCHEMA + ".shedlock")
                .withTransactionManager(new DataSourceTransactionManager(dataSource))
                .usingDbTime()
                .build());
        LockConfiguration lock = new LockConfiguration(Instant.now(), "rating.outbox-publisher",
                Duration.ofSeconds(30), Duration.ZERO);

        Optional<SimpleLock> first = lockProvider.lock(lock);
        assertThat(first).isPresent();
        // what a second replica sees while the job runs: it skips the run
        assertThat(lockProvider.lock(lock)).isEmpty();
        first.get().unlock();
        Optional<SimpleLock> again = lockProvider.lock(lock);
        assertThat(again).isPresent();
        again.get().unlock();
    }
}
