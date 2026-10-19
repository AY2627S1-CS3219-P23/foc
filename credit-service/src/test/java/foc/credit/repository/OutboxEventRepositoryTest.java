/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13, PR #188 review (Leong Wei
 * Zhi).
 * Scope: the outbox publisher's poll query against a real Postgres:
 * which replies are due, in what order, and the batch cap applied to
 * the due ones only (the team's decision on the review's finding).
 * 2026-10-19, Claude Code (Opus 5.5), issue #218: the retention purge's
 * delete (team decisions: sent rows only, counted from sent_at) and the
 * V3 index it reads.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

import foc.credit.PostgresTestContainer;
import foc.credit.entity.OutboxEvent;

@SpringBootTest
class OutboxEventRepositoryTest extends PostgresTestContainer {

    private static final Instant NOW = Instant.parse("2026-10-13T08:00:00Z");

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private JdbcTemplate jdbc;

    // the query reads the whole table, so rows other tests left count
    @BeforeEach
    void anEmptyOutbox() {
        outboxEvents.deleteAll();
    }

    @Test
    void aReplyNeverReturnedIsDue() {
        UUID reply = stored(NOW.minusSeconds(5), null, null);

        assertThat(dueAt(NOW, 10)).containsExactly(reply);
    }

    @Test
    void aReturnedReplyIsNotDueBeforeItsNextAttempt() {
        UUID returned = stored(NOW.minusSeconds(5), null, NOW.plusSeconds(30));

        assertThat(dueAt(NOW, 10)).isEmpty();
        assertThat(dueAt(NOW.plusSeconds(30), 10)).containsExactly(returned);
    }

    @Test
    void aSentReplyIsNeverDue() {
        stored(NOW.minusSeconds(5), NOW, null);

        assertThat(dueAt(NOW, 10)).isEmpty();
    }

    @Test
    void theDueRepliesComeOldestFirst() {
        UUID newer = stored(NOW.minusSeconds(1), null, null);
        UUID older = stored(NOW.minusSeconds(9), null, NOW.minusSeconds(2));

        assertThat(dueAt(NOW, 10)).containsExactly(older, newer);
    }

    // the review's finding: older replies waiting for their next attempt
    // take no place in the batch
    @Test
    void theBatchIsCountedOverTheDueRepliesOnly() {
        stored(NOW.minusSeconds(9), null, NOW.plusSeconds(30));
        stored(NOW.minusSeconds(8), null, NOW.plusSeconds(30));
        UUID third = stored(NOW.minusSeconds(7), null, null);
        UUID fourth = stored(NOW.minusSeconds(6), null, null);
        stored(NOW.minusSeconds(5), null, null);

        assertThat(dueAt(NOW, 2)).containsExactly(third, fourth);
    }

    @Test
    void thePurgeDeletesTheRepliesSentBeforeTheCutoff() {
        Instant cutoff = NOW.minus(Duration.ofDays(30));
        stored(cutoff.minus(Duration.ofDays(2)), cutoff.minusSeconds(1), null);
        UUID sentAtCutoff = stored(cutoff.minus(Duration.ofDays(2)), cutoff, null);
        UUID sentSince = stored(cutoff.minus(Duration.ofDays(2)), cutoff.plusSeconds(1), null);

        assertThat(outboxEvents.deleteSentBefore(cutoff)).isEqualTo(1);
        assertThat(remaining()).containsExactlyInAnyOrder(sentAtCutoff, sentSince);
    }

    // an unsent reply is one the Order Service is still waiting for
    @Test
    void thePurgeNeverDeletesAnUnsentReply() {
        Instant cutoff = NOW.minus(Duration.ofDays(30));
        UUID unsent = stored(cutoff.minus(Duration.ofDays(60)), null, null);
        UUID returned = stored(cutoff.minus(Duration.ofDays(60)), null, NOW.plusSeconds(30));

        assertThat(outboxEvents.deleteSentBefore(cutoff)).isZero();
        assertThat(remaining()).containsExactlyInAnyOrder(unsent, returned);
    }

    @Test
    void theSentRepliesHaveAPartialIndexOnSentAt() {
        String definition = jdbc.queryForObject(
            "select indexdef from pg_indexes where tablename = 'outbox_event' and indexname = 'idx_outbox_event_sent'",
            String.class);

        assertThat(definition).contains("(sent_at)").contains("WHERE (sent_at IS NOT NULL)");
    }

    private List<UUID> remaining() {
        return outboxEvents.findAll().stream().map(OutboxEvent::getEventId).toList();
    }

    private List<UUID> dueAt(Instant now, int batchSize) {
        return outboxEvents.findDue(now, Limit.of(batchSize)).stream().map(OutboxEvent::getEventId).toList();
    }

    private UUID stored(Instant createdAt, Instant sentAt, Instant nextAttemptAt) {
        OutboxEvent reply = new OutboxEvent(UUID.randomUUID(), "credit.reserved", "{}", createdAt);
        reply.setSentAt(sentAt);
        reply.setNextAttemptAt(nextAttemptAt);
        return outboxEvents.save(reply).getEventId();
    }
}
