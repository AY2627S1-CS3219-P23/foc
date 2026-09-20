/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-20.
 * Scope: retention purge scheduler for issue #68, implementing team
 * decision D9 (docs/notification-service.md): configurable retention
 * window (NOTIF_RETENTION_DAYS, default 30) plus a scheduled purge job.
 * Cutoff field (created_at vs occurred_at) and schedule-cadence
 * configurability (a second env var vs a fixed cron) were open
 * implementation details resolved by the author via neutral options.
 * 2026-09-20, PR #81 Copilot review: the repository's purge query
 * became an explicit bulk {@code @Modifying @Query} (was a derived
 * {@code deleteBy...} method), so its return type is {@code int}.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.service;

import foc.notification.repository.NotificationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hard-deletes notification rows once they have been stored longer
 * than the configured retention window (D9, F3.4). Plain business
 * logic with zero broker imports (D11): the scheduler only depends on
 * {@link NotificationRepository}, so a broker swap never touches it.
 *
 * <p>Runs on the app's sole {@link org.springframework.scheduling.TaskScheduler}
 * bean (the STOMP heartbeat scheduler declared in
 * {@code WebSocketStompConfig}) — Boot backs off its own default
 * scheduler once a user-defined one exists.
 */
@Component
class RetentionPurgeScheduler {

	private static final Logger log = LoggerFactory.getLogger(RetentionPurgeScheduler.class);

	private final NotificationRepository notifications;
	private final int retentionDays;

	RetentionPurgeScheduler(NotificationRepository notifications,
			@Value("${notification.retention.days}") int retentionDays) {
		this.notifications = notifications;
		this.retentionDays = retentionDays;
	}

	@Scheduled(cron = "${notification.retention.purge-cron}")
	void purgeExpiredNotifications() {
		Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
		int deleted = notifications.deleteByCreatedAtBefore(cutoff);
		log.info("purged {} notification(s) stored before {}", deleted, cutoff);
	}
}
