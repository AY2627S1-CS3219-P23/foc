/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-18.
 * Scope: repository scaffolded for issue #61.
 * 2026-09-19, issue #64: moved to the repository package (author
 * decision: controller-service-repository layout).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.repository;

import foc.notification.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
