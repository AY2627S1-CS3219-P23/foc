/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-10-28
Scope: issue #267 review: unit tests for the outbox retention purge, a
       copy of credit-service's OutboxRetentionSchedulerTest: the cutoff
       is the window before now, and a window below 1 is refused.
Author review: Ryan Ang, pending pull request review.
*/

package foc.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import foc.user.repository.OutboxEventRepository;

class OutboxRetentionSchedulerTest {

    private final OutboxEventRepository outboxEvents = mock(OutboxEventRepository.class);

    @Test
    void theEventsSentBeforeTheWindowArePurged() {
        Instant now = Instant.parse("2026-10-28T19:00:00Z");

        new OutboxRetentionScheduler(outboxEvents, 30, Clock.fixed(now, ZoneOffset.UTC)).purgeSentEvents();

        verify(outboxEvents).deleteSentBefore(Instant.parse("2026-09-28T19:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1 })
    void aWindowBelowOneDayIsRefused(int days) {
        assertThatThrownBy(() -> new OutboxRetentionScheduler(outboxEvents, days, Clock.systemUTC()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("user.outbox.retention.days");
    }
}
