/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-06, issue #48 (DP10).
 * Scope: the retention purge under ShedLock (team decision, Leong Wei Zhi
 * via options Q&A), on the Testcontainers Postgres with Flyway on: while
 * another replica holds notification.retention-purge, a run through the
 * bean is skipped; once it is released, the run purges.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import foc.notification.PostgresTestContainer;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {PostgresTestContainer.FLYWAY_ON, PostgresTestContainer.DDL_VALIDATE})
class RetentionPurgeLockTest extends PostgresTestContainer {

	private static final String LOCK_NAME = "notification.retention-purge";

	@Autowired
	private RetentionPurgeScheduler scheduler;

	@Autowired
	private LockProvider lockProvider;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void aRunWhileAnotherReplicaHoldsTheLockIsSkipped() {
		String eventId = "purge-lock-" + Instant.now().toEpochMilli();
		jdbc.update("INSERT INTO processed_events (event_id, processed_at) VALUES (?, ?)",
				eventId, Timestamp.from(Instant.now().minus(40, ChronoUnit.DAYS)));

		// "another replica" mid-purge
		SimpleLock held = lockProvider.lock(new LockConfiguration(Instant.now(), LOCK_NAME,
				Duration.ofMinutes(1), Duration.ZERO)).orElseThrow();
		try {
			scheduler.purgeExpiredNotifications();
			assertThat(count(eventId)).as("skipped while the lock is held").isEqualTo(1);
		} finally {
			held.unlock();
		}

		scheduler.purgeExpiredNotifications();
		assertThat(count(eventId)).as("purged once the lock is free").isZero();
	}

	private int count(String eventId) {
		return jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId);
	}
}
