/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the Credit Service's broker topology, following
 * notification-service's RabbitMqTopology. The queue names, the four
 * bound keys, declaring both exchanges and the retry values are the
 * author's decisions (docs/credit-service.md D11).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import foc.contracts.events.core.EventContracts;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.request.RequestCancelled;
import foc.contracts.events.request.RequestCompleted;
import foc.contracts.events.request.RequestExpired;
import foc.contracts.events.request.RequestSubmitted;

/**
 * The saga's topology (docs/credit-service.md D7, D11): this service's
 * durable work queue on the {@code request-events} exchange, bound to
 * the four events it acts on, and the {@code credit-events} exchange
 * its replies go to. Both exchanges are declared here, as durable topic
 * exchanges like every other declarer's, so the service starts whether
 * or not the Order Service has.
 *
 * <p>Failure legs, the Notification Service's layout: the work queue
 * dead-letters to the DLQ, and the retry queue holds a failed event for
 * {@code credit.rabbitmq.retry.ttl-ms} before its own dead-letter leg
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
	public static final String REQUEST_EVENTS_QUEUE = "credit-service.request-events";

	/** Holds a failed event for the retry delay, then returns it to the work queue. */
	public static final String REQUEST_EVENTS_RETRY_QUEUE = REQUEST_EVENTS_QUEUE + ".retry";

	/** Events that ran out of attempts, conflicted with their record, or never converted. */
	public static final String REQUEST_EVENTS_DEAD_LETTER_QUEUE = REQUEST_EVENTS_QUEUE + ".dlq";

	/**
	 * The events the Credit Service acts on. Bound one by one rather than
	 * with {@code request.#}, so no other request event reaches the queue.
	 */
	static final List<Class<?>> CONSUMED_EVENTS = List.of(
			RequestSubmitted.class, RequestCompleted.class, RequestCancelled.class, RequestExpired.class);

	/** The broker's default exchange routes by queue name. */
	private static final String DEFAULT_EXCHANGE = "";

	@Bean
	Declarables sagaTopology(@Value("${credit.rabbitmq.retry.ttl-ms}") int retryTtlMs) {
		TopicExchange requestEvents = new TopicExchange(EventContracts.REQUEST_EVENTS_EXCHANGE, true, false);
		TopicExchange creditEvents = new TopicExchange(EventContracts.CREDIT_EVENTS_EXCHANGE, true, false);
		Queue workQueue = QueueBuilder.durable(REQUEST_EVENTS_QUEUE)
				.deadLetterExchange(DEFAULT_EXCHANGE)
				.deadLetterRoutingKey(REQUEST_EVENTS_DEAD_LETTER_QUEUE)
				.build();
		Queue retryQueue = QueueBuilder.durable(REQUEST_EVENTS_RETRY_QUEUE)
				.ttl(retryTtlMs)
				.deadLetterExchange(DEFAULT_EXCHANGE)
				.deadLetterRoutingKey(REQUEST_EVENTS_QUEUE)
				.build();
		Queue deadLetterQueue = QueueBuilder.durable(REQUEST_EVENTS_DEAD_LETTER_QUEUE).build();

		List<Declarable> declarables = new ArrayList<>(
				List.of(requestEvents, creditEvents, workQueue, retryQueue, deadLetterQueue));
		for (Class<?> event : CONSUMED_EVENTS) {
			// the key comes from the shared registry, never hand-written
			declarables.add(BindingBuilder.bind(workQueue).to(requestEvents)
					.with(EventTypeRegistry.routingKeyFor(event)));
		}
		return new Declarables(declarables);
	}

}
