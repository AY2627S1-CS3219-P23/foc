/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the copied converter as the application wires it, over Boot's
 * JSON mapper: it reads the shared request fixture, refuses a fractional
 * reward (the author's decision; application.yaml's
 * accept-float-as-int), refuses an incomplete or unknown event, and
 * injects eventType outbound.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import foc.contracts.events.credit.CreditReserved;
import foc.contracts.events.request.RequestSubmitted;
import foc.credit.PostgresTestContainer;

@SpringBootTest
class DomainEventMessageConverterTest extends PostgresTestContainer {

	@Autowired
	private DomainEventMessageConverter converter;

	@Autowired
	private JsonMapper jsonMapper;

	/** The shared request.submitted fixture, changed by the caller. */
	private Message submitted(Consumer<ObjectNode> change) throws IOException {
		try (InputStream fixture = getClass().getResourceAsStream("/contracts/request-submitted.example.json")) {
			ObjectNode body = (ObjectNode) jsonMapper.readTree(fixture);
			change.accept(body);
			return new Message(jsonMapper.writeValueAsBytes(body), new MessageProperties());
		}
	}

	@Test
	void readsTheSharedFixture() throws IOException {
		Object event = converter.fromMessage(submitted(body -> body.put("reward", 3)));

		assertThat(event).isInstanceOfSatisfying(RequestSubmitted.class, submitted -> {
			assertThat(submitted.reward()).isEqualTo(3);
			assertThat(submitted.requestId()).isNotBlank();
			assertThat(submitted.requesterId()).isNotBlank();
		});
	}

	@Test
	void refusesAFractionalReward() throws IOException {
		Message fractional = submitted(body -> body.put("reward", 1.7));

		assertThatExceptionOfType(MessageConversionException.class)
				.isThrownBy(() -> converter.fromMessage(fractional));
	}

	@Test
	void refusesAMissingReward() throws IOException {
		Message incomplete = submitted(body -> body.remove("reward"));

		assertThatExceptionOfType(MessageConversionException.class)
				.isThrownBy(() -> converter.fromMessage(incomplete))
				.withMessageContaining("reward");
	}

	@Test
	void refusesAnUnknownEventType() throws IOException {
		Message unknown = submitted(body -> body.put("eventType", "request.teleported"));

		assertThatExceptionOfType(MessageConversionException.class)
				.isThrownBy(() -> converter.fromMessage(unknown));
	}

	@Test
	void injectsTheEventTypeOutbound() {
		CreditReserved reply = new CreditReserved("evt-1", Instant.parse("2026-10-10T08:00:00Z"),
				"credit-service", "corr-1", List.of(), "r-1", "u-req", 3);

		Message message = converter.toMessage(reply, new MessageProperties());

		assertThat(jsonMapper.readTree(message.getBody()).path("eventType").stringValue())
				.isEqualTo("credit.reserved");
	}
}
