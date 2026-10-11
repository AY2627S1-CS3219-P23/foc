/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the two scaffold interfaces resolve to a bean, and every stub
 * method says it is not implemented rather than doing nothing (transfer
 * and release included, now that they return a SettleResult).
 * 2026-10-11, Claude Code (Opus 5.5), issue #169: the CreditOperations
 * case removed with its stub; the real bean has CreditOperationsServiceTest.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import foc.credit.PostgresTestContainer;

@SpringBootTest
class NotImplementedStubsTest extends PostgresTestContainer {

    @Autowired
    private ReplyOutbox replyOutbox;

    @Test
    void replyOutboxIsNotImplemented() {
        assertThatThrownBy(() -> replyOutbox.enqueue(null))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("not implemented");
    }
}
