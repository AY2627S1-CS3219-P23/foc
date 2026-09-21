/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: JSON message converter wiring for issue #64 per team
 * decisions D11 (broker types confined to this package) and D15 (the
 * AMQP converter must use Boot's auto-configured Jackson mapper so
 * the tolerant-reader contract test's guarantees hold on the wire).
 * Same day, Method-B refactor (D16-D19): the generic Jackson
 * converter is replaced by the registry-driven DomainEventMessageConverter.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Declares the message converter for all AMQP traffic: the
 * registry-driven {@link DomainEventMessageConverter} over Boot's
 * auto-configured {@link JsonMapper} (D15) — the same mapper the
 * contract test asserts tolerant reading against, so unknown fields
 * are ignored at the broker boundary too (F1.3). Boot applies this
 * single {@code MessageConverter} bean to both the listener container
 * factory and {@code RabbitTemplate}; the record class is resolved
 * from the body's {@code eventType}, so publishers never set type
 * headers (D11, D18).
 */
@Configuration
class RabbitMqMessageConverterConfig {

	@Bean
	DomainEventMessageConverter rabbitJsonMessageConverter(JsonMapper jsonMapper) {
		return new DomainEventMessageConverter(jsonMapper);
	}
}
