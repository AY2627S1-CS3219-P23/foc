/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the STOMP adapter of the ChatPublisher port: frames go to the
 * chat's topic on the in-memory broker.
 * 2026-10-31: the receiver's unread frame goes to their own queue,
 * /user/queue/chat (C16-C19).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.stomp;

import foc.chat.dto.ChatFrame;
import foc.chat.dto.MessageView;
import foc.chat.dto.UnreadFrame;
import foc.chat.service.ChatPublisher;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
class StompChatPublisher implements ChatPublisher {

    static final String TOPIC_PREFIX = "/topic/chats/";
    // the user-destination suffix; clients subscribe to /user/queue/chat
    static final String UNREAD_QUEUE = "/queue/chat";

    private final SimpMessagingTemplate template;

    StompChatPublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    @Override
    public void messageSent(MessageView message) {
        template.convertAndSend(TOPIC_PREFIX + message.chatId(), ChatFrame.message(message));
    }

    @Override
    public void chatClosed(UUID chatId) {
        template.convertAndSend(TOPIC_PREFIX + chatId, ChatFrame.closed(chatId));
    }

    // every session of the receiver gets it (each open tab updates its badge)
    @Override
    public void unread(long receiverId, UnreadFrame frame) {
        template.convertAndSendToUser(Long.toString(receiverId), UNREAD_QUEUE, frame);
    }
}
