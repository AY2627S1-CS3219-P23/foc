/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: unit tests of the STOMP frame policing: CONNECT auth and the
 * denylist (C15), SUBSCRIBE limited to readable chats and the error
 * queue, SEND limited to the message destination.
 * 2026-10-31: the caller's own unread queue may be subscribed (C16).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import foc.chat.TestTokens;
import foc.chat.security.Caller;
import foc.chat.security.JwtVerifier;
import foc.chat.security.TokenDenylist;
import foc.chat.service.ChatService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class ChatChannelInterceptorTest {

    private final TokenDenylist denylist = mock(TokenDenylist.class);
    private final ChatService chatService = mock(ChatService.class);
    private final ChatChannelInterceptor interceptor =
            new ChatChannelInterceptor(new JwtVerifier(TestTokens.SECRET), denylist, chatService);
    private final MessageChannel channel = mock(MessageChannel.class);

    private static Message<byte[]> frame(StompCommand command, String destination, StompPrincipal user,
            String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static StompPrincipal user(long id) {
        return new StompPrincipal(new Caller(id, false));
    }

    @Test
    void aValidConnectAttachesTheCaller() {
        Message<byte[]> connect = frame(StompCommand.CONNECT, null, null, "Bearer " + TestTokens.admin(7));

        interceptor.preSend(connect, channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(connect);
        assertThat(accessor.getUser()).isEqualTo(new StompPrincipal(new Caller(7, true)));
    }

    @Test
    void aConnectWithoutAValidTokenIsRefused() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.CONNECT, null, null, "Bearer not-a-jwt"), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void aLoggedOutTokenIsRefused() {
        when(denylist.isRevoked(anyString())).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.CONNECT, null, null, "Bearer " + TestTokens.user(7)), channel))
                .isInstanceOf(MessagingException.class).hasMessage("token revoked");
    }

    @Test
    void aSubscriptionNeedsAChatTheCallerCanRead() {
        UUID readable = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(chatService.canRead(eq(new Caller(7, false)), eq(readable))).thenReturn(true);

        interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/chats/" + readable, user(7), null), channel);
        interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/errors", user(7), null), channel);
        interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/chat", user(7), null), channel);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/chats/" + other, user(7), null), channel))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/anything", user(7), null), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void sendingIsOnlyToAChatsMessageDestination() {
        UUID chat = UUID.randomUUID();

        interceptor.preSend(frame(StompCommand.SEND, "/app/chats/" + chat + "/messages", user(7), null), channel);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SEND, "/topic/chats/" + chat, user(7), null), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void framesBeforeConnectAreRefused() {
        when(chatService.canRead(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/chats/" + UUID.randomUUID(), null, null), channel))
                .isInstanceOf(MessagingException.class);
    }
}
