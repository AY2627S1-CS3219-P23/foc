/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-20.
 * Scope: unit test for issue #68's retention purge scheduler (team
 * decision D9, docs/notification-service.md), exercising the purge
 * method directly rather than waiting on the cron trigger, per the
 * design doc's "testing follows the port" style (D11).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import foc.notification.entity.Notification;
import foc.notification.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs on H2 with the listener container disabled by the test
 * configuration (no broker involved, D11). The test-configured
 * retention window is 30 days ({@code src/test/resources/application.yaml}).
 * {@code @Transactional} gives the backdating JPQL update an active
 * transaction to run in (unlike {@code @DataJpaTest}, plain
 * {@code @SpringBootTest} doesn't wrap each test in one).
 */
@SpringBootTest
@Transactional
class RetentionPurgeSchedulerTest {

	@Autowired
	private RetentionPurgeScheduler scheduler;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void clearDatabase() {
		notifications.deleteAll();
	}

	@Test
	void purgesOnlyRowsStoredBeyondTheRetentionWindow() {
		Notification expired = notifications.save(new Notification("usr-1", "e-1", "request.created",
				"{}", Instant.now()));
		Notification recent = notifications.save(new Notification("usr-2", "e-2", "request.created",
				"{}", Instant.now()));
		backdate(expired.getId(), Instant.now().minus(31, ChronoUnit.DAYS));
		backdate(recent.getId(), Instant.now().minus(29, ChronoUnit.DAYS));

		scheduler.purgeExpiredNotifications();

		assertThat(notifications.findAll()).extracting(Notification::getId)
				.containsExactly(recent.getId());
	}

	private void backdate(Long id, Instant createdAt) {
		entityManager.createQuery("update Notification n set n.createdAt = :createdAt where n.id = :id")
				.setParameter("createdAt", createdAt)
				.setParameter("id", id)
				.executeUpdate();
		entityManager.flush();
		entityManager.clear();
	}
}
