/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: eligibility-row repository for issue #251; #252's submit API
 * will read through it.
 * Author review: [pending]
 */
package foc.rating.repository;

import foc.rating.entity.RateableRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateableRequestRepository extends JpaRepository<RateableRequest, String> {
}
