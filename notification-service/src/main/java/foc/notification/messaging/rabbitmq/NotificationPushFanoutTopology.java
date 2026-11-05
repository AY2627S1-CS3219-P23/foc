/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): cross-replica STOMP fanout over AMQP (team
 * decision, Leong Wei Zhi via options Q&A) — the fanout exchange, this
 * replica's own queue and binding, and the listener container factory
 * for it.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Push fanout topology (DP10): every replica binds its own queue to the
 * {@value #PUSH_EXCHANGE} fanout exchange, so a push published by any
 * replica reaches all of them, the publisher included.
 *
 * <p>The queue is an {@link AnonymousQueue}: a unique generated name per
 * replica, non-durable, exclusive and auto-delete, so it goes away with
 * the replica's connection and the broker never holds pushes for a
 * replica that is gone. It is declared through the same
 * {@link Declarables} flow as the domain topology
 * ({@link RabbitMqTopologyInitializer}), and Spring's {@code RabbitAdmin}
 * declares it again on every reconnect.
 *
 * <p>The exchange name is this service's own (only this service
 * publishes and consumes on it), so it lives here, not in foc-contracts,
 * whose {@code EventContracts} holds cross-service names only (D15).
 *
 * <p>The listener has its own container factory: AUTO acknowledgement
 * (the service-wide {@code acknowledge-mode: manual} is for the domain
 * event listener, which acks after its commit), a rejected push dropped
 * rather than requeued, and a plain converter — the push body is not a
 * domain event, so the registry-driven {@link DomainEventMessageConverter}
 * must not see it.
 */
@Configuration
class NotificationPushFanoutTopology {

	/** Fanout exchange owned by this service (DP10). */
	static final String PUSH_EXCHANGE = "notification.push";

	/** The container factory {@link RabbitMqUserPushFanout}'s listener uses. */
	static final String PUSH_LISTENER_CONTAINER_FACTORY = "notificationPushListenerContainerFactory";

	@Bean
	AnonymousQueue notificationPushQueue() {
		return new AnonymousQueue();
	}

	@Bean
	Declarables notificationPushTopology(AnonymousQueue notificationPushQueue) {
		FanoutExchange exchange = new FanoutExchange(PUSH_EXCHANGE, true, false);
		return new Declarables(exchange, notificationPushQueue,
				BindingBuilder.bind(notificationPushQueue).to(exchange));
	}

	@Bean(name = PUSH_LISTENER_CONTAINER_FACTORY)
	SimpleRabbitListenerContainerFactory notificationPushListenerContainerFactory(
			SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		// spring.rabbitmq.listener.simple.* first (auto-startup, prefetch),
		// then the push-specific overrides
		configurer.configure(factory, connectionFactory);
		factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
		factory.setDefaultRequeueRejected(false);
		factory.setMessageConverter(new SimpleMessageConverter());
		return factory;
	}
}
