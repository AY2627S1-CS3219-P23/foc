/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: the PATCH's 404 (docs/notification-service.md "REST API
 * contract"): no notification with that ID belongs to the caller —
 * someone else's and nonexistent are deliberately the same answer, so
 * the API leaks no other user's IDs (D26).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.controller;

/** The caller has no such notification; carries no detail of whether anyone does. */
public class NotificationNotFoundException extends RuntimeException {

	public NotificationNotFoundException() {
		super("No such notification");
	}
}
