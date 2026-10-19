/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-19, issue #218.
 * Scope: the outbox retention purge with the repository mocked and the
 * clock fixed: the cutoff is the configured number of days before now,
 * and a window below 1 day is refused at construction (team decision).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import foc.credit.repository.OutboxEventRepository;

class OutboxRetentionSchedulerTest {

    private final OutboxEventRepository outboxEvents = mock(OutboxEventRepository.class);

    @Test
    void theRepliesSentBeforeTheWindowArePurged() {
        Instant now = Instant.parse("2026-10-19T19:00:00Z");

        new OutboxRetentionScheduler(outboxEvents, 30, Clock.fixed(now, ZoneOffset.UTC)).purgeSentReplies();

        verify(outboxEvents).deleteSentBefore(Instant.parse("2026-09-19T19:00:00Z"));
    }

    // a window of 0 would purge every sent reply on the first run
    @ParameterizedTest
    @ValueSource(ints = { 0, -1 })
    void aWindowBelowOneDayIsRefused(int days) {
        assertThatThrownBy(() -> new OutboxRetentionScheduler(outboxEvents, days))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("credit.outbox.retention.days");
    }
}
