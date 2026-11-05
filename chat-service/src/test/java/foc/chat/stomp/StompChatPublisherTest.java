/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (DP10).
 * Scope: cross-replica STOMP fanout over AMQP (team decision, Leong Wei
 * Zhi via options Q&A): unit tests that each frame is published to the
 * fanout with the right kind, destination and payload, and that a failed
 * publish is not thrown.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
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
import tools.jackson.databind.json.JsonMapper;

class StompChatPublisherTest {

    private static final UUID CHAT = UUID.fromString("5f0c1e9a-3b5e-4c55-9d38-0b8f7c2b1a11");

    private final ChatPushFanout fanout = mock(ChatPushFanout.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    // no SimpMessagingTemplate: the fanout is the only way out
    private final StompChatPublisher publisher = new StompChatPublisher(fanout, jsonMapper);

    private ChatPush published() {
        ArgumentCaptor<ChatPush> push = ArgumentCaptor.forClass(ChatPush.class);
        verify(fanout).publish(push.capture());
        return push.getValue();
    }

    @Test
    void aSentMessageGoesToTheChatsTopic() {
        MessageView message = new MessageView(UUID.randomUUID(), CHAT, 11, "outside now", false,
                Instant.parse("2026-11-05T08:00:00Z"));

        publisher.messageSent(message);

        ChatPush push = published();
        assertThat(push.kind()).isEqualTo(ChatPush.Kind.TOPIC);
        assertThat(push.user()).isNull();
        assertThat(push.destination()).isEqualTo("/topic/chats/" + CHAT);
        assertThat(push.payload()).isEqualTo(jsonMapper.valueToTree(ChatFrame.message(message)));
        assertThat(push.payload().get("type").asString()).isEqualTo("MESSAGE");
    }

    @Test
    void aCloseGoesToTheChatsTopic() {
        publisher.chatClosed(CHAT);

        ChatPush push = published();
        assertThat(push.kind()).isEqualTo(ChatPush.Kind.TOPIC);
        assertThat(push.destination()).isEqualTo("/topic/chats/" + CHAT);
        assertThat(push.payload()).isEqualTo(jsonMapper.valueToTree(ChatFrame.closed(CHAT)));
    }

    @Test
    void anUnreadFrameGoesToTheReceiversOwnQueue() {
        UnreadFrame frame = new UnreadFrame(CHAT, 7, 1, true, 22, "outside now", false);

        publisher.unread(11, frame);

        ChatPush push = published();
        assertThat(push.kind()).isEqualTo(ChatPush.Kind.USER);
        assertThat(push.user()).isEqualTo("11");
        assertThat(push.destination()).isEqualTo("/queue/chat");
        assertThat(push.payload()).isEqualTo(jsonMapper.valueToTree(frame));
    }

    @Test
    void aFailedPublishIsLoggedNotThrown() {
        doThrow(new IllegalStateException("broker down")).when(fanout).publish(any());

        assertThatCode(() -> publisher.chatClosed(CHAT)).doesNotThrowAnyException();
    }
}
