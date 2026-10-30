/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: dedupe-table repository, following notification-service's
 * (without its retention sweep — rating-service has no purge yet).
 * Author review: [pending]
 */
package foc.rating.repository;

import foc.rating.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
