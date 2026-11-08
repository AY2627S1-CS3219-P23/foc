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
 * 2026-11-08, Claude Code (Fable 5.1), issue #305 (D35): a second listener
 * container factory for the push fanout queue — plain converter, auto ack
 * — because the push envelope is not a domain event. Author review: Leong
 * Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
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
 *
 * <p>The push fanout queue (D35) carries {@code UserPush} envelopes, not
 * events, so its listener runs on {@link #pushFanoutContainerFactory} —
 * the same yaml-configured container (auto-startup, concurrency) but with
 * the plain converter, which leaves the body to the listener, and
 * automatic acks (the manual mode is for the work queues' commit-then-ack).
 * A rejected push is dropped, never requeued: a push is best-effort.
 */
@Configuration
class RabbitMqMessageConverterConfig {

	static final String PUSH_FANOUT_CONTAINER_FACTORY = "pushFanoutContainerFactory";

	@Bean
	DomainEventMessageConverter rabbitJsonMessageConverter(JsonMapper jsonMapper) {
		return new DomainEventMessageConverter(jsonMapper);
	}

	@Bean(PUSH_FANOUT_CONTAINER_FACTORY)
	SimpleRabbitListenerContainerFactory pushFanoutContainerFactory(
			SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		configurer.configure(factory, connectionFactory);
		factory.setMessageConverter(new SimpleMessageConverter());
		factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
		factory.setDefaultRequeueRejected(false);
		return factory;
	}
}
