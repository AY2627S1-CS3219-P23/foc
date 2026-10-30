/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: message converter wiring, following notification-service's and
 * credit-service's RabbitMqMessageConverterConfig (same package there).
 * Author review: [pending]
 */
package foc.rating.messaging.rabbitmq;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Declares the message converter for inbound request events: the
 * registry-driven {@link DomainEventMessageConverter} over Boot's
 * auto-configured {@link JsonMapper}, so the same mapper settings hold
 * at the broker boundary. Boot applies the single bean to both the
 * listener container factory and the {@code RabbitTemplate}.
 */
@Configuration
class RabbitMqMessageConverterConfig {

    @Bean
    DomainEventMessageConverter rabbitJsonMessageConverter(JsonMapper jsonMapper) {
        return new DomainEventMessageConverter(jsonMapper);
    }
}
