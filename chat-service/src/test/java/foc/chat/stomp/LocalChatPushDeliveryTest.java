/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (DP10).
 * Scope: cross-replica STOMP fanout over AMQP (team decision, Leong Wei
 * Zhi via options Q&A): unit tests that a received push reaches this
 * replica's broker as a topic broadcast or a user send, per its kind.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LocalChatPushDeliveryTest {

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final LocalChatPushDelivery delivery = new LocalChatPushDelivery(template);
    private final JsonNode payload = JsonMapper.builder().build().readTree("{\"type\":\"CLOSED\"}");

    @Test
    void aTopicPushIsBroadcastToTheTopic() {
        delivery.deliver(ChatPush.topic("/topic/chats/c-1", payload));

        verify(template).convertAndSend("/topic/chats/c-1", payload);
        verifyNoMoreInteractions(template);
    }

    @Test
    void aUserPushGoesToThatUsersDestination() {
        delivery.deliver(ChatPush.user("22", "/queue/chat", payload));

        verify(template).convertAndSendToUser("22", "/queue/chat", payload);
        verifyNoMoreInteractions(template);
    }
}
