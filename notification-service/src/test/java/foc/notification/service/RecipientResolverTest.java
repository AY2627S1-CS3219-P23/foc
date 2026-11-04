/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-04.
 * Scope: pins the D34 recipient rule per event type against the
 * canonical fixtures, and makes a rule mandatory for every registered
 * request/rating event (the check DomainEventContractTest used to make
 * on the removed parties field). Author review: Leong Wei Zhi, pending
 * pull request review.
 */
package foc.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import foc.contracts.events.core.DomainEvent;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.credit.CreditEvent;
import foc.contracts.events.rating.RatingSubmitted;
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

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void requesterOnlyEvents() throws IOException {
		for (String type : List.of("request.submitted", "request.created", "request.rejected",
				"request.cancelled", "request.courier-arrived", "request.dropped")) {
			assertThat(RecipientResolver.recipientsOf(readFixture(type))).as(type)
					.containsExactly(REQUESTER);
		}
	}

	@Test
	void requesterAndCourierEvents() throws IOException {
		for (String type : List.of("request.accepted", "request.collected", "request.completed",
				"request.disputed", "request.dispute-resolved")) {
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

	// the rater knows what they did (#44)
	@Test
	void ratingTellsTheRateeOnly() throws IOException {
		RatingSubmitted rating = (RatingSubmitted) readFixture("rating.submitted");

		assertThat(RecipientResolver.recipientsOf(rating)).containsExactly(rating.rateeId())
				.doesNotContain(rating.raterId());
	}

	// this service binds neither exchange; a hand-published one is logged, not stored
	@Test
	void creditAndUserEventsTellNobody() throws IOException {
		for (String type : List.of("credit.reserved", "credit.reservation-rejected",
				"user.created", "user.purged")) {
			assertThat(RecipientResolver.recipientsOf(readFixture(type))).as(type).isEmpty();
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
			if (event instanceof CreditEvent || event instanceof UserEvent) {
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
