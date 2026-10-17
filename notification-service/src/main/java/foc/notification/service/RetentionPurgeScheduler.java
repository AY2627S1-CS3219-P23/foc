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
 * 2026-10-17, issue #137: the run also sweeps processed_events (author
 * decision via neutral options: same 30-day window, no new config).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.service;

import foc.notification.repository.NotificationRepository;
import foc.notification.repository.ProcessedEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hard-deletes notification rows once they have been stored longer
 * than the configured retention window (D9, F3.4), and sweeps the
 * processed_events dedupe table past the same window (issue #137).
 * Purging a dedupe row reopens the duplicate-detection window for that
 * event ID — accepted trade-off: retries are 10 s TTL × 3 then DLQ, so
 * only a manual DLQ replay ≥ the retention window later could
 * re-process an event, and it would recreate notifications this purge
 * already deleted. Plain business logic with zero broker imports
 * (D11): the scheduler only depends on repositories, so a broker swap
 * never touches it.
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
	private final ProcessedEventRepository processedEvents;
	private final int retentionDays;

	RetentionPurgeScheduler(NotificationRepository notifications,
			ProcessedEventRepository processedEvents,
			@Value("${notification.retention.days}") int retentionDays) {
		this.notifications = notifications;
		this.processedEvents = processedEvents;
		this.retentionDays = retentionDays;
	}

	@Scheduled(cron = "${notification.retention.purge-cron}")
	void purgeExpiredNotifications() {
		Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
		int deletedNotifications = notifications.deleteByCreatedAtBefore(cutoff);
		int deletedEvents = processedEvents.deleteByProcessedAtBefore(cutoff);
		log.info("purged {} notification(s) and {} processed-event row(s) stored before {}",
				deletedNotifications, deletedEvents, cutoff);
	}
}
