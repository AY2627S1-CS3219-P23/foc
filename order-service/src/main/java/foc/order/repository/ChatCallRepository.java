/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the dispatcher's poll over chat_call (C11).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order.repository;

import foc.order.model.ChatCall;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatCallRepository extends JpaRepository<ChatCall, Long> {

    // The pending calls that are due, oldest first, but only each request's
    // oldest pending call: a close must not overtake the open it follows,
    // so a later call waits while an earlier one is still being retried.
    @Query("""
        select c from ChatCall c
        where c.status = foc.order.model.ChatCall.Status.PENDING and c.nextAttemptAt <= :now
          and not exists (
            select 1 from ChatCall e
            where e.requestId = c.requestId and e.status = foc.order.model.ChatCall.Status.PENDING
              and e.id < c.id)
        order by c.id
        """)
    List<ChatCall> findDue(@Param("now") Instant now, Limit limit);

    List<ChatCall> findByRequestIdOrderById(Long requestId);
}
