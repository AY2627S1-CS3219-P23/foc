/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: message converter wiring, a copy of credit-service's
 * RabbitMqMessageConverterConfig with the package and javadoc changed.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.messaging.rabbitmq;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Declares the message converter for inbound Credit Service replies:
 * the registry-driven {@link DomainEventMessageConverter} over Boot's
 * auto-configured {@link JsonMapper}.
 */
@Configuration
class RabbitMqMessageConverterConfig {

	@Bean
	DomainEventMessageConverter rabbitJsonMessageConverter(JsonMapper jsonMapper) {
		return new DomainEventMessageConverter(jsonMapper);
	}
}
