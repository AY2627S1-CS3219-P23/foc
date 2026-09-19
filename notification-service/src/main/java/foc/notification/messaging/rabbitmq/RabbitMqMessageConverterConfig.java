/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: JSON message converter wiring for issue #64 per team
 * decisions D11 (broker types confined to this package) and D15 (the
 * AMQP converter must use Boot's auto-configured Jackson mapper so
 * the tolerant-reader contract test's guarantees hold on the wire).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Declares the JSON message converter for all AMQP consumption,
 * wrapping Boot's auto-configured {@link JsonMapper} (D15) — the same
 * mapper the envelope contract test asserts tolerant reading against,
 * so unknown fields and event types are ignored at the broker
 * boundary too (F1.3). Boot's listener-container auto-configuration
 * picks up this single {@code MessageConverter} bean; the target type
 * is inferred from the listener method parameter, so publishers need
 * not set type headers.
 */
@Configuration
class RabbitMqMessageConverterConfig {

	@Bean
	JacksonJsonMessageConverter rabbitJsonMessageConverter(JsonMapper jsonMapper) {
		return new JacksonJsonMessageConverter(jsonMapper);
	}
}
