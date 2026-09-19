/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-18.
 * Scope: repository scaffolded for issue #61.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
