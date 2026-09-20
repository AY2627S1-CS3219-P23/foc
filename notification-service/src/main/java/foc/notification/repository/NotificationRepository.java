/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-18.
 * Scope: repository scaffolded for issue #61.
 * 2026-09-19, issue #64: moved to the repository package (author
 * decision: controller-service-repository layout).
 * 2026-09-20, issue #68: deleteByCreatedAtBefore added for the
 * retention purge scheduler (team decision D9, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.repository;

import foc.notification.entity.Notification;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/**
	 * Hard-deletes every row stored before {@code cutoff} (D9/F3.4).
	 * Spring Data recognizes {@code deleteBy...} as a modifying query
	 * and runs it in its own transaction automatically.
	 */
	long deleteByCreatedAtBefore(Instant cutoff);
}
