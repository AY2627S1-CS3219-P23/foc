/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the STOMP adapter of the ChatPublisher port: frames go to the
 * chat's topic on the in-memory broker.
 * 2026-10-31: the receiver's unread frame goes to their own queue,
 * /user/queue/chat (C16-C19).
 * Author review: Ko-Khan, pending pull request review.
 * 2026-11-05, Claude Code (Opus 5.5), issue #48 (DP10): cross-replica STOMP
 * fanout over AMQP (team decision, Leong Wei Zhi via options Q&A) — the
 * frames go to every replica through ChatPushFanout instead of to this
 * replica's broker only; a failed publish is logged, not thrown. Author
 * review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import foc.chat.dto.ChatFrame;
import foc.chat.dto.MessageView;
import foc.chat.dto.UnreadFrame;
import foc.chat.service.ChatPublisher;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// The subscriber may be on any replica, so every frame goes to all of
// them (DP10); this replica delivers its own copy when it comes back.
// Pushes run after the commit and are best-effort: a failure here must
// not fail the send or close that already committed.
@Component
class StompChatPublisher implements ChatPublisher {

    static final String TOPIC_PREFIX = "/topic/chats/";
    // the user-destination suffix; clients subscribe to /user/queue/chat
    static final String UNREAD_QUEUE = "/queue/chat";

    private static final Logger log = LoggerFactory.getLogger(StompChatPublisher.class);

    private final ChatPushFanout fanout;
    private final JsonMapper jsonMapper;

    StompChatPublisher(ChatPushFanout fanout, JsonMapper jsonMapper) {
        this.fanout = fanout;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void messageSent(MessageView message) {
        publish(() -> ChatPush.topic(TOPIC_PREFIX + message.chatId(),
                jsonMapper.valueToTree(ChatFrame.message(message))));
    }

    @Override
    public void chatClosed(UUID chatId) {
        publish(() -> ChatPush.topic(TOPIC_PREFIX + chatId, jsonMapper.valueToTree(ChatFrame.closed(chatId))));
    }

    // every session of the receiver gets it (each open tab updates its badge)
    @Override
    public void unread(long receiverId, UnreadFrame frame) {
        publish(() -> ChatPush.user(Long.toString(receiverId), UNREAD_QUEUE, jsonMapper.valueToTree(frame)));
    }

    private void publish(java.util.function.Supplier<ChatPush> push) {
        try {
            fanout.publish(push.get());
        } catch (RuntimeException ex) {
            log.warn("chat push failed", ex);
        }
    }
}
