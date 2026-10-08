/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: repository for issue #92's pending email changes, modeled on
       PendingSignupRepository: a locked finder so the flows that
       read-then-write a row (request, verify, resend) serialize per
       account, and a bulk expiry sweep for the purge scheduler.
       PR #157 Copilot review: the purge's FK cleanup no longer rides on
       the expiry sweep (TTL and retention window are independently
       configurable, so a live row of a purged account is possible) —
       deleteByUserDeletedBefore clears the purged set outright, as
       AccountTokenRepository does, and the expiry sweep's cutoff is
       inclusive, matching the flows' !isAfter(now) boundary.
Author review: Leong Wei Zhi to review via the PR.
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

import foc.user.entity.PendingEmailChange;
import jakarta.persistence.LockModeType;

@Repository
public interface PendingEmailChangeRepository extends JpaRepository<PendingEmailChange, Long> {

    // plain read, for tests and inspection: the flows that write a row
    // (PATCH, verify, resend) go through the locked finder below
    Optional<PendingEmailChange> findByUserId(Long userId);

    // verify reads-then-writes the attempts counter, and PATCH/resend
    // read-then-replace the code; the row lock makes concurrent requests
    // for one account queue, so no increment and no code is lost
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PendingEmailChange p WHERE p.userId = :userId")
    Optional<PendingEmailChange> findWithLockByUserId(@Param("userId") Long userId);

    // removes the pending changes of accounts whose recovery window has
    // passed, whatever their expiry, so the purge's users delete can't
    // trip the user_id FK (AccountTokenRepository's shape; bulk delete
    // statements can't join, hence the subquery)
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PendingEmailChange p WHERE p.userId IN"
        + " (SELECT u.id FROM User u WHERE u.deletedAt < :cutoff)")
    int deleteByUserDeletedBefore(@Param("cutoff") Instant cutoff);

    // hygiene only: an expired row is also rejected on sight at verify
    // time. Cutoff inclusive, because the flow treats an expiry exactly
    // at now as already expired (!isAfter(now))
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PendingEmailChange p WHERE p.expiresAt <= :cutoff")
    int deleteByExpiresAtNotAfter(@Param("cutoff") Instant cutoff);
}
