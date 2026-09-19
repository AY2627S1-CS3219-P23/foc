/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: application event for issue #67's post-commit push hook
 * (design doc: processor emits stored notifications to the push
 * gateway via an in-process call, after the DB commit).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.service;

import foc.notification.entity.Notification;

/**
 * Published by the event processor for each notification row it
 * saves, inside the processing transaction. Transport adapters listen
 * with {@code @TransactionalEventListener(AFTER_COMMIT)}, so a push
 * happens only after the row is durably committed — and never for
 * rolled-back, duplicate, or stale events, which save no rows.
 */
public record NotificationStoredEvent(Notification notification) {
}
