/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-06.
 * Scope: issue #48 (DP10): insert-first dedupe (team decision, Leong Wei
 * Zhi via options Q&A) on the Testcontainers Postgres. Two deliveries of
 * one event processed at once (two replicas) store one notification and
 * push once, and the losing delivery returns normally (the listener acks
 * it). To make the race certain rather than likely, the test holds an
 * uncommitted dedupe row for the event until both deliveries are waiting
 * on it in pg_locks — both have passed the seen-before check by then —
 * and then rolls it back.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import foc.contracts.events.request.RequestCreated;
import foc.notification.PostgresTestContainer;
import foc.notification.messaging.stomp.UserPushFanout;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {PostgresTestContainer.FLYWAY_ON, PostgresTestContainer.DDL_VALIDATE})
class ConcurrentDedupeTest extends PostgresTestContainer {

	@Autowired
	private EventProcessor eventProcessor;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbc;

	// what leaves the replica after commit: one call per stored row
	@MockitoBean
	private UserPushFanout fanout;

	@Test
	void twoSimultaneousDeliveriesOfOneEventStoreAndPushOnce() throws Exception {
		String eventId = "concurrent-" + Instant.now().toEpochMilli();
		// request.created tells the requester alone (D34): one row, one push
		RequestCreated event = new RequestCreated(eventId, Instant.parse("2026-11-06T08:00:00Z"),
				"order-service", "c-1", "req-" + eventId, "usr-req-1001", "Techno Edge", "COM3-01-19", "hi");

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try (Connection holder = dataSource.getConnection()) {
			holder.setAutoCommit(false);
			try (PreparedStatement st = holder.prepareStatement(
					"INSERT INTO processed_events (event_id, processed_at) VALUES (?, now())")) {
				st.setString(1, eventId);
				st.executeUpdate();
			}

			Future<?> first = pool.submit(() -> eventProcessor.process(event));
			Future<?> second = pool.submit(() -> eventProcessor.process(event));

			// both deliveries are blocked on the held row's key: each saw no
			// committed dedupe row and is inserting its own
			awaitWaitersOnTransactionLock(2);

			holder.rollback(); // the two deliveries now race for the key
			holder.setAutoCommit(true);

			// neither throws: the loser is skipped like a seen-before event
			first.get(30, TimeUnit.SECONDS);
			second.get(30, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
		}

		assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE event_id = ?", Integer.class, eventId))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class,
				eventId)).isEqualTo(1);
		verify(fanout, times(1)).publish(any());
	}

	private void awaitWaitersOnTransactionLock(int waiters) throws InterruptedException {
		// a blocked unique-key insert waits on the holder's transaction id
		String sql = "SELECT count(*) FROM pg_locks WHERE locktype = 'transactionid' AND NOT granted";
		Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
		while (jdbc.queryForObject(sql, Long.class) < waiters) {
			assertThat(Instant.now()).as("both deliveries queued on the held key").isBefore(deadline);
			Thread.sleep(50);
		}
	}
}
