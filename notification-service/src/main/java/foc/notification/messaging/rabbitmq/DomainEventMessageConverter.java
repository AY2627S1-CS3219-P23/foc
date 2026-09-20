/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: typed event message converter for the Method-B refactor per
 * team decisions D11 (body-only, broker types confined to this
 * package), D15 (Boot's auto-configured Jackson mapper) and D16-D19
 * (docs/notification-service.md): dispatch on the body's canonical
 * eventType via the shared registry, never on __TypeId__ headers.
 * Same day, PR #75 review: reject events with missing required
 * fields (per-field nullability from the contracts' @Nullable
 * marker) so incomplete bodies fail fatally here instead of reaching
 * the processor.
 * 2026-09-20: the schemaVersion gate added on that review was
 * removed with the field itself (author decision — breaking changes
 * ship as new event types instead).
 * 2026-09-20, issue #65: javadoc only — conversion rejects now
 * dead-letter instead of being dropped. Same day, PR #79 Copilot
 * review: explicit empty-body guard so a null/empty body is a fatal
 * conversion failure (dead-lettered) rather than a mapper-dependent
 * error outside the fatal classification.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import foc.contracts.events.DomainEvent;
import foc.contracts.events.EventTypeRegistry;
import foc.contracts.events.Nullable;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Converts between typed {@link DomainEvent} records and JSON message
 * bodies. Inbound, it dispatches on the body's {@code eventType} field
 * — the canonical identity (D18); the routing key is transport-only —
 * resolving the record class through {@link EventTypeRegistry} and
 * binding the whole body to it (tolerant of unknown fields via Boot's
 * mapper, F1.3). Outbound, it injects the derived {@code eventType}
 * into the JSON, since the records deliberately don't store it.
 *
 * <p>Every inbound failure — malformed JSON, missing or unknown
 * {@code eventType} (which is also how a breaking contract change,
 * shipped as a new event type, presents to a not-yet-upgraded
 * consumer), binding errors, or a missing required field (components
 * not marked {@code @Nullable} in the contract) — surfaces as the
 * AMQP {@link MessageConversionException}, which the listener
 * container's default error handler classifies as fatal and rejects
 * without requeue: the work queue's dead-letter leg files the message
 * in the DLQ on first delivery, intact and replayable after a
 * consumer upgrade (D7) — it skips the retry queue, since retrying a
 * message that cannot convert could never succeed (author decision,
 * 2026-09-20).
 */
class DomainEventMessageConverter implements MessageConverter {

	static final String EVENT_TYPE_FIELD = "eventType";

	private final JsonMapper jsonMapper;

	DomainEventMessageConverter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public Message toMessage(Object object, MessageProperties messageProperties)
			throws MessageConversionException {
		if (!(object instanceof DomainEvent event)) {
			throw new MessageConversionException(
					"only DomainEvent payloads are supported, got: " + object.getClass().getName());
		}
		byte[] body;
		try {
			ObjectNode tree = (ObjectNode) jsonMapper.valueToTree(event);
			tree.put(EVENT_TYPE_FIELD, event.eventType());
			body = jsonMapper.writeValueAsBytes(tree);
		} catch (JacksonException ex) {
			throw new MessageConversionException("failed to serialize " + event.eventType(), ex);
		}
		messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
		messageProperties.setContentLength(body.length);
		return new Message(body, messageProperties);
	}

	@Override
	public Object fromMessage(Message message) throws MessageConversionException {
		byte[] body = message.getBody();
		if (body == null || body.length == 0) {
			throw new MessageConversionException("empty message body");
		}
		JsonNode tree;
		try {
			tree = jsonMapper.readTree(body);
		} catch (JacksonException ex) {
			throw new MessageConversionException("malformed JSON body", ex);
		}
		JsonNode typeNode = tree.path(EVENT_TYPE_FIELD);
		if (!typeNode.isString()) {
			throw new MessageConversionException("missing or non-string eventType field");
		}
		String eventType = typeNode.stringValue();
		EventTypeRegistry.Entry entry = EventTypeRegistry.entryFor(eventType)
				.orElseThrow(() -> new MessageConversionException("unknown eventType: " + eventType));
		DomainEvent event;
		try {
			event = jsonMapper.treeToValue(tree, entry.eventClass());
		} catch (JacksonException ex) {
			throw new MessageConversionException(
					"body does not bind to " + entry.eventClass().getSimpleName(), ex);
		}
		requireCompleteEvent(event);
		return event;
	}

	/**
	 * Binding succeeds even when reference-type components are absent
	 * (Jackson supplies null), so completeness is enforced here: every
	 * component not marked {@link Nullable} in the contract must be
	 * present, or the message is fatally rejected instead of handing
	 * the processor an event that would fail — and be requeued —
	 * forever.
	 */
	private static void requireCompleteEvent(DomainEvent event) {
		for (RecordComponent component : event.getClass().getRecordComponents()) {
			if (component.isAnnotationPresent(Nullable.class)) {
				continue;
			}
			Object value;
			try {
				value = component.getAccessor().invoke(event);
			} catch (ReflectiveOperationException ex) {
				throw new MessageConversionException(
						"cannot read component " + component.getName() + " of " + event.eventType(), ex);
			}
			if (value == null) {
				throw new MessageConversionException(
						"missing required field '" + component.getName() + "' for " + event.eventType());
			}
		}
	}

}
