/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the push side of the service as a port, so the rules in
 * ChatService do not depend on the STOMP broker (StompChatPublisher is
 * the adapter).
 * 2026-10-31: the receiver's unread push (C16-C19).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.service;

import foc.chat.dto.MessageView;
import foc.chat.dto.UnreadFrame;
import java.util.UUID;

public interface ChatPublisher {

    /** Pushes a stored message to the chat's subscribers. */
    void messageSent(MessageView message);

    /** Tells the chat's subscribers it has ended. */
    void chatClosed(UUID chatId);

    /** Tells one user their unread count for a chat went up. */
    void unread(long receiverId, UnreadFrame frame);
}
