/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: empty Spring Data repository for the scaffold; queries come
 * with the features that need them.
 * 2026-10-11, Claude Code (Opus 5.5), issue #169: the locking read the
 * credit operations start with (D2, D3); then, on team review, the
 * insert that never overwrites, moved here from the service so a test
 * can call it twice.
 * 2026-10-25, Claude Code (Opus 5.5), issue #236: the read behind the
 * admin list of long-held reservations.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import foc.credit.entity.ReservationStatus;
import foc.credit.entity.Reserved;

public interface ReservedRepository extends JpaRepository<Reserved, String> {

    // the first lock of every operation on a request (D2)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserved r where r.requestRef = :requestRef")
    Optional<Reserved> findForUpdate(@Param("requestRef") String requestRef);

    // a plain read for the admin list (issue #236); the page sets the
    // order and the cap
    List<Reserved> findByStatusAndCreatedAtLessThanEqual(ReservationStatus status, Instant cutoff, Pageable page);

    /**
     * Inserts a request's record, never updates one: a second insert for
     * the same request fails on the primary key (D3). Use this for a new
     * record, not {@code save}, which merges and would overwrite the
     * record a concurrent first delivery just committed.
     */
    default void insert(Reserved record) {
        insertRow(record.getRequestRef(), record.getRequesterId(), record.getAmount(),
            record.getStatus().name(), record.getCreatedAt(), record.getSettledAt());
    }

    // the cast gives a null settledAt a type
    @Modifying
    @Query(value = """
        insert into credit_reservation (request_ref, requester_id, amount, status, created_at, settled_at)
        values (:requestRef, :requesterId, :amount, :status, :createdAt, cast(:settledAt as timestamptz))
        """, nativeQuery = true)
    void insertRow(@Param("requestRef") String requestRef, @Param("requesterId") String requesterId,
            @Param("amount") int amount, @Param("status") String status,
            @Param("createdAt") Instant createdAt, @Param("settledAt") Instant settledAt);
}
