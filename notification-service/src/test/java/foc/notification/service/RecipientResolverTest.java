/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-04.
 * Scope: pins the D34 recipient rule per event type against the
 * canonical fixtures, and makes a rule mandatory for every registered
 * request/rating event (the check DomainEventContractTest used to make
 * on the removed parties field). Author review: Leong Wei Zhi, pending
 * pull request review.
 * PR #287 review (@claude bot, Copilot): a null-courier case and a
 * pre-D34 accepted-expiry body pinning the accepted rollout limitation.
 * PR #287 review (@Sinnez1): the null-courier case now expects a loud
 * failure on the events that require one; a type without a rule fails
 * loudly too.
 * 2026-11-04, follow-up to PR #287 (author's call): credit and user
 * events are refused like any unmapped type, no longer ignored.
 * 2026-11-08, Claude Code (Opus 5.5), Credit NFR1.1 (PR #313): the three
 * credit-change events have rules (team decision); the saga's two
 * replies and the user events are still refused. Author review: Ryan
 * Ang, pending pull request review.
 */
package foc.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import foc.contracts.events.core.DomainEvent;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.rating.RatingSubmitted;
import foc.contracts.events.request.RequestAccepted;
import foc.contracts.events.request.RequestDropped;
import foc.contracts.events.request.RequestExpired;
import foc.contracts.events.user.UserEvent;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

/**
 * The recipient rule (D34) over the canonical fixtures shipped in the
 * foc-contracts jar — the same bodies the broker delivers, bound by
 * Boot's auto-configured mapper as at the AMQP boundary (D15).
 */
@JsonTest
class RecipientResolverTest {

	private static final String REQUESTER = "usr-req-1001";
	private static final String COURIER = "usr-cou-2002";

	// the saga's credit replies, which this service does not bind
	private static final List<String> SAGA_REPLIES = List.of("credit.reserved", "credit.reservation-rejected");

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void requesterOnlyEvents() throws IOException {
		for (String type : List.of("request.submitted", "request.created", "request.rejected",
				"request.cancelled", "request.courier-arrived", "request.dropped", "credit.released")) {
			assertThat(RecipientResolver.recipientsOf(readFixture(type))).as(type)
					.containsExactly(REQUESTER);
		}
	}

	@Test
	void requesterAndCourierEvents() throws IOException {
		for (String type : List.of("request.accepted", "request.collected", "request.completed",
				"request.disputed", "request.dispute-resolved", "credit.transferred",
				"credit.dispute-settled")) {
			assertThat(RecipientResolver.recipientsOf(readFixture(type))).as(type)
					.containsExactly(REQUESTER, COURIER);
		}
	}

	// The drop removes the courier as a party (D33 prunes their rows); they
	// are named so the prune can find them, not so they are told.
	@Test
	void droppedNamesTheCourierButDoesNotTellThem() throws IOException {
		RequestDropped dropped = (RequestDropped) readFixture("request.dropped");

		assertThat(dropped.courierId()).isEqualTo(COURIER);
		assertThat(RecipientResolver.recipientsOf(dropped)).containsExactly(REQUESTER);
	}

	@Test
	void expiredWhileOpenTellsTheRequesterAlone() throws IOException {
		RequestExpired expired = (RequestExpired) readFixture("request.expired");

		assertThat(expired.courierId()).isNull();
		assertThat(RecipientResolver.recipientsOf(expired)).containsExactly(REQUESTER);
	}

	@Test
	void expiredAfterAcceptanceAlsoTellsTheCourier() {
		RequestExpired expired = new RequestExpired("e-exp", Instant.parse("2026-09-19T09:00:00Z"),
				"order-service", "c-1", "req-1", REQUESTER, COURIER, "Techno Edge", "COM3-01-19", null);

		assertThat(RecipientResolver.recipientsOf(expired)).containsExactly(REQUESTER, COURIER);
	}

	// A required courier that is missing is a contract violation: the
	// converter dead-letters such a body (D21), and the resolver refuses it
	// too rather than quietly telling the requester alone (PR #287 review).
	@Test
	void missingCourierOnATwoPartyEventFailsLoudly() {
		RequestAccepted accepted = new RequestAccepted("e-acc", Instant.parse("2026-09-19T08:30:00Z"),
				"order-service", "c-1", "req-1", REQUESTER, null, "Techno Edge", "COM3-01-19", null);

		assertThatThrownBy(() -> RecipientResolver.recipientsOf(accepted))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("request.accepted").hasMessageContaining("e-acc");
	}

	// A registered type with no rule must not be swallowed: the processor
	// resolves before its first write, so this throw dead-letters the
	// message for replay once the rule exists (PR #287 review).
	@Test
	void aTypeWithoutARuleFailsLoudly() {
		record Unmapped(String eventId, Instant occurredAt, String producer, String correlationId)
				implements DomainEvent {
			@Override
			public String eventType() {
				return "request.unmapped";
			}
		}

		assertThatThrownBy(() -> RecipientResolver.recipientsOf(
				new Unmapped("e-new", Instant.parse("2026-09-19T08:30:00Z"), "order-service", "c-1")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("request.unmapped").hasMessageContaining("e-new");
	}

	// Accepted rollout limitation (D34): an expiry published before D34 for
	// an accepted request named its courier only in parties, which this
	// service no longer reads. It binds with courierId null and tells the
	// requester alone — hence "drain the queues before deploying".
	@Test
	void legacyAcceptedExpiryBodyTellsTheRequesterAlone() throws IOException {
		String legacy = """
				{"eventType": "request.expired", "eventId": "e-legacy",
				 "occurredAt": "2026-09-19T09:00:00Z", "producer": "order-service",
				 "correlationId": "c-1", "parties": ["usr-req-1001", "usr-cou-2002"],
				 "requestId": "req-1", "requesterId": "usr-req-1001",
				 "pickupLocation": "Techno Edge", "dropoffLocation": "COM3-01-19", "note": null}
				""";
		RequestExpired expired = objectMapper.readValue(legacy, RequestExpired.class);

		assertThat(expired.courierId()).isNull();
		assertThat(RecipientResolver.recipientsOf(expired)).containsExactly(REQUESTER);
	}

	// the rater knows what they did (#44)
	@Test
	void ratingTellsTheRateeOnly() throws IOException {
		RatingSubmitted rating = (RatingSubmitted) readFixture("rating.submitted");

		assertThat(RecipientResolver.recipientsOf(rating)).containsExactly(rating.rateeId())
				.doesNotContain(rating.raterId());
	}

	// This service binds neither exchange (D16), so a credit or user event
	// can only arrive by hand or by a rebinding: it is refused like any
	// type without a rule and dead-letters where someone can see it.
	@Test
	void sagaRepliesAndUserEventsAreRefusedLikeAnyUnmappedType() throws IOException {
		for (String type : List.of("credit.reserved", "credit.reservation-rejected",
				"user.created", "user.purged")) {
			DomainEvent event = readFixture(type);
			assertThatThrownBy(() -> RecipientResolver.recipientsOf(event)).as(type)
					.isInstanceOf(IllegalStateException.class).hasMessageContaining(type);
		}
	}

	/**
	 * Registry-driven: a new request or rating event added to the catalog
	 * fails here until it has a rule — otherwise it would fall into the
	 * logging default and silently notify nobody.
	 */
	@Test
	void everyConsumedEventTypeHasARule() throws IOException {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			DomainEvent event = readFixture(entry.eventType());
			if (SAGA_REPLIES.contains(entry.eventType()) || event instanceof UserEvent) {
				continue;
			}
			assertThat(RecipientResolver.recipientsOf(event)).as(entry.eventType()).isNotEmpty();
		}
	}

	// the fixture-path rule, as in DomainEventContractTest (EventTypeRegistry's javadoc)
	private DomainEvent readFixture(String eventType) throws IOException {
		Class<? extends DomainEvent> type = EventTypeRegistry.entryFor(eventType).orElseThrow().eventClass();
		String path = "/contracts/" + eventType.replace('.', '-') + ".example.json";
		try (InputStream fixture = getClass().getResourceAsStream(path)) {
			assertThat(fixture).as("fixture %s on classpath via foc-contracts jar", path).isNotNull();
			return objectMapper.readValue(fixture, type);
		}
	}
}
