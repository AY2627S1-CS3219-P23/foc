/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: the reads and read-flag writes behind the REST API (F3.1,
 * F3.2), in the layering credit-service's CreditQueryService uses. The
 * list order (created_at, newest first — author decision via options
 * Q&A, docs/notification-service.md D25) and the count-checked bulk
 * writes (D26) are the user's decisions; this transcribes them.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.service;

import foc.notification.entity.Notification;
import foc.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * What a user may read and change of their own notifications: a page
 * of them, the unread count, and the read flag (F3.1, F3.2). Every
 * query is scoped to the caller's recipient ID — the JWT subject the
 * security filter verified — so there is no path to another user's
 * rows.
 */
@Service
public class NotificationQueryService {

	// the contract's order, with no sort parameter: newest stored row
	// first (created_at is when this service stored it, so a
	// late-delivered event surfaces at the top, where a catch-up reader
	// expects it), and the ID settles rows of the same instant so pages
	// do not overlap (D25)
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final NotificationRepository notifications;
	private final ObjectMapper objectMapper;

	public NotificationQueryService(NotificationRepository notifications, ObjectMapper objectMapper) {
		this.notifications = notifications;
		this.objectMapper = objectMapper;
	}

	/** A page past the last one is an empty page, not an error. */
	public Page<NotificationDto> list(String recipientId, boolean unreadOnly, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size, NEWEST_FIRST);
		Page<Notification> rows = unreadOnly
				? notifications.findByRecipientIdAndReadFalse(recipientId, pageRequest)
				: notifications.findByRecipientId(recipientId, pageRequest);
		return rows.map(notification -> NotificationDto.from(notification, objectMapper));
	}

	public long unreadCount(String recipientId) {
		return notifications.countByRecipientIdAndReadFalse(recipientId);
	}

	/**
	 * Sets one notification's read flag.
	 *
	 * @return false when the caller has no such notification — theirs or
	 *         anyone's, indistinguishably (the controller answers 404)
	 */
	public boolean setRead(String recipientId, long id, boolean read) {
		return notifications.updateReadFlag(id, recipientId, read) > 0;
	}

	/** Marks everything unread read; already-read rows make it a no-op, not an error. */
	public void markAllRead(String recipientId) {
		notifications.markAllRead(recipientId);
	}
}
