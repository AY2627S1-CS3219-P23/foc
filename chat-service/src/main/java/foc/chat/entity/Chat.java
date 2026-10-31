/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
 * Scope: the chat row of V1__chat.sql: one per accepted courier of a
 * request (C4), opened and closed by order-service's calls (C11).
 * 2026-10-31: each party's last-read time, for their unread count
 * (C16-C19).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * The chat between a request's requester and one courier. Its parties
 * never change: a courier who drops the request leaves their chat
 * closed, and the next courier gets a new one (C4).
 */
@Entity
@Table(name = "chat")
public class Chat {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private long requestId;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private long requesterId;

    @Column(name = "courier_id", nullable = false, updatable = false)
    private long courierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChatStatus status;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    // null while open
    @Column(name = "closed_at")
    private Instant closedAt;

    // when each party last opened the chat; null if never
    @Column(name = "requester_last_read_at")
    private Instant requesterLastReadAt;

    @Column(name = "courier_last_read_at")
    private Instant courierLastReadAt;

    protected Chat() {
    }

    public Chat(long requestId, long requesterId, long courierId, Instant openedAt) {
        this.requestId = requestId;
        this.requesterId = requesterId;
        this.courierId = courierId;
        this.status = ChatStatus.OPEN;
        this.openedAt = openedAt;
    }

    public void close(Instant at) {
        this.status = ChatStatus.CLOSED;
        this.closedAt = at;
    }

    public boolean isOpen() {
        return status == ChatStatus.OPEN;
    }

    public boolean isParty(long userId) {
        return userId == requesterId || userId == courierId;
    }

    /** The other party of the chat; only meaningful for a party. */
    public long otherParty(long userId) {
        return userId == requesterId ? courierId : requesterId;
    }

    /** When the party last opened the chat, or null if never. */
    public Instant lastReadAt(long userId) {
        return userId == requesterId ? requesterLastReadAt : courierLastReadAt;
    }

    public void markRead(long userId, Instant at) {
        if (userId == requesterId) {
            requesterLastReadAt = at;
        } else if (userId == courierId) {
            courierLastReadAt = at;
        }
    }

    public UUID getId() {
        return id;
    }

    public long getRequestId() {
        return requestId;
    }

    public long getRequesterId() {
        return requesterId;
    }

    public long getCourierId() {
        return courierId;
    }

    public ChatStatus getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
