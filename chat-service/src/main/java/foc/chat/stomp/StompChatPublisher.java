/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the STOMP adapter of the ChatPublisher port: frames go to the
 * chat's topic on the in-memory broker.
 * 2026-10-31: the receiver's unread frame goes to their own queue,
 * /user/queue/chat (C16-C19).
 * Author review: Ko-Khan, pending pull request review.
 * 2026-11-08, Claude Code (Fable 5.1), issue #305 (team decision, Leong Wei
 * Zhi via options Q&A): frames are published to the chat.push fanout
 * instead of this JVM's broker; ChatPushListener delivers them on every
 * replica. Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import foc.chat.dto.ChatFrame;
import foc.chat.dto.MessageView;
import foc.chat.dto.UnreadFrame;
import foc.chat.service.ChatPublisher;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class StompChatPublisher implements ChatPublisher {

    static final String TOPIC_PREFIX = "/topic/chats/";
    // the user-destination suffix; clients subscribe to /user/queue/chat
    static final String UNREAD_QUEUE = "/queue/chat";

    private static final Logger log = LoggerFactory.getLogger(StompChatPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;

    StompChatPublisher(RabbitTemplate rabbitTemplate, JsonMapper jsonMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void messageSent(MessageView message) {
        publish(null, TOPIC_PREFIX + message.chatId(), ChatFrame.message(message));
    }

    @Override
    public void chatClosed(UUID chatId) {
        publish(null, TOPIC_PREFIX + chatId, ChatFrame.closed(chatId));
    }

    // every session of the receiver gets it (each open tab updates its badge)
    @Override
    public void unread(long receiverId, UnreadFrame frame) {
        publish(Long.toString(receiverId), UNREAD_QUEUE, frame);
    }

    // Called after the transaction committed: a failed publish is logged, not
    // thrown, so it can never undo or re-run the committed send (issue #305).
    private void publish(String user, String destination, Object frame) {
        try {
            rabbitTemplate.convertAndSend(ChatPushFanoutConfig.PUSH_EXCHANGE, "",
                    new ChatPush(user, destination, jsonMapper.valueToTree(frame)));
        } catch (RuntimeException ex) {
            log.warn("push to {} failed", destination, ex);
        }
    }
}
