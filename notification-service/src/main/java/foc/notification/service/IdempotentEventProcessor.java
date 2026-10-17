/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: idempotent event processor for issue #64, implementing the
 * team's decisions D4 (event-ID dedupe) and D11 (no broker imports)
 * and requirements F1.1-F1.3, F2.1 per docs/notification-service.md.
 * 2026-09-19, issue #67: publishes a NotificationStoredEvent per
 * saved row so the STOMP push gateway can push after commit.
 * 2026-09-19, Method-B refactor (D16-D19): consumes typed DomainEvent
 * records; the D5 per-entity sequence discard and entity identity were
 * removed by author decision (D19 supersedes D5, F2.4 retired).
 * 2026-09-20: order→request event vocabulary rename applied (author
 * decision D22, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 * 2026-10-17, Claude Code (Fable 5), issue #66: ingest-time display-name
 * enrichment per the author's D30 decision (options Q&A, superseding
 * D29): party IDs resolve to usernames through the UserDirectory port
 * and land in the stored payload, so push frames and REST rows carry
 * the same names without a read-time lookup. Best-effort by contract —
 * a failed lookup stores the row without the name, never dead-letters.
 */
package foc.notification.service;

import foc.contracts.events.core.DomainEvent;
import foc.notification.entity.Notification;
import foc.notification.entity.ProcessedEvent;
import foc.notification.repository.NotificationRepository;
import foc.notification.repository.ProcessedEventRepository;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns consumed events into notification rows, idempotently, in a
 * single transaction — dedupe and notification inserts commit or roll
 * back together (D4).
 *
 * <p>Flow: (1) an already-seen event ID returns immediately (F2.1);
 * (2) the event ID is recorded — its primary key is the backstop for
 * a concurrent duplicate, whose losing transaction rolls back at
 * commit and resolves via broker redelivery into case 1 (only
 * reachable with listener concurrency &gt; 1; the default is 1);
 * (3) the event's business fields (everything outside
 * {@link DomainEvent#METADATA_FIELDS}) are serialized with Boot's
 * auto-configured Jackson mapper — the same mapper the AMQP converter
 * uses (D15) — into the payload column, keeping the client-facing
 * shape free of transport metadata; (4) one notification row per
 * party (F1.2), each followed by a {@link NotificationStoredEvent}
 * published inside the transaction — transaction-bound listeners (the
 * STOMP push gateway) receive it only after the commit, and discarded
 * events publish nothing.
 *
 * <p>Everything else comes from the event alone (F1.1, amended by
 * D30): the one outward call is the best-effort username lookup per
 * party-id field, through {@link UserDirectory} — cached, finite
 * timeouts, and empty on any failure, so it can slow an ingest but
 * never fail one. The lookup does run inside this transaction; with
 * the adapter's 2 s ceilings that is a bounded hold, accepted over
 * resolving before the dedupe check (which would pay the lookup for
 * duplicate deliveries too).
 */
@Service
class IdempotentEventProcessor implements EventProcessor {

	/** Party-id payload fields and the display-name field each feeds (D30). */
	private static final Map<String, String> PARTY_NAME_FIELDS = Map.of(
			"requesterId", "requesterName",
			"courierId", "courierName");

	private final ProcessedEventRepository processedEvents;
	private final NotificationRepository notifications;
	private final ObjectMapper objectMapper;
	private final ApplicationEventPublisher eventPublisher;
	private final UserDirectory userDirectory;

	IdempotentEventProcessor(ProcessedEventRepository processedEvents,
			NotificationRepository notifications,
			ObjectMapper objectMapper,
			ApplicationEventPublisher eventPublisher,
			UserDirectory userDirectory) {
		this.processedEvents = processedEvents;
		this.notifications = notifications;
		this.objectMapper = objectMapper;
		this.eventPublisher = eventPublisher;
		this.userDirectory = userDirectory;
	}

	@Override
	@Transactional
	public void process(DomainEvent event) {
		if (processedEvents.existsById(event.eventId())) {
			return;
		}
		processedEvents.save(new ProcessedEvent(event.eventId()));

		Map<String, Object> fields = businessFields(event);
		PARTY_NAME_FIELDS.forEach((idField, nameField) -> {
			if (fields.get(idField) instanceof String userId) {
				userDirectory.usernameOf(userId).ifPresent(name -> fields.put(nameField, name));
			}
		});
		String payloadJson = objectMapper.writeValueAsString(fields);
		for (String party : event.parties()) {
			Notification saved = notifications.save(new Notification(party, event.eventId(),
					event.eventType(), payloadJson, event.occurredAt()));
			eventPublisher.publishEvent(new NotificationStoredEvent(saved));
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> businessFields(DomainEvent event) {
		Map<String, Object> fields = objectMapper.convertValue(event, Map.class);
		fields.keySet().removeAll(DomainEvent.METADATA_FIELDS);
		return fields;
	}
}
