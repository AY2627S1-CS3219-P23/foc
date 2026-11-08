/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, PR #306 review (Claude
 * reviewer: the duplicate-key ack had only isolated unit tests).
 * Scope: two transactions race one event id on a real PostgreSQL; the
 * loser's failure is the one DomainEventsListener acks as a duplicate.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import foc.contracts.events.rating.RatingSubmitted;
import foc.notification.PostgresTestContainer;
import foc.notification.service.EventProcessor;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {PostgresTestContainer.FLYWAY_ON, PostgresTestContainer.DDL_VALIDATE})
class ConcurrentDuplicateIntegrationTest extends PostgresTestContainer {

	@Autowired
	private EventProcessor eventProcessor;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	void theLosingDeliveryFailsOnProcessedEventsPkeyAndCountsAsADuplicate() throws Exception {
		RatingSubmitted event = new RatingSubmitted("e-race-1", Instant.parse("2026-11-08T09:30:00Z"),
				"rating-service", "c-race", "rat-20261108-0001",
				"req-20261108-0042", "usr-req-1001", "usr-cou-2002", 4);
		CountDownLatch winnerInserted = new CountDownLatch(1);
		CountDownLatch commitWinner = new CountDownLatch(1);

		// the winner: holds an uncommitted dedupe row for the event id
		CompletableFuture<Void> winner = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(tx -> {
			jdbcTemplate.update("INSERT INTO processed_events (event_id, processed_at) VALUES (?, now())",
					event.eventId());
			winnerInserted.countDown();
			await(commitWinner);
		}));
		await(winnerInserted);

		// the loser: its dedupe read cannot see the uncommitted row, so it
		// goes on and its insert waits on the winner's key
		CompletableFuture<Void> loser = CompletableFuture.runAsync(() -> eventProcessor.process(event));
		waitForALockWait();
		commitWinner.countDown();
		winner.get(10, TimeUnit.SECONDS);

		Throwable failure = catchThrowable(() -> loser.get(10, TimeUnit.SECONDS));

		assertThat(failure).isInstanceOf(ExecutionException.class);
		assertThat(DomainEventsListener.isDuplicateKey(failure.getCause())).isTrue();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM notifications WHERE event_id = ?", Integer.class, event.eventId()))
				.isZero();
	}

	private void waitForALockWait() throws InterruptedException {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (jdbcTemplate.queryForObject(
				"SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'", Integer.class) == 0) {
			assertThat(Instant.now()).as("the loser never blocked on the winner's key").isBefore(deadline);
			Thread.sleep(50);
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}
}
