/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: typed event message converter for the Method-B refactor per
 * team decisions D11 (body-only, broker types confined to this
 * package), D15 (Boot's auto-configured Jackson mapper) and D16-D19
 * (docs/notification-service.md): dispatch on the body's canonical
 * eventType via the shared registry, never on __TypeId__ headers.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import foc.contracts.events.DomainEvent;
import foc.contracts.events.EventTypeRegistry;
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
 * {@code eventType}, binding errors — surfaces as the AMQP
 * {@link MessageConversionException}, which the listener container's
 * default error handler classifies as fatal and rejects without
 * requeue: until #65's dead-letter topology lands, such messages are
 * dropped (they never had a valid consumer); afterwards the same
 * rejection dead-letters them for replay.
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
		JsonNode tree;
		try {
			tree = jsonMapper.readTree(message.getBody());
		} catch (JacksonException ex) {
			throw new MessageConversionException("malformed JSON body", ex);
		}
		JsonNode typeNode = tree.path(EVENT_TYPE_FIELD);
		if (!typeNode.isString()) {
			throw new MessageConversionException("missing or non-string eventType field");
		}
		String eventType = typeNode.stringValue();
		Class<? extends DomainEvent> eventClass = EventTypeRegistry.classFor(eventType)
				.orElseThrow(() -> new MessageConversionException("unknown eventType: " + eventType));
		try {
			return jsonMapper.treeToValue(tree, eventClass);
		} catch (JacksonException ex) {
			throw new MessageConversionException("body does not bind to " + eventClass.getSimpleName(), ex);
		}
	}

}
