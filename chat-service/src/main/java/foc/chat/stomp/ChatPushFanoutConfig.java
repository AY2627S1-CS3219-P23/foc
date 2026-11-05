/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (DP10).
 * Scope: cross-replica STOMP fanout over AMQP (team decision, Leong Wei
 * Zhi via options Q&A): the chat.push fanout exchange, this replica's
 * own queue and binding, and the push listener's container factory,
 * following notification-service's NotificationPushFanoutTopology.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every replica binds its own queue to the {@value #PUSH_EXCHANGE} fanout
 * exchange, so a push published by any replica reaches all of them, the
 * publisher included. The queue is an {@link AnonymousQueue} (a unique
 * name per replica, non-durable, exclusive, auto-delete): it goes away
 * with the replica's connection. Spring's {@code RabbitAdmin} declares
 * the three beans when the connection opens, and again on every
 * reconnect.
 *
 * <p>The exchange is this service's own (only chat-service publishes and
 * consumes on it), so its name lives here rather than in foc-contracts.
 * The push listener acknowledges automatically and drops what it cannot
 * handle instead of requeueing it: a push is fire-and-forget, and history
 * is the catch-up path.
 */
@Configuration
class ChatPushFanoutConfig {

    static final String PUSH_EXCHANGE = "chat.push";

    static final String PUSH_LISTENER_CONTAINER_FACTORY = "chatPushListenerContainerFactory";

    @Bean
    FanoutExchange chatPushExchange() {
        return new FanoutExchange(PUSH_EXCHANGE, true, false);
    }

    @Bean
    AnonymousQueue chatPushQueue() {
        return new AnonymousQueue();
    }

    @Bean
    Binding chatPushBinding(FanoutExchange chatPushExchange, AnonymousQueue chatPushQueue) {
        return BindingBuilder.bind(chatPushQueue).to(chatPushExchange);
    }

    @Bean(name = PUSH_LISTENER_CONTAINER_FACTORY)
    SimpleRabbitListenerContainerFactory chatPushListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        // spring.rabbitmq.listener.simple.* first (auto-startup, prefetch),
        // then the push-specific settings
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        // the listener reads the raw body itself
        factory.setMessageConverter(new SimpleMessageConverter());
        return factory;
    }
}
