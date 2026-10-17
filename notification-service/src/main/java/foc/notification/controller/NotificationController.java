/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: the REST API of docs/notification-service.md "REST API
 * contract" (F3.1, F3.2): the four routes, their parameters and the
 * checks behind the contract's 400s and 404s. The routes, shapes,
 * bounds and errors are the author's decisions made via options Q&A
 * (D24-D27); this transcribes them. Controller structure follows
 * credit-service's CreditController.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.controller;

import foc.notification.dto.MarkReadRequest;
import foc.notification.dto.PageResponse;
import foc.notification.dto.UnreadCountResponse;
import foc.notification.service.NotificationDto;
import foc.notification.service.NotificationQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own notifications (F3.1, F3.2). The caller is the
 * token's user, set by {@code JwtAuthenticationFilter}; no route takes
 * a user ID, so there is no way to another user's data (D24).
 */
@RestController
@RequestMapping("/notifications/me")
public class NotificationController {

	static final int MAX_PAGE_SIZE = 100;

	private final NotificationQueryService notifications;

	public NotificationController(NotificationQueryService notifications) {
		this.notifications = notifications;
	}

	// page and size are plain integers checked here, not Spring's
	// Pageable: that would turn a negative page into 0, cap an oversized
	// size silently and admit a sort parameter (same stance as
	// credit-service)
	@GetMapping
	public PageResponse<NotificationDto> list(
			Authentication caller,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "false") boolean unread) {
		if (page < 0) {
			throw new InvalidQueryParameterException("page must be 0 or more");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidQueryParameterException("size must be between 1 and " + MAX_PAGE_SIZE);
		}
		return PageResponse.of(notifications.list(caller.getName(), unread, page, size));
	}

	// what the bell badge re-syncs from on load, reconnect and after
	// writes; the list's totalElements can't serve this, it counts all
	// rows unless the unread filter is on
	@GetMapping("/unread-count")
	public UnreadCountResponse unreadCount(Authentication caller) {
		return new UnreadCountResponse(notifications.unreadCount(caller.getName()));
	}

	/**
	 * Marks one notification read or unread (F3.2). 404 when the ID
	 * doesn't exist or isn't the caller's — indistinguishable by design.
	 * Repeating a mark is the same 204, not an error.
	 */
	@PatchMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void setRead(Authentication caller, @PathVariable long id, @RequestBody MarkReadRequest body) {
		if (body == null || body.read() == null) {
			throw new InvalidRequestBodyException("read is required and must be true or false");
		}
		if (!notifications.setRead(caller.getName(), id, body.read())) {
			throw new NotificationNotFoundException();
		}
	}

	/** Marks every unread notification of the caller read, in one call. */
	@PostMapping("/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markAllRead(Authentication caller) {
		notifications.markAllRead(caller.getName());
	}
}
