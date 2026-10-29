/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-10-28
Scope: issue #267 (team decision USR-1): the User Service's broker
       topology, the producer half of order-service's RabbitMqTopology:
       only the user-events exchange its events go to. The queue is the
       consumer's (credit-service), as every consumer declares its own.
       Same day, PR review (team decision): nothing declares it at startup
       any more, so the service starts without a broker; Spring AMQP's
       RabbitAdmin declares it when the publisher first connects.
Author review: Ryan Ang, pending pull request review.
*/

package foc.user.messaging.rabbitmq;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import foc.contracts.events.core.EventContracts;

/**
 * The {@code user-events} exchange, a durable topic exchange as every
 * other declarer of it has it. No startup runner declares it: login and
 * sign-up must not wait on the broker, and the outbox holds the events
 * meanwhile. Boot's {@code RabbitAdmin} declares this bean whenever a
 * connection opens, so the exchange exists before the first publish
 * whether or not the Credit Service has run; until that service binds
 * its queue, a publish is returned as unroutable and retried.
 */
@Configuration
public class RabbitMqTopology {

    @Bean
    Declarables userEventsTopology() {
        return new Declarables(new TopicExchange(EventContracts.USER_EVENTS_EXCHANGE, true, false));
    }
}
