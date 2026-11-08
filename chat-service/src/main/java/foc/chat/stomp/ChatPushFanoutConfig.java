/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (team decision:
 * AMQP fanout for the cross-replica STOMP push, Leong Wei Zhi via options
 * Q&A). Scope: the AMQP JSON converter and the exchange name the push
 * fanout uses. Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cross-replica push (issue #305). chat-service pushes STOMP frames from a
 * per-JVM simple broker, so with two replicas a frame produced on one would
 * miss a browser connected to the other. {@link StompChatPublisher} now
 * publishes every push to the service-local fanout exchange
 * {@value #PUSH_EXCHANGE}; each replica — the publisher included — consumes
 * it on its own anonymous queue ({@link ChatPushListener}) and offers it to
 * its own broker. A socket lives on exactly one replica, so a frame reaches
 * it once; replicas without a matching session or subscription send nothing.
 *
 * <p>This is the only AMQP use in the service: no domain events in or out
 * (C11 unchanged), no foc-contracts.
 */
@Configuration
class ChatPushFanoutConfig {

    static final String PUSH_EXCHANGE = "chat.push";

    /** JSON on the wire, with Boot's mapper (Instant/UUID as in the STOMP frames). */
    @Bean
    JacksonJsonMessageConverter amqpJsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
