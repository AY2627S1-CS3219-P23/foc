/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (DP10).
 * Scope: cross-replica STOMP fanout over AMQP (team decision, Leong Wei
 * Zhi via options Q&A): unit tests for the AMQP adapter (envelope,
 * exchange, no local delivery on publish, received pushes delivered,
 * unreadable ones dropped) and the fanout topology (exchange/queue/binding
 * shape, the listener factory's AUTO ack).
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import tools.jackson.databind.json.JsonMapper;

class RabbitChatPushFanoutTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final LocalChatPushDelivery localDelivery = mock(LocalChatPushDelivery.class);
    private final RabbitChatPushFanout fanout = new RabbitChatPushFanout(rabbitTemplate, jsonMapper, localDelivery);

    private final ChatPush push = ChatPush.user("22", "/queue/chat",
            jsonMapper.readTree("{\"chatId\":\"c-1\",\"unreadCount\":1,\"first\":true}"));

    @Test
    void publishesThePushToTheFanoutExchangeOnly() {
        fanout.publish(push);

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("chat.push"), eq(""), sent.capture());
        MessageProperties properties = sent.getValue().getMessageProperties();
        assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.NON_PERSISTENT);
        assertThat(jsonMapper.readValue(sent.getValue().getBody(), ChatPush.class)).isEqualTo(push);
        // the publisher's copy comes back through its own queue
        verifyNoInteractions(localDelivery);
    }

    @Test
    void aReceivedPushIsDeliveredLocally() {
        ChatPush topic = ChatPush.topic("/topic/chats/c-1", jsonMapper.readTree("{\"type\":\"CLOSED\"}"));

        fanout.onPush(MessageBuilder.withBody(jsonMapper.writeValueAsBytes(topic)).build());

        verify(localDelivery).deliver(topic);
    }

    @Test
    void anUnreadablePushIsDroppedNotThrown() {
        Message garbage = MessageBuilder.withBody("not json".getBytes(StandardCharsets.UTF_8)).build();

        assertThatCode(() -> fanout.onPush(garbage)).doesNotThrowAnyException();
        verifyNoInteractions(localDelivery);
    }

    @Test
    void eachReplicaBindsItsOwnTransientQueueToTheFanoutExchange() {
        ChatPushFanoutConfig config = new ChatPushFanoutConfig();
        FanoutExchange exchange = config.chatPushExchange();
        AnonymousQueue queue = config.chatPushQueue();

        Binding binding = config.chatPushBinding(exchange, queue);

        assertThat(exchange.getName()).isEqualTo("chat.push");
        assertThat(exchange.getType()).isEqualTo(ExchangeTypes.FANOUT);
        assertThat(queue.isDurable()).isFalse();
        assertThat(queue.isExclusive()).isTrue();
        assertThat(queue.isAutoDelete()).isTrue();
        // a fresh name per replica
        assertThat(config.chatPushQueue().getName()).isNotEqualTo(queue.getName());
        assertThat(binding.getExchange()).isEqualTo("chat.push");
        assertThat(binding.getDestination()).isEqualTo(queue.getName());
    }

    @Test
    void thePushListenerAcksAutomatically() {
        RabbitProperties properties = new RabbitProperties();
        // even if the service ever sets manual acks for another listener
        properties.getListener().getSimple().setAcknowledgeMode(AcknowledgeMode.MANUAL);

        SimpleMessageListenerContainer container = new ChatPushFanoutConfig()
                .chatPushListenerContainerFactory(new SimpleRabbitListenerContainerFactoryConfigurer(properties),
                        mock(ConnectionFactory.class))
                .createListenerContainer();

        assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.AUTO);
    }
}
