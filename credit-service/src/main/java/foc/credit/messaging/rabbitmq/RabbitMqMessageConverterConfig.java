/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: message converter wiring, following notification-service's
 * RabbitMqMessageConverterConfig (same package there).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Declares the message converter for inbound request events: the
 * registry-driven {@link DomainEventMessageConverter} over Boot's
 * auto-configured {@link JsonMapper}, so the spring.jackson settings in
 * application.yaml (a fractional reward is refused, not truncated) hold
 * at the broker boundary.
 */
@Configuration
class RabbitMqMessageConverterConfig {

	@Bean
	DomainEventMessageConverter rabbitJsonMessageConverter(JsonMapper jsonMapper) {
		return new DomainEventMessageConverter(jsonMapper);
	}
}
