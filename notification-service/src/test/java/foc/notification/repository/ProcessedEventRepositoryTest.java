/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17.
 * Scope: repository test for issue #137's deleteByProcessedAtBefore,
 * mirroring NotificationRepositoryTest.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import foc.notification.entity.ProcessedEvent;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Rows are backdated via a JPQL bulk update through the raw
 * {@link EntityManager} (the pattern from NotificationRepositoryTest),
 * which bypasses Hibernate's dirty-checking entirely.
 */
@DataJpaTest
class ProcessedEventRepositoryTest {

	@Autowired
	private ProcessedEventRepository processedEvents;

	@Autowired
	private EntityManager entityManager;

	@Test
	void deletesOnlyRowsOlderThanCutoff() {
		ProcessedEvent oldRow = processedEvents.save(new ProcessedEvent("e-old"));
		ProcessedEvent freshRow = processedEvents.save(new ProcessedEvent("e-new"));
		backdate(oldRow.getEventId(), Instant.now().minus(40, ChronoUnit.DAYS));

		Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
		int deleted = processedEvents.deleteByProcessedAtBefore(cutoff);

		assertThat(deleted).isEqualTo(1);
		assertThat(processedEvents.findAll()).extracting(ProcessedEvent::getEventId)
				.containsExactly(freshRow.getEventId());
	}

	@Test
	void leavesEverythingWhenNothingIsOlderThanCutoff() {
		processedEvents.save(new ProcessedEvent("e-1"));

		int deleted = processedEvents.deleteByProcessedAtBefore(Instant.now().minus(30, ChronoUnit.DAYS));

		assertThat(deleted).isZero();
		assertThat(processedEvents.count()).isEqualTo(1);
	}

	private void backdate(String eventId, Instant processedAt) {
		entityManager.createQuery("update ProcessedEvent p set p.processedAt = :processedAt where p.eventId = :id")
				.setParameter("processedAt", processedAt)
				.setParameter("id", eventId)
				.executeUpdate();
		entityManager.flush();
		entityManager.clear();
	}
}
