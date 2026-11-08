/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (team decision:
 * AMQP fanout for the cross-replica STOMP push, Leong Wei Zhi via options
 * Q&A). Scope: the per-replica consumer of the chat.push fanout, delivering
 * each push into this JVM's simple broker. Author review: Leong Wei Zhi,
 * pending pull request review.
 */
package foc.chat.stomp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * The receiving half of the push fanout (issue #305, see
 * {@link ChatPushFanoutConfig}). The anonymous queue is exclusive and
 * auto-delete: declared by this listener, redeclared on reconnect, gone
 * with the replica. Acks automatically and never requeues: a push is
 * best-effort, and the pop-up re-fetches the newest page on reconnect.
 */
@Component
class ChatPushListener {

    private static final Logger log = LoggerFactory.getLogger(ChatPushListener.class);

    private final SimpMessagingTemplate template;

    ChatPushListener(SimpMessagingTemplate template) {
        this.template = template;
    }

    @RabbitListener(ackMode = "AUTO", bindings = @QueueBinding(
            value = @Queue,
            exchange = @Exchange(value = ChatPushFanoutConfig.PUSH_EXCHANGE, type = ExchangeTypes.FANOUT)))
    void onPush(ChatPush push) {
        try {
            if (push.user() == null) {
                template.convertAndSend(push.destination(), push.payload());
            } else {
                template.convertAndSendToUser(push.user(), push.destination(), push.payload());
            }
        } catch (RuntimeException ex) {
            log.warn("push to {} failed", push.destination(), ex);
        }
    }
}
