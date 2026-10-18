/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: the Order Service's broker topology, a copy of credit-service's
 * RabbitMqTopology turned around: the work queue sits on credit-events
 * and is bound to the two reservation replies (docs/credit-service.md,
 * "What the other services must build"). The work/retry/dead-letter
 * layout and its values are copied from credit-service D11 by the
 * author's decision; the queue names follow its consumer-prefixed
 * convention.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.messaging.rabbitmq;

import foc.contracts.events.core.EventContracts;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.credit.CreditReservationRejected;
import foc.contracts.events.credit.CreditReserved;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * The saga's topology on the Order Service's side: its durable work
 * queue on the {@code credit-events} exchange, bound to the two
 * reservation replies, and the {@code request-events} exchange its own
 * events go to. Both exchanges are declared here, as durable topic
 * exchanges like every other declarer's, so the service starts whether
 * or not the Credit Service has.
 *
 * <p>Failure legs, the Credit Service's layout: the work queue
 * dead-letters to the DLQ, and the retry queue holds a failed reply for
 * {@code order.rabbitmq.retry.ttl-ms} before its own dead-letter leg
 * returns it to the work queue. Both legs go through the default
 * exchange, which rewrites the routing key to the queue name; nothing
 * is lost, since the body's {@code eventType} is the original key.
 *
 * <p>Queue and exchange arguments cannot change once declared: a broker
 * that already holds the retry queue with another TTL refuses this
 * declaration (PRECONDITION_FAILED) until the queue is deleted.
 */
@Configuration
public class RabbitMqTopology {

	/** This service's work queue; consumer-prefixed and private to it. */
	public static final String CREDIT_EVENTS_QUEUE = "order-service.credit-events";

	/** Holds a failed reply for the retry delay, then returns it to the work queue. */
	public static final String CREDIT_EVENTS_RETRY_QUEUE = CREDIT_EVENTS_QUEUE + ".retry";

	/** Replies that ran out of attempts, named no known request, or never converted. */
	public static final String CREDIT_EVENTS_DEAD_LETTER_QUEUE = CREDIT_EVENTS_QUEUE + ".dlq";

	/** The replies the Order Service acts on, bound one by one. */
	static final List<Class<?>> CONSUMED_EVENTS = List.of(CreditReserved.class, CreditReservationRejected.class);

	/** The broker's default exchange routes by queue name. */
	private static final String DEFAULT_EXCHANGE = "";

	@Bean
	Declarables sagaTopology(@Value("${order.rabbitmq.retry.ttl-ms}") int retryTtlMs) {
		TopicExchange requestEvents = new TopicExchange(EventContracts.REQUEST_EVENTS_EXCHANGE, true, false);
		TopicExchange creditEvents = new TopicExchange(EventContracts.CREDIT_EVENTS_EXCHANGE, true, false);
		Queue workQueue = QueueBuilder.durable(CREDIT_EVENTS_QUEUE)
				.deadLetterExchange(DEFAULT_EXCHANGE)
				.deadLetterRoutingKey(CREDIT_EVENTS_DEAD_LETTER_QUEUE)
				.build();
		Queue retryQueue = QueueBuilder.durable(CREDIT_EVENTS_RETRY_QUEUE)
				.ttl(retryTtlMs)
				.deadLetterExchange(DEFAULT_EXCHANGE)
				.deadLetterRoutingKey(CREDIT_EVENTS_QUEUE)
				.build();
		Queue deadLetterQueue = QueueBuilder.durable(CREDIT_EVENTS_DEAD_LETTER_QUEUE).build();

		List<Declarable> declarables = new ArrayList<>(
				List.of(requestEvents, creditEvents, workQueue, retryQueue, deadLetterQueue));
		for (Class<?> event : CONSUMED_EVENTS) {
			// the key comes from the shared registry, never hand-written
			declarables.add(BindingBuilder.bind(workQueue).to(creditEvents)
					.with(EventTypeRegistry.routingKeyFor(event)));
		}
		return new Declarables(declarables);
	}

}
