/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: a call order-service owes chat-service (C11), the chat_call row
 * of V5__chat_calls.sql.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * Open or close the chat of a request, written in the same transaction as
 * the request's state change, so the call is owed exactly when the change
 * committed. {@code ChatCallDispatcher} sends it and deletes the row once
 * chat-service has taken it.
 */
@Entity
@Table(name = "chat_call")
public class ChatCall {

    public enum Kind { OPEN, CLOSE }

    public enum Status { PENDING, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 8)
    private Kind kind;

    @Column(name = "requester_id", updatable = false)
    private Long requesterId;

    @Column(name = "courier_id", updatable = false)
    private Long courierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatCall() {
    }

    private ChatCall(Long requestId, Kind kind, Long requesterId, Long courierId, Instant now) {
        this.requestId = requestId;
        this.kind = kind;
        this.requesterId = requesterId;
        this.courierId = courierId;
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public static ChatCall open(Long requestId, Long requesterId, Long courierId, Instant now) {
        return new ChatCall(requestId, Kind.OPEN, requesterId, courierId, now);
    }

    public static ChatCall close(Long requestId, Instant now) {
        return new ChatCall(requestId, Kind.CLOSE, null, null, now);
    }

    /** Counts a failed attempt: due again after the delay, or given up after the last one. */
    public void failed(Instant now, Duration retryDelay, int maxAttempts) {
        attempts++;
        if (attempts >= maxAttempts) {
            status = Status.FAILED;
        } else {
            nextAttemptAt = now.plus(retryDelay);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Kind getKind() {
        return kind;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public Long getCourierId() {
        return courierId;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }
}
