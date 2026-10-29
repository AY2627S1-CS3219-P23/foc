/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-10-28
Scope: issue #267 review: the retention purge's query against Postgres:
       it deletes only events sent before the cutoff, never an unsent
       one however old.
Author review: Ryan Ang, pending pull request review.
*/

package foc.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import foc.user.PostgresTestContainer;
import foc.user.entity.OutboxEvent;

@SpringBootTest
@Transactional
class OutboxEventRepositoryTest extends PostgresTestContainer {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private OutboxEventRepository outboxEvents;

    @BeforeEach
    void emptyOutbox() {
        outboxEvents.deleteAll();
    }

    @Test
    void thePurgeDeletesOnlyEventsSentBeforeTheCutoff() {
        UUID oldSent = store(NOW.minus(60, ChronoUnit.DAYS), NOW.minus(40, ChronoUnit.DAYS));
        UUID recentSent = store(NOW.minus(60, ChronoUnit.DAYS), NOW.minus(10, ChronoUnit.DAYS));
        // credit-service has not heard of it yet, however old it is
        UUID oldUnsent = store(NOW.minus(60, ChronoUnit.DAYS), null);

        int deleted = outboxEvents.deleteSentBefore(NOW.minus(30, ChronoUnit.DAYS));

        assertThat(deleted).isEqualTo(1);
        assertThat(outboxEvents.findAll()).extracting(OutboxEvent::getEventId)
            .containsExactlyInAnyOrder(recentSent, oldUnsent)
            .doesNotContain(oldSent);
    }

    private UUID store(Instant createdAt, Instant sentAt) {
        UUID id = UUID.randomUUID();
        outboxEvents.insert(new OutboxEvent(id, "user.created", "{}", createdAt));
        if (sentAt != null) {
            OutboxEvent event = outboxEvents.findById(id).orElseThrow();
            event.setSentAt(sentAt);
            outboxEvents.saveAndFlush(event);
        }
        return id;
    }
}
