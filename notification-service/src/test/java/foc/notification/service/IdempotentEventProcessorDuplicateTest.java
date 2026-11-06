/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-06.
 * Scope: issue #48 (DP10): unit coverage of the insert-first dedupe's
 * primary-key-violation path (team decision, Leong Wei Zhi via options
 * Q&A), with mocked repositories and a recording transaction manager: a
 * unique violation on the dedupe row rolls the transaction back and
 * returns normally with nothing stored or pushed; any other integrity
 * failure is rethrown. The real violation on Postgres is in
 * ConcurrentDedupeTest.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import foc.contracts.events.request.RequestCreated;
import foc.contracts.events.request.RequestDropped;
import foc.notification.entity.ProcessedEvent;
import foc.notification.repository.NotificationRepository;
import foc.notification.repository.ProcessedEventRepository;
import java.sql.SQLException;
import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

class IdempotentEventProcessorDuplicateTest {

	private static final Instant OCCURRED_AT = Instant.parse("2026-11-06T08:00:00Z");

	private final ProcessedEventRepository processedEvents = mock(ProcessedEventRepository.class);
	private final NotificationRepository notifications = mock(NotificationRepository.class);
	private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
	private final RecordingTransactionManager transactions = new RecordingTransactionManager();
	private IdempotentEventProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new IdempotentEventProcessor(processedEvents, notifications, JsonMapper.builder().build(),
				publisher, transactions);
	}

	private static RequestCreated created(String eventId) {
		return new RequestCreated(eventId, OCCURRED_AT, "order-service", "c-1",
				"req-1", "usr-req-1001", "Techno Edge", "COM3-01-19", "hi");
	}

	// as Hibernate's ConstraintViolationException reaches the repository
	// caller: translated, the driver's SQLException at the bottom
	private static DataIntegrityViolationException violation(String sqlState) {
		SQLException driver = new SQLException("simulated", sqlState);
		return new DataIntegrityViolationException("simulated",
				new ConstraintViolationException("simulated", driver, "processed_events_pkey"));
	}

	@Test
	void aPrimaryKeyViolationOnTheDedupeRowRollsBackAndReturnsNormally() {
		when(processedEvents.existsById("e-1")).thenReturn(false);
		when(processedEvents.saveAndFlush(any(ProcessedEvent.class))).thenThrow(violation("23505"));

		processor.process(created("e-1"));

		verify(notifications, never()).save(any());
		verifyNoInteractions(publisher);
		assertThat(transactions.rolledBack).isTrue();
		assertThat(transactions.committed).isFalse();
	}

	// a drop's prune runs after the dedupe row: a concurrent duplicate drop
	// never deletes anything
	@Test
	void aConcurrentDuplicateDropPrunesNothing() {
		RequestDropped drop = new RequestDropped("e-drop", OCCURRED_AT, "order-service", "c-1",
				"req-1", "usr-req-1001", "usr-cou-2002", "Techno Edge", "COM3-01-19", "hi");
		when(processedEvents.saveAndFlush(any(ProcessedEvent.class))).thenThrow(violation("23505"));

		processor.process(drop);

		verify(notifications, never()).deleteByRecipientIdAndPayloadLike(any(), any(), any());
		verify(notifications, never()).save(any());
		assertThat(transactions.rolledBack).isTrue();
	}

	@Test
	void anotherIntegrityFailureIsRethrownForTheRetryPath() {
		// 22001: value too long, e.g. an over-long event id — not a duplicate
		when(processedEvents.saveAndFlush(any(ProcessedEvent.class))).thenThrow(violation("22001"));

		assertThatThrownBy(() -> processor.process(created("e-1")))
				.isInstanceOf(DataIntegrityViolationException.class);

		verify(notifications, never()).save(any());
		assertThat(transactions.rolledBack).isTrue();
	}

	@Test
	void theDedupeRowIsFlushedBeforeAnyNotificationIsStored() {
		when(notifications.save(any())).thenAnswer(call -> call.getArgument(0));

		processor.process(created("e-1"));

		InOrder order = inOrder(processedEvents, notifications);
		order.verify(processedEvents).existsById("e-1");
		order.verify(processedEvents).saveAndFlush(any(ProcessedEvent.class));
		order.verify(notifications).save(any());
		assertThat(transactions.committed).isTrue();
	}

	@Test
	void aSeenBeforeEventWritesNothing() {
		when(processedEvents.existsById("e-1")).thenReturn(true);

		processor.process(created("e-1"));

		verify(processedEvents, never()).saveAndFlush(any());
		verify(notifications, never()).save(any());
		assertThat(transactions.committed).isTrue();
	}

	/** Records whether the template committed or rolled back. */
	private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {

		boolean committed;
		boolean rolledBack;

		@Override
		protected Object doGetTransaction() {
			return new Object();
		}

		@Override
		protected void doBegin(Object transaction, TransactionDefinition definition) {
		}

		@Override
		protected void doCommit(DefaultTransactionStatus status) {
			committed = true;
		}

		@Override
		protected void doRollback(DefaultTransactionStatus status) {
			rolledBack = true;
		}
	}
}
