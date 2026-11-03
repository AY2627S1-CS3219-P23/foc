/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: sends the owed chat calls (C11): every second the due ones go
 * out, oldest first and in order per request; a failed call is due again
 * 15 s later and given up, logged at ERROR, after 20 attempts (the
 * author's values). The 1 s first-attempt poll is the tool's choice,
 * listed in the doc.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

import foc.order.model.ChatCall;
import foc.order.repository.ChatCallRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The order transition never waits for chat-service: it only writes the
 * call (RequestsService), and this poll delivers it. chat-service's calls
 * are idempotent, so a call sent twice (a crash between the send and the
 * delete) does no harm.
 *
 * <p>Off in tests via {@code order.chat.dispatcher.enabled=false}, like
 * the outbox publisher.
 */
@Component
@ConditionalOnProperty(name = "order.chat.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
public class ChatCallDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChatCallDispatcher.class);

    private final ChatCallRepository calls;
    private final ChatClient client;
    private final int batchSize;
    private final Duration retryDelay;
    private final int maxAttempts;

    public ChatCallDispatcher(ChatCallRepository calls, ChatClient client,
            @Value("${order.chat.dispatcher.batch-size}") int batchSize,
            @Value("${order.chat.retry.delay-ms}") long retryDelayMs,
            @Value("${order.chat.retry.max-attempts}") int maxAttempts) {
        this.calls = calls;
        this.client = client;
        this.batchSize = batchSize;
        this.retryDelay = Duration.ofMillis(retryDelayMs);
        this.maxAttempts = maxAttempts;
    }

    // a delay, not a rate: polls never overlap, so a call is never sent
    // twice at once
    @Scheduled(fixedDelayString = "${order.chat.dispatcher.poll-interval-ms}")
    public void dispatch() {
        List<ChatCall> due = calls.findDue(Instant.now(), Limit.of(batchSize));
        for (ChatCall call : due) {
            if (client.send(call)) {
                calls.delete(call);
                continue;
            }
            call.failed(Instant.now(), retryDelay, maxAttempts);
            if (call.getStatus() == ChatCall.Status.FAILED) {
                log.error("chat {} for request {} given up after {} attempts", call.getKind(),
                        call.getRequestId(), call.getAttempts());
            }
            calls.save(call);
        }
    }
}
