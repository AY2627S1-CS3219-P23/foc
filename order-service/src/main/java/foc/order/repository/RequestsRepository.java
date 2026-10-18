/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-11.
 * Scope: Spring Data JPA repository skeleton for the Requests entity,
 * following the supplier-service SuppliersRepository pattern.
 * 2026-10-15: the pending-timeout sweep's query.
 * 2026-10-16: the query takes the OrderStates enum.
 * 2026-10-18: the expiry and auto-complete sweeps' queries.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.repository;

import foc.order.OrderStates;
import foc.order.model.Requests;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RequestsRepository extends JpaRepository<Requests, Long> {
    // Basic CRUD operations (save, findAll, findById, delete) are automatically included!

    // Requests in a state since before the cutoff, e.g. PENDING ones past the waiting time.
    List<Requests> findByStatusAndCreatedAtBefore(OrderStates status, Instant cutoff);

    // Requests in one of the states whose deadline has passed (the expiry sweep).
    List<Requests> findByStatusInAndDeadlineBefore(List<OrderStates> statuses, Instant now);

    // Requests in a state since the courier arrived before the cutoff (the auto-complete sweep).
    List<Requests> findByStatusAndArrivedAtBefore(OrderStates status, Instant cutoff);

}
