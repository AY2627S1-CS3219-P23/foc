/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13, issue #172.
 * Scope: the history filters of docs/credit-service.md "Read API
 * contract", as the query service takes them.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Instant;

/**
 * The optional history filters (Credit F4.1.2, F4.1.3). A null one is
 * not applied. The amounts are inclusive; {@code from} is inclusive and
 * {@code to} exclusive.
 */
public record HistoryFilter(Integer minAmount, Integer maxAmount, Instant from, Instant to) {
}
