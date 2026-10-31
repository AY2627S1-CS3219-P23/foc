/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: chat lookups for the open/close calls and the REST API.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.repository;

import foc.chat.entity.Chat;
import foc.chat.entity.ChatStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    // at most one, by the partial unique index chat_one_open_per_request
    Optional<Chat> findByRequestIdAndStatus(long requestId, ChatStatus status);

    // an admin's view of a request: every chat it has had, newest first
    List<Chat> findByRequestIdOrderByOpenedAtDesc(long requestId);

    // the retention purge's second step: closed chats with nothing left to
    // read, closed before the cutoff
    @Modifying
    @Query("""
        delete from Chat c
        where c.status = foc.chat.entity.ChatStatus.CLOSED and c.closedAt < :cutoff
          and not exists (select 1 from ChatMessage m where m.chatId = c.id)
        """)
    int deleteEmptyClosedBefore(@Param("cutoff") Instant cutoff);
}
