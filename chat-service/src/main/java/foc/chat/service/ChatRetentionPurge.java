/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the retention purge (C10): messages older than
 * CHAT_RETENTION_DAYS (30 by default), daily at 03:00 by default
 * (CHAT_PURGE_CRON), then the closed chats left empty.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.service;

import foc.chat.repository.ChatMessageRepository;
import foc.chat.repository.ChatRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChatRetentionPurge {

    private static final Logger log = LoggerFactory.getLogger(ChatRetentionPurge.class);

    private final ChatMessageRepository messages;
    private final ChatRepository chats;
    private final Duration retention;

    public ChatRetentionPurge(ChatMessageRepository messages, ChatRepository chats,
            @Value("${chat.retention.days}") int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalStateException("CHAT_RETENTION_DAYS must be at least 1");
        }
        this.messages = messages;
        this.chats = chats;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "${chat.retention.purge-cron}")
    @Transactional
    public void purge() {
        Instant cutoff = Instant.now().minus(retention);
        int purgedMessages = messages.deleteSentBefore(cutoff);
        int purgedChats = chats.deleteEmptyClosedBefore(cutoff);
        log.info("chat retention purged messages={} chats={} before={}", purgedMessages, purgedChats, cutoff);
    }
}
