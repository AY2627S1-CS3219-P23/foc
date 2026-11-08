/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305.
 * Scope: a fanned-out push is handed to this replica's broker — a topic
 * broadcast without a user, a user send with one — and a failing send is
 * swallowed. Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ChatPushListenerTest {

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final ChatPushListener listener = new ChatPushListener(template);
    private final JsonNode payload = JsonMapper.builder().build().readTree("{\"type\":\"CLOSED\"}");

    @Test
    void aTopicPushIsBroadcast() {
        listener.onPush(new ChatPush(null, "/topic/chats/abc", payload));

        verify(template).convertAndSend("/topic/chats/abc", payload);
    }

    @Test
    void aUserPushGoesToThatUser() {
        listener.onPush(new ChatPush("11", "/queue/chat", payload));

        verify(template).convertAndSendToUser("11", "/queue/chat", payload);
    }

    @Test
    void aFailingSendIsLoggedNotThrown() {
        doThrow(new IllegalStateException("x")).when(template).convertAndSend(anyString(), any(Object.class));

        assertThatCode(() -> listener.onPush(new ChatPush(null, "/topic/chats/abc", payload)))
                .doesNotThrowAnyException();
    }
}
