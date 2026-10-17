/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: PR #168 Copilot review: the handler as Spring manages it, to
 * show the operation and the reply run inside one transaction. The unit
 * test calls the method directly and would pass without @Transactional.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import foc.contracts.events.request.RequestSubmitted;
import foc.credit.PostgresTestContainer;

@SpringBootTest
class RequestEventHandlerTransactionTest extends PostgresTestContainer {

    @MockitoBean
    private CreditOperations operations;

    @MockitoBean
    private ReplyOutbox outbox;

    @Autowired
    private RequestEventHandler handler;

    @Test
    void theOperationAndItsReplyRunInOneTransaction() {
        boolean[] active = new boolean[2];
        when(operations.reserve("r-1", "u-req", 3)).thenAnswer(invocation -> {
            active[0] = TransactionSynchronizationManager.isActualTransactionActive();
            return ReserveResult.RESERVED;
        });
        doAnswer(invocation -> {
            active[1] = TransactionSynchronizationManager.isActualTransactionActive();
            return null;
        }).when(outbox).enqueue(any());

        handler.handle(new RequestSubmitted("evt-1", Instant.now(), "order-service", "corr-1",
            List.of("u-req"), "r-1", "u-req", "UTown", "PGP", null, 3));

        verify(outbox).enqueue(any());
        assertThat(active).as("a transaction is open in reserve and in enqueue").containsExactly(true, true);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }
}
