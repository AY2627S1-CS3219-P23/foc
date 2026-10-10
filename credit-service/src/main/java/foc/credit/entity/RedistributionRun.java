/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: JPA entity for the redistribution_run table, transcribed from
 * V1__baseline.sql (docs/credit-service.md "Schema"); no logic.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A month already redistributed. The primary key on {@code period} lets
 * only one run per month commit (F6.1.1).
 */
@Entity
@Table(name = "redistribution_run")
public class RedistributionRun {

    // year and month, e.g. 2026-10
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 7)
    private String period;

    @Column(nullable = false, updatable = false)
    private int distributed;

    @Column(nullable = false, updatable = false)
    private int remainder;

    @Column(name = "ran_at", nullable = false, updatable = false)
    private Instant ranAt;

    protected RedistributionRun() {
    }

    public RedistributionRun(String period, int distributed, int remainder, Instant ranAt) {
        this.period = period;
        this.distributed = distributed;
        this.remainder = remainder;
        this.ranAt = ranAt;
    }

    public String getPeriod() {
        return period;
    }

    public int getDistributed() {
        return distributed;
    }

    public int getRemainder() {
        return remainder;
    }

    public Instant getRanAt() {
        return ranAt;
    }
}
