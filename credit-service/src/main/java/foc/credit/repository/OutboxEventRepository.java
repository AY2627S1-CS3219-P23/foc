/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: empty Spring Data repository for the scaffold; queries come
 * with the features that need them.
 * 2026-10-12, Claude Code (Opus 5.5), issue #170: the publisher's poll
 * query.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import foc.credit.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // the publisher's poll (D10): unsent replies, oldest first, on the
    // partial index idx_outbox_event_unsent
    List<OutboxEvent> findBySentAtIsNullOrderByCreatedAtAsc(Limit limit);
}
