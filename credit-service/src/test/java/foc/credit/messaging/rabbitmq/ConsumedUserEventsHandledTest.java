/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-29.
 * Scope: PR #268 review: ConsumedEventsHandledTest's check repeated for
 * the user-events queue, tying CONSUMED_USER_EVENTS to the arms of
 * UserEventHandler.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import foc.contracts.events.user.UserEvent;
import foc.credit.service.AccountResult;
import foc.credit.service.CreditOperations;
import foc.credit.service.UserEventHandler;

/**
 * Every event bound to the user-events queue has an arm in the handler.
 * An event bound without an arm would be acknowledged and lost: the
 * handler's default arm takes it.
 */
class ConsumedUserEventsHandledTest {

	static List<Class<?>> consumedUserEvents() {
		return RabbitMqTopology.CONSUMED_USER_EVENTS;
	}

	@ParameterizedTest
	@MethodSource("consumedUserEvents")
	void everyBoundEventReachesAnOperation(Class<?> eventType) {
		// answers each operation with a result, so the handler runs through
		CreditOperations operations = mock(CreditOperations.class, invocation ->
				invocation.getMethod().getReturnType() == AccountResult.class ? AccountResult.DUPLICATE : null);
		UserEventHandler handler = new UserEventHandler(operations);
		UserEvent event = (UserEvent) mock(eventType);
		when(event.userId()).thenReturn("42");

		handler.handle(event);

		assertThat(mockingDetails(operations).getInvocations())
				.as("%s is bound to the user-events queue but the handler has no arm for it",
						eventType.getSimpleName())
				.isNotEmpty();
	}
}
