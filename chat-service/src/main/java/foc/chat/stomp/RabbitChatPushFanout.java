/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (DP10).
 * Scope: cross-replica STOMP fanout over AMQP (team decision, Leong Wei
 * Zhi via options Q&A): publishes each push to the chat.push exchange
 * and delivers every push it receives to this replica's STOMP broker.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * A push goes out as the {@link ChatPush} JSON, non-persistent (the
 * queues are not durable), to {@value ChatPushFanoutConfig#PUSH_EXCHANGE};
 * every replica's queue gets a copy and {@link #onPush} hands it to
 * {@link LocalChatPushDelivery}. The publisher never delivers locally: its
 * copy comes back through its own queue. A push that cannot be read or
 * delivered is logged and dropped.
 */
@Component
class RabbitChatPushFanout implements ChatPushFanout {

    // a fanout exchange ignores the routing key
    private static final String NO_ROUTING_KEY = "";

    private static final Logger log = LoggerFactory.getLogger(RabbitChatPushFanout.class);

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final LocalChatPushDelivery localDelivery;

    RabbitChatPushFanout(RabbitTemplate rabbitTemplate, JsonMapper jsonMapper, LocalChatPushDelivery localDelivery) {
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMapper = jsonMapper;
        this.localDelivery = localDelivery;
    }

    @Override
    public void publish(ChatPush push) {
        Message message = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(push))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT)
                .build();
        rabbitTemplate.send(ChatPushFanoutConfig.PUSH_EXCHANGE, NO_ROUTING_KEY, message);
    }

    @RabbitListener(queues = "#{chatPushQueue.name}",
            containerFactory = ChatPushFanoutConfig.PUSH_LISTENER_CONTAINER_FACTORY)
    void onPush(Message message) {
        try {
            localDelivery.deliver(jsonMapper.readValue(message.getBody(), ChatPush.class));
        } catch (RuntimeException ex) {
            log.warn("dropped a fanned-out chat push that could not be delivered", ex);
        }
    }
}
