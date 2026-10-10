/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the credit_history.type values, from V1__baseline.sql's CHECK
 * constraint (docs/credit-service.md "Schema").
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.entity;

/** Which way a history row's amount moved (the doc's history table). */
public enum CreditHistoryType {
    PROVISION,
    RESERVE,
    RELEASE,
    TRANSFER_OUT,
    TRANSFER_IN,
    EXPIRY,
    REDISTRIBUTION
}
