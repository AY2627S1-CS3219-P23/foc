/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: empty Spring Data repository for the scaffold; queries come
 * with the features that need them.
 * 2026-10-11, Claude Code (Opus 5.5), issue #169: the locking read the
 * credit operations start with (D2, D3).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import foc.credit.entity.Reserved;

public interface ReservedRepository extends JpaRepository<Reserved, String> {

    // the first lock of every operation on a request (D2)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserved r where r.requestRef = :requestRef")
    Optional<Reserved> findForUpdate(@Param("requestRef") String requestRef);
}
