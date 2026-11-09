/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305.
 * Scope: each ChatPublisher call publishes one envelope to the chat.push
 * fanout — topic frames with no user, the unread frame to the receiver —
 * carrying the frame's own JSON; a failing publish is swallowed (it runs
 * after commit). Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import foc.chat.dto.ChatFrame;
import foc.chat.dto.MessageView;
import foc.chat.dto.UnreadFrame;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

class StompChatPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final StompChatPublisher publisher = new StompChatPublisher(rabbitTemplate, jsonMapper);

    private static final UUID CHAT = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final MessageView MESSAGE = new MessageView(UUID.randomUUID(), CHAT, 22L, "hi", false,
            Instant.parse("2026-11-08T08:00:00Z"));

    private ChatPush published() {
        ArgumentCaptor<Object> envelope = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(ChatPushFanoutConfig.PUSH_EXCHANGE), eq(""), envelope.capture());
        return (ChatPush) envelope.getValue();
    }

    @Test
    void aMessageIsBroadcastToTheChatTopic() {
        publisher.messageSent(MESSAGE);

        ChatPush push = published();
        assertThat(push.user()).isNull();
        assertThat(push.destination()).isEqualTo("/topic/chats/" + CHAT);
        assertThat(push.payload()).isEqualTo(jsonMapper.valueToTree(ChatFrame.message(MESSAGE)));
    }

    @Test
    void aCloseIsBroadcastToTheChatTopic() {
        publisher.chatClosed(CHAT);

        ChatPush push = published();
        assertThat(push.user()).isNull();
        assertThat(push.destination()).isEqualTo("/topic/chats/" + CHAT);
        assertThat(push.payload().get("type").asString()).isEqualTo("CLOSED");
    }

    @Test
    void anUnreadFrameGoesToTheReceiversQueue() {
        UnreadFrame frame = new UnreadFrame(CHAT, 7L, 1, true, 22L, "hi", false);

        publisher.unread(11L, frame);

        ChatPush push = published();
        assertThat(push.user()).isEqualTo("11");
        assertThat(push.destination()).isEqualTo("/queue/chat");
        assertThat(push.payload()).isEqualTo(jsonMapper.valueToTree(frame));
    }

    @Test
    void aFailingPublishIsLoggedNotThrown() {
        doThrow(new IllegalStateException("broker down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> publisher.chatClosed(CHAT)).doesNotThrowAnyException();
    }
}
