/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: broker topology declaration for issue #62 per team decisions
 * D1, D8, D11-D15 (docs/notification-service.md).
 * Same day, Method-B refactor: fanout exchange replaced by a topic
 * exchange with an "order.#" binding (author decision D16, supersedes
 * D14); then, on the author's cleanup decision, the per-domain
 * declarations were grouped into one Declarables bean so topology
 * additions no longer touch the initializer.
 * 2026-09-20, issue #65: retry queue and DLQ added per team decisions
 * D6/D7 (values decided 2026-09-19: 10 s TTL, 3 attempts) and the
 * author's decisions of 2026-09-20: the default exchange serves as
 * the dead-letter exchange on both legs, and unconvertible messages
 * dead-letter on first rejection.
 * 2026-09-20: order→request event vocabulary rename applied (author
 * decision D22, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 * 2026-10-30, Claude Code (Fable 5), issue #255: rating-domain trio
 * (notification-service.rating-events + .retry/.dlq) bound to the
 * rating-events exchange on the exact rating.submitted key via the
 * registry (D13/D16; recipient scope decided on #44). Author review:
 * Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import foc.contracts.events.core.EventContracts;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.rating.RatingSubmitted;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Request- and rating-domain topology (D12, D13, D16): one durable
 * <strong>topic</strong> exchange per producing domain, published to
 * with routing keys from the shared {@code EventTypeRegistry} (D18),
 * and one consumer-private work-queue trio per exchange. The request
 * work queue binds {@value #REQUEST_EVENTS_BINDING_PATTERN} — this
 * service notifies on <em>every</em> request event (F1.1, F1.2), and a
 * new request event ships via a contracts release anyway, so that
 * binding never changes. The rating work queue binds the exact
 * {@code rating.submitted} key instead (issue #255): the rating domain
 * will grow events this service must not notify on, so it opts in
 * key by key (rating-service's own consumer style, no hand-written
 * strings).
 *
 * <p>Failure topology (D6/D7, F2.2/F2.3), identical per trio: the work
 * queue dead-letters to the durable DLQ, and the durable retry queue
 * parks a failed event for {@code notification.rabbitmq.retry.ttl-ms}
 * before its own dead-letter leg returns it to the work queue for
 * another attempt. The {@code <work-queue>.retry} naming is an
 * invariant {@link DomainEventsListener} relies on to derive each
 * delivery's retry queue from its consuming queue.
 * Both legs use the <em>default exchange</em> as their dead-letter
 * exchange (author decision, 2026-09-20): zero extra declarables, at
 * the cost that the routing key is rewritten to the target queue name
 * in transit — nothing is lost, since the body's {@code eventType} is
 * the original routing key (D18) and the broker's {@code x-death}
 * header records the original keys. Who routes where: the listener
 * republishes to the retry queue while attempts remain and nacks
 * without requeue when they are exhausted; a message the converter
 * cannot even convert (unknown eventType, malformed body) is rejected
 * by the container on first delivery and dead-letters straight to the
 * DLQ — retrying it could never succeed (author decision, 2026-09-20).
 *
 * <p>Each domain's declarations are grouped into one
 * {@link Declarables} bean; {@link RabbitMqTopologyInitializer}
 * declares every such group generically, so adding topology (a second
 * domain) touches only this class.
 *
 * <p>Durability is opt-in in RabbitMQ (D8, Notif NFR1.2): the exchange
 * and queues are declared durable here, and publishers must send
 * messages with delivery mode PERSISTENT — Spring AMQP's
 * {@code RabbitTemplate} default — or queued events will not survive a
 * broker restart.
 *
 * <p>Migration note: queue/exchange arguments are immutable once
 * declared — a broker volume holding the pre-refactor fanout exchange,
 * or the pre-#65 work queue (no dead-letter arguments), or a retry
 * queue declared with a different TTL, makes the declaration fail
 * ({@code PRECONDITION_FAILED}); reset the volume once — see the
 * service README.
 *
 * <p>Broker-specific code stays in this package (D11); business logic
 * must not import broker types.
 */
@Configuration
public class RabbitMqTopology {

	/**
	 * This service's work queue for the request domain; consumer-prefixed
	 * per the team naming convention (D13) and service-private, so it
	 * lives here rather than in foc-contracts (D15).
	 */
	public static final String REQUEST_EVENTS_QUEUE = "notification-service.request-events";

	/**
	 * TTL/delay queue (D6, F2.2): the listener parks a failed event
	 * here; on TTL expiry the broker returns it to the work queue.
	 */
	public static final String REQUEST_EVENTS_RETRY_QUEUE = REQUEST_EVENTS_QUEUE + ".retry";

	/**
	 * Terminal queue (D7, F2.3): events that exhausted their attempts
	 * or never converted, kept for inspection and manual re-publish.
	 */
	public static final String REQUEST_EVENTS_DEAD_LETTER_QUEUE = REQUEST_EVENTS_QUEUE + ".dlq";

	/** All request-domain events, current and future (D16). */
	static final String REQUEST_EVENTS_BINDING_PATTERN = "request.#";

	/**
	 * This service's work queue for the rating domain (issue #255);
	 * consumer-prefixed and service-private like the request trio
	 * (D13/D15).
	 */
	public static final String RATING_EVENTS_QUEUE = "notification-service.rating-events";

	/** Rating-domain TTL/delay queue (D6, F2.2), same leg shape. */
	public static final String RATING_EVENTS_RETRY_QUEUE = RATING_EVENTS_QUEUE + ".retry";

	/** Rating-domain terminal queue (D7, F2.3). */
	public static final String RATING_EVENTS_DEAD_LETTER_QUEUE = RATING_EVENTS_QUEUE + ".dlq";

	/**
	 * The broker's built-in default exchange: routes by queue name, so
	 * a dead-letter leg needs no declared exchange or binding.
	 */
	private static final String DEFAULT_EXCHANGE = "";

	@Bean
	Declarables requestEventsTopology(
			@Value("${notification.rabbitmq.retry.ttl-ms}") int retryTtlMs) {
		TopicExchange exchange = new TopicExchange(EventContracts.REQUEST_EVENTS_EXCHANGE, true, false);
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
		Binding binding = BindingBuilder.bind(workQueue).to(exchange)
				.with(REQUEST_EVENTS_BINDING_PATTERN);
		return new Declarables(exchange, workQueue, retryQueue, deadLetterQueue, binding);
	}

	/**
	 * The exchange declaration must match rating-service's (durable
	 * topic, non-auto-delete) — redeclaring with different arguments
	 * fails {@code PRECONDITION_FAILED}; the producer declares the
	 * exchange only and leaves queues and bindings to each consumer
	 * (D13, rating-service's {@code ratingEventsTopology} javadoc).
	 */
	@Bean
	Declarables ratingEventsTopology(
			@Value("${notification.rabbitmq.retry.ttl-ms}") int retryTtlMs) {
		TopicExchange exchange = new TopicExchange(EventContracts.RATING_EVENTS_EXCHANGE, true, false);
		Queue workQueue = QueueBuilder.durable(RATING_EVENTS_QUEUE)
				.deadLetterExchange(DEFAULT_EXCHANGE)
				.deadLetterRoutingKey(RATING_EVENTS_DEAD_LETTER_QUEUE)
				.build();
		Queue retryQueue = QueueBuilder.durable(RATING_EVENTS_RETRY_QUEUE)
				.ttl(retryTtlMs)
				.deadLetterExchange(DEFAULT_EXCHANGE)
				.deadLetterRoutingKey(RATING_EVENTS_QUEUE)
				.build();
		Queue deadLetterQueue = QueueBuilder.durable(RATING_EVENTS_DEAD_LETTER_QUEUE).build();
		Binding binding = BindingBuilder.bind(workQueue).to(exchange)
				.with(EventTypeRegistry.routingKeyFor(RatingSubmitted.class));
		return new Declarables(exchange, workQueue, retryQueue, deadLetterQueue, binding);
	}

}
