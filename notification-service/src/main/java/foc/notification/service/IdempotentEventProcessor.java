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
 * PR #205 review: the lookup moved OUTSIDE the transaction (programmatic
 * TransactionTemplate replaces @Transactional) so a user-service stall
 * can no longer hold a pooled DB connection mid-ingest.
 * 2026-10-19, Claude Code (Fable 5), issue #221: ingest-time name
 * enrichment removed per the author's D32 decision (options Q&A,
 * superseding D30/D31) — payloads carry party IDs only and the web
 * hydrates names at read time. Reverted to @Transactional: the
 * lookup-before-transaction ordering was the sole reason for the
 * programmatic TransactionTemplate.
 * 2026-10-30, Claude Code (Fable 5), issue #266: request.dropped now
 * prunes the dropping courier's rows for that request, per the
 * author's D33 decision (options Q&A: prune chosen over past-party
 * read access and over web-only 404 softening) — the drop removes
 * them as a party, so their rows could only dead-end on the detail
 * 404. Same transaction as the dedupe insert, so a redelivered event
 * replays both or neither.
 * 2026-10-31, Claude Code (Fable 5), PR #277 review (@Sinnez1): the
 * prune is bounded to rows at or before the drop (a retried drop no
 * longer deletes a re-accept's fresh rows), and the reverse reorder is
 * guarded at ingest — a courier-party event landing after the drop
 * that covers it stores no courier row (author decision via options
 * Q&A, over a D33 accepted-limitation entry).
 * 2026-11-04, Claude Code (Fable 5.1), D34 (author decision via options
 * Q&A): the fan-out iterates RecipientResolver's per-type rule instead
 * of the removed parties field — events carry business facts only.
 * Author review: Leong Wei Zhi, pending pull request review.
 * PR #287 review (@Sinnez1): recipients are resolved before the first
 * write, so a missing rule or courier throws with nothing to roll back
 * and the message stays replayable through the retry/DLQ path.
 * 2026-11-08, Claude Code (Fable 5.1), PR #306 review (Sinnez1): the
 * concurrent-duplicate note updated for two replicas (issue #305). Author
 * review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.service;

import foc.contracts.events.core.DomainEvent;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.request.RequestDropped;
import foc.notification.entity.Notification;
import foc.notification.entity.ProcessedEvent;
import foc.notification.repository.NotificationRepository;
import foc.notification.repository.ProcessedEventRepository;
import java.time.Instant;
import java.util.List;
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
 * (1b) recipients are resolved from the event's own id fields (D34) —
 * a type without a rule or a missing required courier throws here,
 * before any write, so the listener's retry/DLQ path keeps the message
 * replayable (PR #287 review); (2) the event ID is recorded — its primary key is the backstop for
 * a concurrent duplicate (two replicas consuming the same event, issue
 * #305), whose losing transaction rolls back at commit on
 * {@code processed_events_pkey}; the listener acks that failure as
 * already processed;
 * (3) the event's business fields (everything outside
 * {@link DomainEvent#METADATA_FIELDS}) are serialized with Boot's
 * auto-configured Jackson mapper — the same mapper the AMQP converter
 * uses (D15) — into the payload column, keeping the client-facing
 * shape free of transport metadata; (4) one notification row per
 * recipient the {@link RecipientResolver} names from the event's own
 * id fields (F1.2, D34), each followed by a {@link NotificationStoredEvent}
 * published inside the transaction — transaction-bound listeners (the
 * STOMP push gateway) receive it only after the commit, and discarded
 * events publish nothing.
 *
 * <p>Everything comes from the event alone (F1.1) — this processor
 * makes no outward calls. Party-id fields (requesterId, courierId)
 * stay IDs in the stored payload; the web resolves them to usernames
 * at read time (D32, superseding D30's ingest-time enrichment).
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
		// Recipients first (PR #287 review): a throw here leaves nothing
		// behind — no dedupe row recording a half-delivered event — so the
		// message retries and dead-letters for replay once the rule or the
		// producer is fixed.
		List<String> recipients = RecipientResolver.recipientsOf(event);
		// A drop removes the courier as a party (order-service nulls
		// courierId), so every row they hold for this request now
		// dead-ends on the detail 404 — delete them (D33, issue #266).
		// Bounded to rows at or before the drop: a retried drop (first
		// attempt rolled back, so the dedupe never recorded it) must not
		// take the fresh rows of a re-accept with it (PR #277 review).
		// MUST run before the saves below: the bulk query's
		// clearAutomatically empties the persistence context, and the
		// dedupe row's insert (assigned PK, so queued until flush, unlike
		// the notifications' immediate IDENTITY inserts) would be
		// discarded with it — a redelivery would then prune again and
		// store the requester's row twice.
		if (event instanceof RequestDropped dropped) {
			notifications.deleteByRecipientIdAndPayloadLike(dropped.courierId(),
					fieldPattern("requestId", dropped.requestId()), dropped.occurredAt());
		}
		processedEvents.save(new ProcessedEvent(event.eventId()));
		Map<String, Object> fields = businessFields(event);
		String payloadJson = objectMapper.writeValueAsString(fields);
		for (String recipient : recipients) {
			if (droppedSince(fields, recipient, event.occurredAt())) {
				continue;
			}
			Notification saved = notifications.save(new Notification(recipient, event.eventId(),
					event.eventType(), payloadJson, event.occurredAt()));
			eventPublisher.publishEvent(new NotificationStoredEvent(saved));
		}
	}

	// The prune's reverse reordering (PR #277 review): a courier-party
	// event (request.accepted/.collected, a dispute) delayed by the
	// retry queue can land after the drop that pruned the courier's
	// rows — storing it would re-create exactly the dead-end row D33
	// removes. "Was this request dropped by this courier at or after
	// this event?" is asked of the requester's stored request.dropped
	// row, whose payload carries both ids; ties go to the drop,
	// mirroring the prune's <= bound. A re-accept is strictly newer
	// than the drop it follows, so its rows store normally — as does
	// every row of a recipient that is not the event's courier.
	private boolean droppedSince(Map<String, Object> fields, String recipient, Instant occurredAt) {
		if (!(fields.get("requestId") instanceof String requestId)
				|| !recipient.equals(fields.get("courierId"))) {
			return false;
		}
		return notifications.countByEventTypeSincePayloadMatchingBoth(
				EventTypeRegistry.routingKeyFor(RequestDropped.class), occurredAt,
				fieldPattern("requestId", requestId), fieldPattern("courierId", recipient)) > 0;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> businessFields(DomainEvent event) {
		Map<String, Object> fields = objectMapper.convertValue(event, Map.class);
		fields.keySet().removeAll(DomainEvent.METADATA_FIELDS);
		return fields;
	}

	// Matches the payload's serialized "<field>":"<value>" — both quotes
	// in the pattern, so request 12 can never match request 123's rows.
	// LIKE wildcards in the value are escaped (the repository queries
	// declare '\' as the escape character); real ids are numeric strings,
	// but the contract type is String, so this must not trust that.
	private static String fieldPattern(String field, String value) {
		String escaped = value
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
		return "%\"" + field + "\":\"" + escaped + "\"%";
	}
}
