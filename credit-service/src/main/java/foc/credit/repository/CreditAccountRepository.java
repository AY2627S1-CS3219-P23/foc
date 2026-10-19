/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: empty Spring Data repository for the scaffold; queries come
 * with the features that need them.
 * 2026-10-11, Claude Code (Opus 5.5), issue #169: the insert-once and
 * locking queries the credit operations need (D2, D6).
 * 2026-10-16, Claude Code (Opus 5.5), issue #173: every account locked
 * in ascending user ID order, for the monthly redistribution.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import foc.credit.entity.CreditAccount;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, String> {

    /**
     * Creates the user's account unless one exists (D6). Returns 1 if this
     * call created it and 0 otherwise; a concurrent first call for the same
     * user waits for the other's commit and then gets 0, so the sign-up
     * credits are granted once.
     */
    @Modifying
    @Query(value = """
        insert into credit_account (user_id, available, reserved, created_at)
        values (:userId, :available, 0, :createdAt)
        on conflict (user_id) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") String userId, @Param("available") int available,
            @Param("createdAt") Instant createdAt);

    // the row lock a balance change is made under (D2)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditAccount a where a.userId = :userId")
    Optional<CreditAccount> findForUpdate(@Param("userId") String userId);

    /**
     * Every account, locked in ascending user ID order (D2), for the
     * redistribution. Postgres locks the rows after sorting them. The
     * "C" collation compares by code point, the order Java's
     * {@code String.compareTo} gives the credit operations' transfer;
     * the database's default collation could order two IDs the other
     * way round and deadlock against it.
     */
    @Query(value = "select * from credit_account order by user_id collate \"C\" for update",
        nativeQuery = true)
    List<CreditAccount> findAllForUpdate();
}
