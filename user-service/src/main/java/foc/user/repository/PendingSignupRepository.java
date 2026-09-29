/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: repository for issue #88's pending sign-ups: lookup by email for
       the sign-up/verify flows, and a bulk expiry sweep for the purge
       scheduler (bulk delete style follows OtpRepository's, whose table
       this design replaces).
       PR #150 Copilot review: locked finder added so concurrent verify
       attempts serialize per row instead of losing attempt increments.
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import foc.user.entity.PendingSignup;
import jakarta.persistence.LockModeType;

@Repository
public interface PendingSignupRepository extends JpaRepository<PendingSignup, Long> {

    Optional<PendingSignup> findByEmail(String email);

    // verify reads-then-writes the attempts counter; the row lock makes
    // concurrent guesses queue so none of the increments is lost
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PendingSignup p WHERE p.email = :email")
    Optional<PendingSignup> findWithLockByEmail(@Param("email") String email);

    // hygiene only: an expired row is also rejected on sight at verify
    // time, so this sweep just keeps the table from accumulating them
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PendingSignup p WHERE p.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
