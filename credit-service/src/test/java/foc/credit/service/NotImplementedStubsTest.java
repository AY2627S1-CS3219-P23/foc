/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the two scaffold interfaces resolve to a bean, and every stub
 * method says it is not implemented rather than doing nothing.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import foc.credit.PostgresTestContainer;

@SpringBootTest
class NotImplementedStubsTest extends PostgresTestContainer {

    @Autowired
    private CreditOperations creditOperations;

    @Autowired
    private ReplyOutbox replyOutbox;

    private static void assertNotImplemented(ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("not implemented");
    }

    @Test
    void creditOperationsAreNotImplemented() {
        assertNotImplemented(() -> creditOperations.getOrCreate("u-1"));
        assertNotImplemented(() -> creditOperations.reserve("r-1", "u-1", 3));
        assertNotImplemented(() -> creditOperations.transfer("r-1", "u-2"));
        assertNotImplemented(() -> creditOperations.release("r-1", "u-1"));
    }

    @Test
    void replyOutboxIsNotImplemented() {
        assertNotImplemented(() -> replyOutbox.enqueue(null));
    }
}
