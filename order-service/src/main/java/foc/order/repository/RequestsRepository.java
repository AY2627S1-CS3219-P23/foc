/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-11.
 * Scope: Spring Data JPA repository skeleton for the Requests entity,
 * following the supplier-service SuppliersRepository pattern.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.repository;

import foc.order.model.Requests;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestsRepository extends JpaRepository<Requests, Long> {
    // Basic CRUD operations (save, findAll, findById, delete) are automatically included!
}
