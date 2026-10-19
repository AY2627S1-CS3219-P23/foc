/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-11
Scope: AccountTokenRepository, renamed with its table (V7, issue #94 —
       owner decision via options Q&A: one use left, so the table takes
       its siblings' per-operation shape). The queries carry over: the
       per-user locked finder (request renews the row and confirm
       counts attempts under it; both flows lock the USER row first,
       the account-update flows' lock order — no by-hash lookup exists,
       and couldn't, since the stored hash is salted BCrypt), an
       expired-row sweep, and the purged-accounts cleanup created for
       issue #93's day-31 purge (bulk deletes in the purge transaction
       chosen by Leong Wei Zhi via options Q&A, over DB-level ON DELETE
       CASCADE).
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

import foc.user.entity.PasswordResetOtp;
import jakarta.persistence.LockModeType;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    // request renews the account's one live row and confirm counts
    // attempts against it, both under the user lock taken just before
    // (one lock order: user, then this row), so concurrent guesses and
    // renewals for one account queue instead of losing increments or
    // codes
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PasswordResetOtp o WHERE o.user.id = :userId")
    Optional<PasswordResetOtp> findWithLockByUserId(@Param("userId") Long userId);

    // hygiene only: an expired row is also rejected on sight at confirm
    // time, so this sweep just keeps the table from accumulating them
    // (run by the purge scheduler alongside the deleted-account sweep);
    // inclusive of the exact boundary confirm calls expired
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PasswordResetOtp o WHERE o.expiresAt <= :cutoff")
    int deleteByExpiresAtNotAfter(@Param("cutoff") Instant cutoff);

    // removes rows owned by accounts whose recovery window has passed, so
    // the purge's users delete doesn't trip the user_id FK (bulk delete
    // statements can't join, hence the subquery)
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PasswordResetOtp o WHERE o.user IN (SELECT u FROM User u WHERE u.deletedAt < :cutoff)")
    int deleteByUserDeletedBefore(@Param("cutoff") Instant cutoff);
}
