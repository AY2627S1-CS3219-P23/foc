/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: the rating row the issue specifies, mirroring the V3
 * migration. The Hibernate-generated UUID id (so save() knows the row
 * is new, unlike #251's assigned-id Persistable workaround) and the
 * absence of setters (ratings are immutable, #44) are the author's
 * decisions.
 * Author review: [pending]
 */
package foc.rating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * One party's rating of the other for a completed request (N1.1, N1.2):
 * 1-5 stars and an optional write-up. Immutable after submit (#44), so
 * there are no setters; the database's unique (request_id, rater_id)
 * key enforces one rating per direction.
 */
// the unique constraint is declared here too, so the H2 test schema
// (built from the entities, Flyway off) enforces it like V3 does
@Entity
@Table(name = "rating", uniqueConstraints = @UniqueConstraint(name = "rating_once_per_rater",
        columnNames = {"request_id", "rater_id"}))
public class Rating {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private String requestId;

    @Column(name = "rater_id", nullable = false, updatable = false)
    private String raterId;

    @Column(name = "ratee_id", nullable = false, updatable = false)
    private String rateeId;

    // short, so Hibernate validates against the migration's smallint
    @Column(nullable = false, updatable = false)
    private short stars;

    // the optional write-up; null when the rater left none
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(updatable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Rating() {
    }

    public Rating(String requestId, String raterId, String rateeId, short stars, String body, Instant createdAt) {
        this.requestId = requestId;
        this.raterId = raterId;
        this.rateeId = rateeId;
        this.stars = stars;
        this.body = body;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRaterId() {
        return raterId;
    }

    public String getRateeId() {
        return rateeId;
    }

    public short getStars() {
        return stars;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
