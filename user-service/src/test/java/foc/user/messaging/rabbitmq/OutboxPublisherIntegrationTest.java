/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-10-28
Scope: issue #267 review: the outbox publisher against a real broker,
       after credit-service's OutboxPublisherIntegrationTest. With nothing
       declared at startup and no credit-service queue bound yet, an event
       is returned, stays unsent and is retried; once a queue is bound it
       is delivered with the wire body credit-service reads.
       Same day, PR review: credit-service's own declaration of the
       exchange is replayed against the one user-service made, which the
       broker refuses if the two differ.
Author review: Ryan Ang, pending pull request review.
*/

package foc.user.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import foc.contracts.events.core.EventContracts;
import foc.user.PostgresTestContainer;
import foc.user.entity.OutboxEvent;
import foc.user.repository.OutboxEventRepository;
import foc.user.service.EventOutbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The publisher polls on its own schedule here (every 200 ms), and a
 * returned event is due again after 1 s rather than the deployed 30 s.
 * credit-service's queue is stood in for by one the test binds, or
 * leaves unbound, itself. The context is closed afterwards: left cached,
 * its publisher would go on polling the shared database while the other
 * test classes run.
 */
@SpringBootTest(properties = {
    "user.outbox.publisher.enabled=true",
    "user.outbox.publisher.poll-interval-ms=200",
    "user.outbox.publisher.unroutable-retry-delay-ms=1000"
})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
class OutboxPublisherIntegrationTest extends PostgresTestContainer {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String CREDIT_QUEUE = "test.credit-service.user-events";

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitMq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));

    @Autowired
    private EventOutbox outbox;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void noQueueBoundAndAnEmptyOutbox() {
        amqpAdmin.deleteQueue(CREDIT_QUEUE);
        outboxEvents.deleteAll();
    }

    @Test
    void anEventPublishedBeforeCreditServiceBindsItsQueueIsRetriedUntilItIsDelivered() {
        transaction.executeWithoutResult(status -> outbox.userCreated(42L));

        // the exchange exists (declared on connect), but no queue takes the
        // event: returned, kept unsent and put off
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(outboxEvents.findAll())
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getSentAt()).isNull();
                assertThat(event.getNextAttemptAt()).isNotNull();
            }));

        // credit-service starts: it declares the exchange as its
        // RabbitMqTopology does, which the broker refuses
        // (PRECONDITION_FAILED) unless it matches user-service's declaration
        amqpAdmin.declareExchange(new TopicExchange(EventContracts.USER_EVENTS_EXCHANGE, true, false));
        // and binds its queue
        // durable: RabbitMQ 4 refuses a transient, non-exclusive queue
        Queue queue = new Queue(CREDIT_QUEUE, true, false, false);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(new Binding(CREDIT_QUEUE, Binding.DestinationType.QUEUE,
            EventContracts.USER_EVENTS_EXCHANGE, "user.#", null));

        await().atMost(TIMEOUT).untilAsserted(() ->
            assertThat(outboxEvents.findAll()).extracting(OutboxEvent::getSentAt).doesNotContainNull());
        Message delivered = rabbitTemplate.receive(CREDIT_QUEUE, TIMEOUT.toMillis());
        assertThat(delivered).isNotNull();
        assertThat(delivered.getMessageProperties().getReceivedRoutingKey()).isEqualTo("user.created");
        JsonNode body = jsonMapper.readTree(new String(delivered.getBody(), StandardCharsets.UTF_8));
        assertThat(body.path("eventType").stringValue()).isEqualTo("user.created");
        assertThat(body.path("userId").stringValue()).isEqualTo("42");
        // a user's events share the user's id as their correlationId
        assertThat(body.path("correlationId").stringValue()).isEqualTo("42");
        assertThat(body.path("producer").stringValue()).isEqualTo("user-service");
        assertThat(body.path("parties").isEmpty()).isTrue();
    }
}
