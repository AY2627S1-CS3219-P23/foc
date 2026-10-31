/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: following notification-service's JwtChannelInterceptor: CONNECT
 * (and STOMP) frames authenticated by the bearer JWT and checked against
 * the denylist (C15); then every SUBSCRIBE limited to a chat the caller
 * may read (C6, C13) or their own error queue, and every SEND to the one
 * message destination (who may send is checked by ChatService).
 * 2026-10-31: the caller's own unread queue may be subscribed too
 * (C16-C19).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.stomp;

import foc.chat.security.Caller;
import foc.chat.security.JwtVerifier;
import foc.chat.security.TokenDenylist;
import foc.chat.service.ChatService;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * A throw here makes the sub-protocol handler send a STOMP ERROR frame
 * with this exception's message and close the session, so the messages
 * stay generic.
 */
@Component
class ChatChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    // a sender's refused messages: @SendToUser names the suffix, the
    // client subscribes to the /user-prefixed form
    static final String ERRORS_DESTINATION_SUFFIX = "/queue/errors";
    static final String ERRORS_DESTINATION = "/user" + ERRORS_DESTINATION_SUFFIX;
    // the caller's unread pushes (StompChatPublisher.UNREAD_QUEUE)
    static final String UNREAD_DESTINATION = "/user/queue/chat";
    private static final Pattern TOPIC = Pattern.compile("^/topic/chats/([0-9a-fA-F-]{36})$");
    private static final Pattern SEND = Pattern.compile("^/app/chats/[0-9a-fA-F-]{36}/messages$");

    private final JwtVerifier jwtVerifier;
    private final TokenDenylist tokenDenylist;
    private final ChatService chatService;

    // ChatService lazily: it needs the broker's messaging template, whose
    // configuration needs this interceptor, so an eager one is a cycle
    ChatChannelInterceptor(JwtVerifier jwtVerifier, TokenDenylist tokenDenylist, @Lazy ChatService chatService) {
        this.jwtVerifier = jwtVerifier;
        this.tokenDenylist = tokenDenylist;
        this.chatService = chatService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // the mutable accessor on CONNECT, so setUser sticks
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        // null for non-frame messages (heartbeats): pass
        if (StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command)) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            checkSubscribe(caller(accessor), accessor.getDestination());
        } else if (StompCommand.SEND.equals(command)) {
            String destination = accessor.getDestination();
            caller(accessor);
            if (destination == null || !SEND.matcher(destination).matches()) {
                throw new MessagingException("sending not allowed");
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new MessagingException("missing bearer token");
        }
        JwtVerifier.VerifiedToken verified;
        Caller caller;
        try {
            verified = jwtVerifier.verify(header.substring(BEARER_PREFIX.length()));
            caller = Caller.of(verified.userId(), verified.role());
        } catch (JwtException | IllegalArgumentException e) {
            // NumberFormatException (a non-numeric sub) is an IllegalArgumentException
            throw new MessagingException("invalid token");
        }
        // outside the catch: a denylist failure already failed open inside
        // the port, so a throw from here is a real reject
        if (isRevoked(verified)) {
            throw new MessagingException("token revoked");
        }
        accessor.setUser(new StompPrincipal(caller));
    }

    // both of the gateway's lookups, as notification-service does them
    private boolean isRevoked(JwtVerifier.VerifiedToken verified) {
        if (verified.jti() != null && tokenDenylist.isRevoked(verified.jti())) {
            return true;
        }
        return verified.issuedAt() != null
                && tokenDenylist.isUserRevokedSince(verified.userId(), verified.issuedAt());
    }

    private void checkSubscribe(Caller caller, String destination) {
        // the caller's own queues: user destinations resolve to the session's
        // principal, so nobody can read another user's
        if (ERRORS_DESTINATION.equals(destination) || UNREAD_DESTINATION.equals(destination)) {
            return;
        }
        Matcher topic = destination == null ? null : TOPIC.matcher(destination);
        if (topic == null || !topic.matches()) {
            throw new MessagingException("subscription not allowed");
        }
        UUID chatId;
        try {
            chatId = UUID.fromString(topic.group(1));
        } catch (IllegalArgumentException e) {
            throw new MessagingException("subscription not allowed");
        }
        if (!chatService.canRead(caller, chatId)) {
            throw new MessagingException("subscription not allowed");
        }
    }

    // every frame after CONNECT inherits the session's principal
    private static Caller caller(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof StompPrincipal principal) {
            return principal.caller();
        }
        throw new MessagingException("not connected");
    }
}
