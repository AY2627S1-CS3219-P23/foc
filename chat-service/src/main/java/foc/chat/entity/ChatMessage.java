/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
 * Scope: the chat_message row of V1__chat.sql: text, an image, or both
 * (C7), the image bytes kept in Postgres (C9).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One message in a chat. Immutable once sent, so there are no setters.
 * History pages read it through {@code ChatMessageRepository}'s
 * projection, which leaves the image bytes out; only the image endpoint
 * loads the whole row.
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "chat_id", nullable = false, updatable = false)
    private UUID chatId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private long senderId;

    // null for an image-only message
    @Column(length = 500, updatable = false)
    private String text;

    // null for a text-only message; Postgres bytea
    @Column(updatable = false)
    private byte[] image;

    @Column(name = "image_content_type", length = 32, updatable = false)
    private String imageContentType;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected ChatMessage() {
    }

    public ChatMessage(UUID chatId, long senderId, String text, byte[] image, String imageContentType,
            Instant sentAt) {
        this.chatId = chatId;
        this.senderId = senderId;
        this.text = text;
        this.image = image;
        this.imageContentType = imageContentType;
        this.sentAt = sentAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChatId() {
        return chatId;
    }

    public long getSenderId() {
        return senderId;
    }

    public String getText() {
        return text;
    }

    public byte[] getImage() {
        return image;
    }

    public String getImageContentType() {
        return imageContentType;
    }

    public boolean hasImage() {
        return image != null;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
