/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: PR #168 review (Leong Wei Zhi): a test tying the events the
 * topology binds to the events the handler acts on, placed and shaped
 * as the team decided (this package, since CONSUMED_EVENTS is
 * package-private; one instance per class against mocked operations).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import foc.contracts.events.request.RequestEvent;
import foc.credit.service.CreditOperations;
import foc.credit.service.ReplyOutbox;
import foc.credit.service.RequestEventHandler;
import foc.credit.service.ReserveResult;
import foc.credit.service.SettleResult;

/**
 * Every event bound to the work queue has an arm in the handler. The
 * two are separate lists, and an event bound without an arm would be
 * acknowledged and lost: the handler's default arm takes it.
 */
class ConsumedEventsHandledTest {

	static List<Class<?>> consumedEvents() {
		return RabbitMqTopology.CONSUMED_EVENTS;
	}

	@ParameterizedTest
	@MethodSource("consumedEvents")
	void everyBoundEventReachesAnOperation(Class<?> eventType) {
		// answers each operation with a result, so the handler runs through
		CreditOperations operations = mock(CreditOperations.class, invocation -> {
			Class<?> returned = invocation.getMethod().getReturnType();
			if (returned == ReserveResult.class) {
				return ReserveResult.DUPLICATE;
			}
			return returned == SettleResult.class ? SettleResult.DUPLICATE : null;
		});
		RequestEventHandler handler = new RequestEventHandler(operations, mock(ReplyOutbox.class));

		handler.handle((RequestEvent) mock(eventType));

		assertThat(mockingDetails(operations).getInvocations())
				.as("%s is bound to the work queue but the handler has no arm for it", eventType.getSimpleName())
				.isNotEmpty();
	}
}
