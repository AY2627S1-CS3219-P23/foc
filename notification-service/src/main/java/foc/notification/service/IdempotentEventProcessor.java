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
 * <p>Everything comes from the event alone — no calls to other
 * services (F1.1).
 */
@Service
class IdempotentEventProcessor implements EventProcessor {

	private final ProcessedEventRepository processedEvents;
	private final NotificationRepository notifications;
	private final ObjectMapper objectMapper;
	private final ApplicationEventPublisher eventPublisher;

	IdempotentEventProcessor(ProcessedEventRepository processedEvents,
			NotificationRepository notifications,
			ObjectMapper objectMapper,
			ApplicationEventPublisher eventPublisher) {
		this.processedEvents = processedEvents;
		this.notifications = notifications;
		this.objectMapper = objectMapper;
		this.eventPublisher = eventPublisher;
	}

	@Override
	@Transactional
	public void process(DomainEvent event) {
		if (processedEvents.existsById(event.eventId())) {
			return;
		}
		processedEvents.save(new ProcessedEvent(event.eventId()));

		String payloadJson = objectMapper.writeValueAsString(businessFields(event));
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
