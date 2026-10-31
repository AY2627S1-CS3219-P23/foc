/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: message reads (without the image bytes) and the retention purge.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.repository;

import foc.chat.dto.MessageView;
import foc.chat.entity.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    // a history page: newest first, sent before the given time; the image
    // bytes are never selected
    @Query("""
        select new foc.chat.dto.MessageView(m.id, m.chatId, m.senderId, m.text, m.imageContentType, m.sentAt)
        from ChatMessage m
        where m.chatId = :chatId and m.sentAt < :before
        order by m.sentAt desc, m.id desc
        """)
    List<MessageView> findPage(@Param("chatId") UUID chatId, @Param("before") Instant before, Limit limit);

    Optional<ChatMessage> findByIdAndChatId(UUID id, UUID chatId);

    // a party's unread count: the other party's messages, all of them if
    // the party never opened the chat, else those sent after they last did
    long countByChatIdAndSenderIdNot(UUID chatId, long receiverId);

    long countByChatIdAndSenderIdNotAndSentAtAfter(UUID chatId, long receiverId, Instant after);

    // the retention purge (C10)
    @Modifying
    @Query("delete from ChatMessage m where m.sentAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") Instant cutoff);
}
