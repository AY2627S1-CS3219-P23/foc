/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: JPA entity for the common_pool table, transcribed from
 * V1__baseline.sql (docs/credit-service.md "Schema"). The doc names no
 * class for this table; the name follows the table's. No logic.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Expired credits awaiting redistribution (F6.1). A single row, which
 * V1 inserts: read it by {@link #ID}, never create one.
 */
@Entity
@Table(name = "common_pool")
public class CommonPool {

    // the only row's key (CHECK id = 1)
    public static final short ID = 1;

    @Id
    @Column(nullable = false, updatable = false)
    private Short id;

    @Column(nullable = false)
    private int balance;

    protected CommonPool() {
    }

    public Short getId() {
        return id;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }
}
