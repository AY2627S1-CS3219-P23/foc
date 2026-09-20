/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-20.
 * Scope: repository test for issue #68's deleteByCreatedAtBefore
 * (team decision D9, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import foc.notification.entity.Notification;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * {@code created_at} is {@code updatable = false} (Notification.java),
 * so a managed-entity save can never move it; rows are backdated via a
 * JPQL bulk update through the raw {@link EntityManager} instead, which
 * bypasses Hibernate's dirty-checking entirely.
 */
@DataJpaTest
class NotificationRepositoryTest {

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private EntityManager entityManager;

	@Test
	void deletesOnlyRowsOlderThanCutoff() {
		Notification oldRow = notifications.save(new Notification("usr-1", "e-1", "request.created",
				"{}", Instant.now()));
		Notification freshRow = notifications.save(new Notification("usr-2", "e-2", "request.created",
				"{}", Instant.now()));
		backdate(oldRow.getId(), Instant.now().minus(40, ChronoUnit.DAYS));

		Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
		int deleted = notifications.deleteByCreatedAtBefore(cutoff);

		assertThat(deleted).isEqualTo(1);
		assertThat(notifications.findAll()).extracting(Notification::getId)
				.containsExactly(freshRow.getId());
	}

	@Test
	void leavesEverythingWhenNothingIsOlderThanCutoff() {
		notifications.save(new Notification("usr-1", "e-1", "request.created", "{}", Instant.now()));

		int deleted = notifications.deleteByCreatedAtBefore(Instant.now().minus(30, ChronoUnit.DAYS));

		assertThat(deleted).isZero();
		assertThat(notifications.count()).isEqualTo(1);
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
