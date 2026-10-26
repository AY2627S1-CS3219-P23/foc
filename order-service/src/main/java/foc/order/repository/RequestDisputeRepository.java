/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-26.
 * Scope: issue #239: Spring Data JPA repository for RequestDispute,
 * following RequestsRepository.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.order.repository;

import foc.order.model.RequestDispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// findAllById loads the disputes of a whole page of requests in one query.
@Repository
public interface RequestDisputeRepository extends JpaRepository<RequestDispute, Long> {
}
