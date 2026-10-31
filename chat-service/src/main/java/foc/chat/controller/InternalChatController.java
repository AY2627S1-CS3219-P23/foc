/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: order-service's open and close calls (C11), accepted only with
 * the shared internal token (C12).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.controller;

import foc.chat.dto.CloseChatRequest;
import foc.chat.dto.OpenChatRequest;
import foc.chat.service.ChatService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Both calls are idempotent and answer 204, so order-service can retry
 * them freely. A missing or wrong token is a 401; so is every call while
 * {@code CHAT_INTERNAL_TOKEN} is unset.
 */
@RestController
@RequestMapping("/internal/chats")
class InternalChatController {

    static final String TOKEN_HEADER = "X-Internal-Token";

    private final ChatService chatService;
    private final byte[] token;

    InternalChatController(ChatService chatService, @Value("${chat.internal.token}") String token) {
        this.chatService = chatService;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/open")
    ResponseEntity<Void> open(@RequestHeader(name = TOKEN_HEADER, required = false) String presented,
            @Valid @RequestBody OpenChatRequest body) {
        if (!authorized(presented)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        chatService.open(body.requestId(), body.requesterId(), body.courierId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/close")
    ResponseEntity<Void> close(@RequestHeader(name = TOKEN_HEADER, required = false) String presented,
            @Valid @RequestBody CloseChatRequest body) {
        if (!authorized(presented)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        chatService.close(body.requestId());
        return ResponseEntity.noContent().build();
    }

    // constant-time comparison, so the token can't be guessed by timing
    private boolean authorized(String presented) {
        return token.length > 0 && presented != null
                && MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8));
    }
}
