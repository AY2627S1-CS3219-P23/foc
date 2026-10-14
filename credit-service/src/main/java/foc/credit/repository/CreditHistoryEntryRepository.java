/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: empty Spring Data repository for the scaffold; queries come
 * with the features that need them.
 * 2026-10-13, Claude Code (Opus 5.5), issue #172: Specification
 * queries, for the history filters (D5).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import foc.credit.entity.CreditHistoryEntry;

public interface CreditHistoryEntryRepository
        extends JpaRepository<CreditHistoryEntry, Long>, JpaSpecificationExecutor<CreditHistoryEntry> {
}
