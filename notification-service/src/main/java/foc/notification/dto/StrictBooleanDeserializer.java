/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, PR #198 Codex review.
 * Scope: Jackson coerces "true" and 1 into a Boolean by default, so a
 * PATCH body like {"read":"true"} was answered 204 where the REST
 * contract promises 400 for a non-boolean read
 * (docs/notification-service.md "REST API contract"). This
 * deserializer accepts only JSON true/false; anything else fails the
 * read and surfaces as the handler's 400. Scoped to the one field on
 * purpose — the AMQP event path keeps Boot's default mapper semantics.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Only JSON {@code true} / {@code false} bind; "true", 1 and friends are refused, not coerced. */
public class StrictBooleanDeserializer extends ValueDeserializer<Boolean> {

	@Override
	public Boolean deserialize(JsonParser parser, DeserializationContext context) {
		JsonToken token = parser.currentToken();
		if (token == JsonToken.VALUE_TRUE) {
			return Boolean.TRUE;
		}
		if (token == JsonToken.VALUE_FALSE) {
			return Boolean.FALSE;
		}
		return context.reportInputMismatch(Boolean.class,
				"read must be the JSON literal true or false");
	}
}
