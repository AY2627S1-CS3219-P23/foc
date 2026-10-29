/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: empty Spring Data repository for the scaffold; queries come
 * with the features that need them.
 * 2026-10-11, Claude Code (Opus 5.5), issue #169: the insert-once and
 * locking queries the credit operations need (D2, D6).
 * 2026-10-16, Claude Code (Opus 5.5), issue #173: every account locked
 * in ascending user ID order, for the monthly redistribution.
 * 2026-10-28, Claude Code (Opus 5.5), issue #267: the closed placeholder
 * insert, and the redistribution's lock and the pool preview's count
 * narrowed to open accounts (team decisions USR-6, USR-7).
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

    /**
     * Creates a closed account holding nothing unless one exists, for a
     * {@code user.purged} that arrived before the account was ever
     * created (USR-6). Returns 1 if this call created it. Taking the row
     * is what stops a late {@code user.created} or get-or-create from
     * granting the sign-up credits, as both insert only when no row is
     * there.
     */
    @Modifying
    @Query(value = """
        insert into credit_account (user_id, available, reserved, created_at, closed_at)
        values (:userId, 0, 0, :now, :now)
        on conflict (user_id) do nothing
        """, nativeQuery = true)
    int insertClosedIfAbsent(@Param("userId") String userId, @Param("now") Instant now);

    // the row lock a balance change is made under (D2)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditAccount a where a.userId = :userId")
    Optional<CreditAccount> findForUpdate(@Param("userId") String userId);

    /**
     * Every open account, locked in ascending user ID order (D2), for the
     * redistribution: a closed one gets no share (USR-7).
     * {@link #countOpen} is the same set, for the pool preview. Postgres locks the rows after sorting them. The
     * "C" collation compares by code point, the order Java's
     * {@code String.compareTo} gives the credit operations' transfer;
     * the database's default collation could order two IDs the other
     * way round and deadlock against it.
     */
    @Query(value = """
        select * from credit_account where closed_at is null
        order by user_id collate "C" for update
        """, nativeQuery = true)
    List<CreditAccount> findAllOpenForUpdate();

    // the accounts a redistribution now would share among (USR-7)
    @Query("select count(a) from CreditAccount a where a.closedAt is null")
    long countOpen();
}
