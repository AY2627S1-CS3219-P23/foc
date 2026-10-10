/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: empty Spring Data repository for the scaffold; queries come
 * with the features that need them.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import foc.credit.entity.CreditHistoryEntry;

public interface CreditHistoryEntryRepository extends JpaRepository<CreditHistoryEntry, Long> {
}
