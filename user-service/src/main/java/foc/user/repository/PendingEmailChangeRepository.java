/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: repository for issue #92's pending email changes, modeled on
       PendingSignupRepository: a locked finder so the flows that
       read-then-write a row (request, verify, resend) serialize per
       account, and a bulk expiry sweep for the purge scheduler.
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

    // hygiene only: an expired row is also rejected on sight at verify
    // time. Runs before the purge's users delete so no row of a purged
    // account survives to trip the user_id FK
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PendingEmailChange p WHERE p.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
