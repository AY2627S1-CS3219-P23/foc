/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: repository for issue #92's account-update gate codes, modeled on
       PendingSignupRepository: a locked finder so request and consume
       serialize per account, and a bulk expiry sweep for the purge
       scheduler.
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

import foc.user.entity.AccountUpdateOtp;
import jakarta.persistence.LockModeType;

@Repository
public interface AccountUpdateOtpRepository extends JpaRepository<AccountUpdateOtp, Long> {

    // plain read, for tests and inspection: request and consume both go
    // through the locked finder below
    Optional<AccountUpdateOtp> findByUserId(Long userId);

    // consuming reads-then-writes the attempts counter, and a repeat
    // request reads-then-replaces the code; the row lock makes
    // concurrent requests for one account queue
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM AccountUpdateOtp o WHERE o.userId = :userId")
    Optional<AccountUpdateOtp> findWithLockByUserId(@Param("userId") Long userId);

    // hygiene only: an expired row is also rejected on sight when
    // presented. Runs before the purge's users delete so no row of a
    // purged account survives to trip the user_id FK
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AccountUpdateOtp o WHERE o.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
