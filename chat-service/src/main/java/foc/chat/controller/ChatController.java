/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the REST reads behind the chat pop-up (C14): a request's chats,
 * a chat's history page, and a message's image. Who may read is
 * ChatService's (C6, C13).
 * 2026-10-31: POST /chats/{chatId}/read, the pop-up having been opened,
 * which resets the caller's unread count (C19).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.controller;

import foc.chat.dto.ChatView;
import foc.chat.dto.MessageView;
import foc.chat.entity.ChatMessage;
import foc.chat.security.Caller;
import foc.chat.service.ChatService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chats")
class ChatController {

    private final ChatService chatService;

    ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // a party: the open chat they are in, or an empty list; an admin:
    // every chat the request has had, newest first
    @GetMapping
    List<ChatView> chatsOf(@RequestParam long requestId, Authentication auth) {
        return chatService.chatsOf(caller(auth), requestId);
    }

    // newest first; page back with before = the oldest sentAt shown
    @GetMapping("/{chatId}/messages")
    List<MessageView> history(@PathVariable UUID chatId, @RequestParam(required = false) Instant before,
            @RequestParam(required = false) Integer size, Authentication auth) {
        return chatService.history(caller(auth), chatId, before, size);
    }

    // the caller has the chat open: their unread count starts over
    @PostMapping("/{chatId}/read")
    ResponseEntity<Void> markRead(@PathVariable UUID chatId, Authentication auth) {
        chatService.markRead(caller(auth), chatId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{chatId}/messages/{messageId}/image")
    ResponseEntity<byte[]> image(@PathVariable UUID chatId, @PathVariable UUID messageId, Authentication auth) {
        ChatMessage message = chatService.image(caller(auth), chatId, messageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(message.getImageContentType()))
                // a message never changes; private, as it is one chat's
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(message.getImage());
    }

    // JwtAuthenticationFilter's principal is the user id; its one
    // authority is ROLE_<role>
    static Caller caller(Authentication auth) {
        String role = auth.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(name -> name.startsWith("ROLE_"))
                .map(name -> name.substring("ROLE_".length()))
                .findFirst().orElse("");
        return Caller.of(auth.getName(), role);
    }
}
