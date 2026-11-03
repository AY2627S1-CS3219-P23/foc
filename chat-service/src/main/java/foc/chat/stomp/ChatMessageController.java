/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the STOMP send handler (C8): a party's message, text and/or a
 * base64 image in the frame, stored by ChatService and pushed to the
 * chat's topic; a refusal goes back to the sender's own session only.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.stomp;

import foc.chat.dto.SendMessage;
import foc.chat.service.ChatClosedException;
import foc.chat.service.ChatNotFoundException;
import foc.chat.service.ChatService;
import foc.chat.service.InvalidMessageException;
import java.security.Principal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
class ChatMessageController {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageController.class);

    private final ChatService chatService;

    ChatMessageController(ChatService chatService) {
        this.chatService = chatService;
    }

    // the stored message reaches the sender too, through the topic they
    // are subscribed to, so nothing is returned here
    @MessageMapping("/chats/{chatId}/messages")
    void send(@DestinationVariable UUID chatId, @Payload SendMessage body, Principal principal) {
        chatService.send(((StompPrincipal) principal).caller(), chatId, body);
    }

    // broadcast = false: only the session that sent the frame is told
    @MessageExceptionHandler({ChatNotFoundException.class, ChatClosedException.class,
            InvalidMessageException.class})
    @SendToUser(destinations = ChatChannelInterceptor.ERRORS_DESTINATION_SUFFIX, broadcast = false)
    ErrorFrame refused(RuntimeException e) {
        return new ErrorFrame(e.getMessage());
    }

    // anything else (a body that isn't JSON, a bad chat id): a generic
    // error, logged, since a handled exception reaches no other log
    @MessageExceptionHandler
    @SendToUser(destinations = ChatChannelInterceptor.ERRORS_DESTINATION_SUFFIX, broadcast = false)
    ErrorFrame failed(Exception e) {
        log.warn("chat message not handled: {}", e.toString());
        return new ErrorFrame("The message could not be sent");
    }

    record ErrorFrame(String detail) {
    }
}
