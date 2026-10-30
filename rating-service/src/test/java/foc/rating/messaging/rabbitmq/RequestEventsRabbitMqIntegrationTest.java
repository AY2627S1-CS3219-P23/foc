/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: issue #251's acceptance criteria end to end through a real
 * broker, modeled on notification-service's
 * RequestEventsRabbitMqIntegrationTest (Claude Code (Fable 5),
 * 2026-09-19/20, reviewed by Leong Wei Zhi): canonical fixture replay
 * to one eligibility row, redelivery deduped behind a FIFO sentinel,
 * unknown and unconsumed types dead-lettered intact, retry/DLQ
 * discipline with attempt-header + x-death evidence, Awaitility only.
 * Rows here are keyed by requestId, not eventId, so dedupe and
 * sentinel assertions read the rateable_request table.
 * Author review: [pending]
 */
package foc.rating.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.rabbitmq.client.GetResponse;
import foc.contracts.events.core.EventContracts;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.request.RequestCompleted;
import foc.rating.repository.RateableRequestRepository;
import foc.rating.service.EventProcessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end through a real broker: raw fixture JSON (no broker type
 * headers — the wire contract as the Order Service publishes it) goes
 * to the {@code request-events} <strong>topic</strong> exchange under
 * its canonical routing key; the listener consumes, the converter
 * dispatches on the body {@code eventType}, the processor stores one
 * eligibility row, duplicates are absorbed, and other keys never reach
 * the queue.
 *
 * <p>Failure paths: a transient processing failure recovers through
 * the retry queue; a persistent one dead-letters after the attempt
 * cap, proven by the listener's attempt header on the DLQ message
 * rather than wall-clock timing; an unknown event type — and a valid
 * event of a type this listener does not take — dead-letters intact on
 * first delivery. Failures are injected by a {@code @Primary}
 * decorator around the real processor, keyed on eventId prefixes
 * ({@code poison-} always throws, {@code flaky-} throws once) — the
 * broker path stays fully real. The test yaml sets a 500 ms retry TTL
 * so cycles complete quickly.
 *
 * <p>The broker container and H2 instance are shared by all methods,
 * so assertions are requestId-scoped or baseline-relative, and each
 * method starts from a purged DLQ. The topology is declared by Boot's
 * auto-configured admin when the first connection opens (the startup
 * initializer stays off, as in every other test). Skips cleanly when
 * Docker is not available.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true",
        // Own H2 instance so this context never shares tables with the
        // cached broker-free context used by the other tests.
        "spring.datasource.url=jdbc:h2:mem:rating-it;DB_CLOSE_DELAY=-1"
})
@Testcontainers(disabledWithoutDocker = true)
class RequestEventsRabbitMqIntegrationTest {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitMq =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));

    @TestConfiguration
    static class FailureInjection {

        /**
         * Decorates the real processor (by bean name — the concrete
         * class is package-private in the service layer): every
         * {@code poison-} event fails, every {@code flaky-} event fails
         * exactly once, everything else flows through untouched.
         */
        @Bean
        @Primary
        EventProcessor failureInjectingProcessor(
                @Qualifier("ratingEligibilityProcessor") EventProcessor delegate) {
            Set<String> alreadyFailed = ConcurrentHashMap.newKeySet();
            return event -> {
                if (event.eventId().startsWith("poison-")) {
                    throw new IllegalStateException("injected persistent failure: " + event.eventId());
                }
                if (event.eventId().startsWith("flaky-") && alreadyFailed.add(event.eventId())) {
                    throw new IllegalStateException("injected transient failure: " + event.eventId());
                }
                delegate.process(event);
            };
        }
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RateableRequestRepository rateableRequests;

    @BeforeEach
    void purgeDeadLetterQueue() {
        rabbitTemplate.execute(channel ->
                channel.queuePurge(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE));
    }

    @Test
    void fixtureReplayYieldsExactlyOneRowAndRedeliveryChangesNothing() throws IOException {
        long baseline = rateableRequests.count();
        byte[] fixture = fixtureBytes("request.completed");

        // Acceptance 1: the canonical fixture, as raw JSON under its
        // canonical routing key, becomes exactly one eligibility row with
        // the fixture's parties and its occurredAt as completed_at.
        publishRawJson(fixture, "request.completed");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(rateableRequests.findById("req-20260919-0042")).hasValueSatisfying(row -> {
                    assertThat(row.getRequesterId()).isEqualTo("usr-req-1001");
                    assertThat(row.getCourierId()).isEqualTo("usr-cou-2002");
                    assertThat(row.getCompletedAt()).isEqualTo(Instant.parse("2026-09-19T09:05:00Z"));
                }));
        assertThat(rateableRequests.count()).isEqualTo(baseline + 1);

        // Topic routing: a key this queue does not bind never reaches it —
        // the body is a perfectly valid completion, so a row for it would
        // prove a routing leak, not a conversion failure.
        publishRawJson(toJson(completed("e-int-leak", "req-int-leak")), "credit.granted");

        // Acceptance 2: duplicate delivery, then a sentinel event — the
        // queue is FIFO, so once the sentinel's row exists the duplicate
        // has been fully processed, and must have changed nothing.
        publishRawJson(fixture, "request.completed");
        publishEvent(completed("e-int-sentinel", "req-int-sentinel"));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(rateableRequests.existsById("req-int-sentinel")).isTrue());
        assertThat(rateableRequests.count()).isEqualTo(baseline + 2);
        assertThat(rateableRequests.existsById("req-int-leak")).isFalse();

        awaitDrained();
        assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE)).isZero();
    }

    @Test
    void unknownAndUnconsumedTypesDeadLetterIntactOnFirstDelivery() throws IOException {
        long baseline = rateableRequests.count();

        // Acceptance 3: an unknown body eventType under the bound key is a
        // fatal conversion failure, rejected without requeue on first
        // delivery and dead-lettered straight to the DLQ — no rows, queue
        // not wedged, message kept intact for replay.
        publishRawJson("""
                {"eventType": "request.refunded", "eventId": "e-int-unknown",
                 "parties": ["usr-req-1001"]}
                """.getBytes(StandardCharsets.UTF_8), "request.completed");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE))
                        .isEqualTo(1));
        GetResponse unknown = getFromDeadLetterQueue();
        assertThat(new String(unknown.getBody(), StandardCharsets.UTF_8))
                .contains("request.refunded").contains("e-int-unknown");
        Map<String, ?> workQueueDeath = xDeathEntry(unknown,
                RabbitMqTopology.REQUEST_EVENTS_QUEUE, "rejected");
        assertThat(String.valueOf(workQueueDeath.get("routing-keys"))).contains("request.completed");

        // A valid event of a known-but-unconsumed type under the same key
        // (a producer error): converts, fails the listener's payload type,
        // equally fatal — dead-lettered intact, skipping the retry queue.
        publishRawJson(fixtureBytes("request.submitted"), "request.completed");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE))
                        .isEqualTo(1));
        GetResponse unconsumed = getFromDeadLetterQueue();
        assertThat(new String(unconsumed.getBody(), StandardCharsets.UTF_8))
                .contains("request.submitted");
        xDeathEntry(unconsumed, RabbitMqTopology.REQUEST_EVENTS_QUEUE, "rejected");

        awaitDrained();
        assertThat(rateableRequests.count()).isEqualTo(baseline);
    }

    @Test
    void transientFailureRecoversThroughRetryQueue() {
        publishEvent(completed("flaky-e-1", "req-int-flaky"));

        // The row can only exist after a full park-and-return cycle: the
        // decorator failed the first delivery, so success proves the
        // message survived the retry queue.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(rateableRequests.existsById("req-int-flaky")).isTrue());

        awaitDrained();
        assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE)).isZero();
    }

    @Test
    void persistentFailureDeadLettersAfterMaxAttempts() {
        publishEvent(completed("poison-e-1", "req-int-poison"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE))
                        .isEqualTo(1));
        assertThat(rateableRequests.existsById("req-int-poison")).isFalse();

        // Attempt evidence, stronger than any wall-clock assertion: the
        // listener's attempt header shows two republishes preceded the
        // final delivery (= the configured 3 attempts), and the broker's
        // x-death carries the retry queue's expired entry, proving the
        // park-and-return cycles really ran through the broker (its count
        // stays 1 — RabbitMQ 4 resets it on client republish, which is
        // exactly why the listener stamps its own header). The body stays
        // replayable.
        GetResponse deadLetter = getFromDeadLetterQueue();
        assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8))
                .contains("poison-e-1").contains("request.completed");
        Number attemptsStamped = (Number) deadLetter.getProps().getHeaders()
                .get(RequestEventsListener.RETRY_ATTEMPTS_HEADER);
        assertThat(attemptsStamped.longValue()).isEqualTo(2);
        xDeathEntry(deadLetter, RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE, "expired");

        awaitDrained();
    }

    /** The canonical fixture shipped in the foc-contracts jar for one event type. */
    private static byte[] fixtureBytes(String eventType) throws IOException {
        String path = "/contracts/" + eventType.replace('.', '-') + ".example.json";
        try (InputStream fixture = RequestEventsRabbitMqIntegrationTest.class.getResourceAsStream(path)) {
            assertThat(fixture).as("fixture %s", path).isNotNull();
            return fixture.readAllBytes();
        }
    }

    private void publishRawJson(byte[] body, String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(EventContracts.REQUEST_EVENTS_EXCHANGE, routingKey,
                new Message(body, properties));
    }

    /** Producer convention (D18): routing key from the registry, by class. */
    private void publishEvent(RequestCompleted event) {
        rabbitTemplate.convertAndSend(EventContracts.REQUEST_EVENTS_EXCHANGE,
                EventTypeRegistry.routingKeyFor(event.getClass()), event);
    }

    /** The converter's own wire shape, for raw publishes under a foreign key. */
    private byte[] toJson(RequestCompleted event) {
        return rabbitTemplate.getMessageConverter().toMessage(event, new MessageProperties()).getBody();
    }

    private static RequestCompleted completed(String eventId, String requestId) {
        return new RequestCompleted(eventId, Instant.parse("2026-09-19T09:05:00Z"),
                "order-service", "c-int", List.of("usr-req-1001"), requestId,
                "usr-req-1001", "usr-cou-2002", "Techno Edge", "COM3-01-19", "integration");
    }

    private long readyMessageCount(String queue) {
        Integer count = rabbitTemplate.execute(channel ->
                channel.queueDeclarePassive(queue).getMessageCount());
        return count == null ? -1 : count;
    }

    /** Every in-flight message settled: work and retry queues at zero. */
    private void awaitDrained() {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_QUEUE)).isZero();
            assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE)).isZero();
        });
    }

    private GetResponse getFromDeadLetterQueue() {
        GetResponse response = rabbitTemplate.execute(channel ->
                channel.basicGet(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE, true));
        assertThat(response).isNotNull();
        return response;
    }

    /**
     * The broker's x-death header entry for one queue/reason pair —
     * values arrive as client types (LongString, Long), so callers
     * compare via String.valueOf/Number.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, ?> xDeathEntry(GetResponse message, String queue, String reason) {
        List<Map<String, ?>> deaths =
                (List<Map<String, ?>>) message.getProps().getHeaders().get("x-death");
        assertThat(deaths).isNotNull();
        return deaths.stream()
                .filter(death -> queue.equals(String.valueOf(death.get("queue")))
                        && reason.equals(String.valueOf(death.get("reason"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no x-death entry for " + queue + "/" + reason + " in " + deaths));
    }
}
