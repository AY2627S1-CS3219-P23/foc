/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: consumer-side contract test (successor to issue #63's
 * envelope test) for the Method-B refactor (D16-D19): every
 * registry-cataloged event's canonical fixture must bind to its
 * record, and the tolerant-reader rule for unknown *fields* holds
 * (F1.3). Unknown *types* are converter-level failures now — see
 * DomainEventMessageConverterTest.
 * 2026-09-20: order→request event vocabulary rename applied (author
 * decision D22, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 * 2026-10-09, Claude Code (Opus 5.5), PR #163 review (Leong Wei Zhi): the catalog
 * now spans the request and credit domains, so the producer and
 * requestId checks go through each event's domain marker, and
 * parties is required present rather than non-empty (credit events
 * notify no one).
 * 2026-10-18, Claude Code (Opus 5.5), issue #165 (Leong Wei Zhi's
 * suggestion): a request event's parties is still required non-empty;
 * only credit events may have none.
 * Author review: Ryan Ang, pending pull request review.
 * 2026-10-22, Claude Code (Opus 5.5), PR #243 review follow-up: a
 * fixture's fixed value (category, reasonCode) must be in its record's
 * value set, so a typo cannot become the documented example. Author
 * review: Ryan Ang, pending pull request review.
 * 2026-10-23, Claude Code (Opus 5.5), PR #244 review: the value is read
 * from the component named for each record, not from whichever String
 * component holds one. Author review: Ryan Ang, pending pull request
 * review.
 * 2026-10-26, Claude Code (Opus 5.5), issue #237: the two N3 dispute
 * fixtures bind their own fields and name both parties. Author review:
 * Ryan Ang, pending pull request review.
 * 2026-10-26, Claude Code (Opus 5.5), PR #260 review: both registry tests
 * read their fixture through readFixture (one path rule, one not-null
 * check), and the resolution's amounts are distinct. Author review: Ryan
 * Ang, pending pull request review. Same review, second round: the
 * split is checked against the request-submitted fixture's reward (DSP-8).
 * 2026-10-28, Claude Code (Opus 5), issue #249: the producer and
 * requestId helpers cover the rating domain, and the parties rule is
 * stated the way it holds — only credit events notify no one, so the
 * rating fixture must name its ratee (scope decisions on #44). Author
 * review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import foc.contracts.events.core.DomainEvent;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.credit.CreditEvent;
import foc.contracts.events.credit.CreditReservationRejected;
import foc.contracts.events.rating.RatingEvent;
import foc.contracts.events.request.RequestAccepted;
import foc.contracts.events.request.RequestDisputeResolved;
import foc.contracts.events.request.RequestDisputed;
import foc.contracts.events.request.RequestEvent;
import foc.contracts.events.request.RequestSubmitted;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

/**
 * Contract test for the typed event catalog: each
 * {@link EventTypeRegistry} entry ships a canonical fixture in the
 * foc-contracts jar at
 * {@code /contracts/<eventType dots&rarr;hyphens>.example.json}, and
 * that fixture must bind to the registered record class. Iterating the
 * registry means a newly added event is covered the moment its entry
 * exists — or fails loudly if its fixture is missing. The catalog spans
 * three producing domains (request, credit and rating), so the expected
 * producer and the request reference come from each event's domain
 * marker.
 *
 * <p>{@code @JsonTest} injects Boot's auto-configured
 * {@link ObjectMapper} — the same mapper the AMQP message converter
 * uses (D15) so the behavior proven here holds at the broker boundary.
 * Note the fixtures carry the injected {@code eventType} field the
 * records don't store (D18); binding them successfully is itself proof
 * of tolerant reading.
 */
@JsonTest
class DomainEventContractTest {

	// the component holding each value-set record's fixed value
	private static final Map<Class<?>, String> VALUE_COMPONENTS = Map.of(
			CreditReservationRejected.class, "reasonCode",
			RequestDisputed.class, "category");

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void everyCatalogedEventHasABindingFixture() throws IOException {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			DomainEvent event = readFixture(entry.eventType(), entry.eventClass());

			assertThat(event.eventType()).isEqualTo(entry.eventType());
			assertThat(event.eventId()).isNotBlank();
			assertThat(event.occurredAt()).isNotNull();
			assertThat(event.producer()).isEqualTo(expectedProducer(event));
			assertThat(event.correlationId()).isNotBlank();
			// credit events notify no one; every other event names someone
			if (event instanceof CreditEvent) {
				assertThat(event.parties()).isNotNull();
			} else {
				assertThat(event.parties()).isNotEmpty();
			}
			assertThat(requestIdOf(event)).isEqualTo("req-20260919-0042");
		}
	}

	/**
	 * A record with fixed values holds them as one public static final
	 * Set (e.g. RequestDisputed.CATEGORIES). Its fixture must carry one of
	 * them in the component VALUE_COMPONENTS names, so a misspelt value
	 * fails here rather than becoming the documented example. A new
	 * value-set record fails until it is added to that map.
	 */
	@Test
	void everyFixtureUsesAValueFromItsRecordsValueSet() throws Exception {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			Set<?> values = valueSetOf(entry.eventClass());
			if (values == null) {
				continue;
			}
			DomainEvent event = readFixture(entry.eventType(), entry.eventClass());

			String name = VALUE_COMPONENTS.get(entry.eventClass());
			assertThat(name).as("%s has a value set but no entry in VALUE_COMPONENTS",
					entry.eventClass().getSimpleName()).isNotNull();
			Object value = null;
			for (RecordComponent component : entry.eventClass().getRecordComponents()) {
				if (component.getName().equals(name)) {
					value = component.getAccessor().invoke(event);
				}
			}
			assertThat(value).as("%s: %s from %s", entry.eventType(), name, values).isIn(values);
		}
	}

	/**
	 * Issue #237: the generic test above proves the dispute fixtures bind;
	 * this pins the fields they add, so a rename in either record fails
	 * here, and both parties are named — the requester and the courier
	 * each get a notification.
	 */
	@Test
	void disputeFixturesBindTheirOwnFieldsAndNameBothParties() throws IOException {
		RequestDisputed disputed = readFixture("request.disputed", RequestDisputed.class);
		assertThat(disputed.category()).isEqualTo(RequestDisputed.WRONG_ITEM);
		assertThat(disputed.disputeNote()).isEqualTo("Got duck rice instead of chicken rice");
		assertThat(disputed.parties()).containsExactlyInAnyOrder(
				disputed.requesterId(), disputed.courierId());

		RequestDisputeResolved resolved =
				readFixture("request.dispute-resolved", RequestDisputeResolved.class);
		// distinct amounts, so a courier/requester swap fails here
		assertThat(resolved.courierAmount()).isEqualTo(0);
		assertThat(resolved.requesterAmount()).isEqualTo(2);
		assertThat(resolved.resolutionNote()).isEqualTo("Wrong item delivered; full refund to the requester");
		// the reward is in another fixture, so changing it alone fails here
		int reward = readFixture("request.submitted", RequestSubmitted.class).reward();
		assertThat(resolved.courierAmount() + resolved.requesterAmount())
				.as("DSP-8: the split adds up to the reward")
				.isEqualTo(reward);
		assertThat(resolved.parties()).containsExactlyInAnyOrder(
				resolved.requesterId(), resolved.courierId());
	}

	// the one copy of the fixture-path rule in this class (EventTypeRegistry's javadoc)
	private <T> T readFixture(String eventType, Class<T> type) throws IOException {
		String path = "/contracts/" + eventType.replace('.', '-') + ".example.json";
		try (InputStream fixture = getClass().getResourceAsStream(path)) {
			assertThat(fixture).as("fixture %s on classpath via foc-contracts jar", path).isNotNull();
			return objectMapper.readValue(fixture, type);
		}
	}

	private static Set<?> valueSetOf(Class<?> recordClass) throws IllegalAccessException {
		for (Field field : recordClass.getDeclaredFields()) {
			int modifiers = field.getModifiers();
			if (field.getType() == Set.class && Modifier.isPublic(modifiers)
					&& Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
				return (Set<?>) field.get(null);
			}
		}
		return null;
	}

	private static String expectedProducer(DomainEvent event) {
		if (event instanceof RequestEvent) {
			return "order-service";
		}
		if (event instanceof CreditEvent) {
			return "credit-service";
		}
		if (event instanceof RatingEvent) {
			return "rating-service";
		}
		throw new AssertionError(event.eventType() + " implements no domain marker interface");
	}

	private static String requestIdOf(DomainEvent event) {
		if (event instanceof RequestEvent requestEvent) {
			return requestEvent.requestId();
		}
		if (event instanceof CreditEvent creditEvent) {
			return creditEvent.requestId();
		}
		if (event instanceof RatingEvent ratingEvent) {
			return ratingEvent.requestId();
		}
		throw new AssertionError(event.eventType() + " implements no domain marker interface");
	}

	@Test
	void ignoresUnknownFields() {
		String json = """
				{
				  "eventType": "request.accepted",
				  "eventId": "e-1",
				  "occurredAt": "2026-09-19T08:30:00Z",
				  "producer": "order-service",
				  "correlationId": "c-1",
				  "parties": ["usr-req-1001", "usr-cou-2002"],
				  "requestId": "req-1",
				  "requesterId": "usr-req-1001",
				  "courierId": "usr-cou-2002",
				  "pickupLocation": "Techno Edge",
				  "dropoffLocation": "COM3-01-19",
				  "note": "hi",
				  "someFutureField": "ignored",
				  "someFutureObject": {"nested": true}
				}
				""";

		RequestAccepted event = objectMapper.readValue(json, RequestAccepted.class);

		assertThat(event.eventId()).isEqualTo("e-1");
		assertThat(event.requestId()).isEqualTo("req-1");
		assertThat(event.parties()).containsExactly("usr-req-1001", "usr-cou-2002");
	}
}
