/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: idempotent event processor for issue #64, implementing the
 * team's decisions D4 (event-ID dedupe), D5 (per-entity sequence
 * discard), D11 (no broker imports) and requirements F1.1-F1.3,
 * F2.1, F2.4 per docs/notification-service.md.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.service;

import foc.contracts.events.EventEnvelope;
import foc.notification.entity.EntitySequence;
import foc.notification.entity.EntitySequenceId;
import foc.notification.entity.Notification;
import foc.notification.entity.ProcessedEvent;
import foc.notification.repository.EntitySequenceRepository;
import foc.notification.repository.NotificationRepository;
import foc.notification.repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns consumed envelopes into notification rows, idempotently, in a
 * single transaction — dedupe, stale discard, sequence update and
 * notification inserts commit or roll back together (D4).
 *
 * <p>Flow: (1) an already-seen event ID returns immediately (F2.1);
 * (2) the event ID is recorded — its primary key is the backstop for
 * a concurrent duplicate, whose losing transaction rolls back at
 * commit and resolves via broker redelivery into case 1 (only
 * reachable with listener concurrency &gt; 1; the default is 1);
 * (3) an envelope whose sequence is at or below the last applied for
 * its (entityType, entityId) is discarded — with its event ID kept,
 * so redeliveries of the stale event short-circuit at step 1 (D5,
 * F2.4); (4) the payload map is serialized with Boot's
 * auto-configured Jackson mapper — the same mapper the AMQP converter
 * uses (D15); (5) one notification row per party (F1.2).
 *
 * <p>Everything comes from the envelope alone — no calls to other
 * services (F1.1) — and unknown entity/event types flow through
 * generically (F1.3).
 */
@Service
class IdempotentEventProcessor implements EventProcessor {

	private final ProcessedEventRepository processedEvents;
	private final EntitySequenceRepository entitySequences;
	private final NotificationRepository notifications;
	private final ObjectMapper objectMapper;

	IdempotentEventProcessor(ProcessedEventRepository processedEvents,
			EntitySequenceRepository entitySequences,
			NotificationRepository notifications,
			ObjectMapper objectMapper) {
		this.processedEvents = processedEvents;
		this.entitySequences = entitySequences;
		this.notifications = notifications;
		this.objectMapper = objectMapper;
	}

	@Override
	@Transactional
	public void process(EventEnvelope envelope) {
		if (processedEvents.existsById(envelope.eventId())) {
			return;
		}
		processedEvents.save(new ProcessedEvent(envelope.eventId()));

		EntitySequenceId key = new EntitySequenceId(envelope.entityType(), envelope.entityId());
		EntitySequence sequence = entitySequences.findById(key).orElse(null);
		if (sequence != null && envelope.sequence() <= sequence.getLastAppliedSequence()) {
			return;
		}
		if (sequence == null) {
			entitySequences.save(new EntitySequence(key, envelope.sequence()));
		} else {
			sequence.setLastAppliedSequence(envelope.sequence());
		}

		String payloadJson = objectMapper.writeValueAsString(envelope.payload());
		for (String party : envelope.parties()) {
			notifications.save(new Notification(party, envelope.entityType(), envelope.entityId(),
					envelope.eventId(), envelope.eventType(), payloadJson, envelope.occurredAt()));
		}
	}
}
