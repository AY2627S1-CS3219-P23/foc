/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-11.
 * Scope: Spring Data JPA repository skeleton for the Requests entity,
 * following the supplier-service SuppliersRepository pattern.
 * 2026-10-15: the pending-timeout sweep's query.
 * 2026-10-16: the query takes the OrderStates enum.
 * 2026-10-18: the expiry and auto-complete sweeps' queries; then the read
 * endpoints' paged queries.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.repository;

import foc.order.OrderStates;
import foc.order.model.Requests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // --- read endpoints; a null status matches every state

    Page<Requests> findByStatus(OrderStates status, Pageable pageable);

    @Query("select r from Requests r where r.requesterId = :user and (:status is null or r.status = :status)")
    Page<Requests> findAsRequester(@Param("user") Long user, @Param("status") OrderStates status, Pageable pageable);

    @Query("select r from Requests r where r.courierId = :user and (:status is null or r.status = :status)")
    Page<Requests> findAsCourier(@Param("user") Long user, @Param("status") OrderStates status, Pageable pageable);

    @Query("select r from Requests r where (r.requesterId = :user or r.courierId = :user)"
            + " and (:status is null or r.status = :status)")
    Page<Requests> findInvolving(@Param("user") Long user, @Param("status") OrderStates status, Pageable pageable);

}
