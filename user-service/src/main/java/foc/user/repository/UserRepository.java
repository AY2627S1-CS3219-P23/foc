/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated repository queries (owner count, uniqueness checks) and the
       advisory-lock query guarding concurrent owner setup.
Author review: Ryan validated that the endpoint logic matches the feature design.
File renamed from userRepository.java to match the public type (Claude Code, Opus 5.5, 2026-09-23).
2026-09-23 (Claude Code, Fable 5), issue #86: countByRole takes the Role enum,
following the entity's String-to-enum conversion.
2026-09-23 (Claude Code, Opus 5.5), issue #95: findByIdAndDeletedAtIsNull added
for profile lookups that skip soft-deleted users.
2026-09-25 (Claude Code, Opus 5.5), PR #131 review: soft-delete convention
documented (team decision: deleted accounts stay reserved for recovery).
2026-09-25 (Claude Code, Opus 5.5): comments on countByRole and the setup lock
updated after owner setup stopped checking for an existing owner; countByRole
noted as kept temporarily for #96 (PR #132 review).
2026-09-26 (Claude Code, Opus 5.5), issue #96: JpaSpecificationExecutor added
for the admin user list (search, role filter, sorting, paging). countByRole
removed: #96 has no admin-count guard, so nothing uses it.
2026-09-27 (Claude Code, Fable 5), issue #93: deleteByDeletedBefore added for
the day-31 purge scheduler (bulk @Modifying query, following the
notification-service purge pattern from PR #81).
2026-09-27 (Claude Code, Opus 5.5), PR #135 review: getActiveUser added so
AdminService and ProfileService share one active-user lookup.
2026-09-29 (Claude Code, Opus 5.5), issue #89: findByUsername added for
username-or-email login.
2026-09-29 (Claude Code, Opus 5.5), PR #141 review: username lookups ignore
case (team decision): existsByUsernameIgnoreCase / findByUsernameIgnoreCase
replace the exact-match versions.
2026-09-29 (Claude Code, Opus 5.5), issue #147: the username lookups compare
lower(username), matching the V2 migration's unique index.
2026-09-30 (Claude Code, Fable 5), issue #92: except-self uniqueness checks
added for the account-update flows (NewAccountDetails' except-self variants).
2026-10-05 (Claude Code, Fable 5), PR #157 Copilot review: locked
active-user lookup added; the account-update flows take it first so a
gate code cannot be mailed to a just-replaced address.
*/

package foc.user.repository;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import foc.user.entity.User;
import foc.user.exception.UserNotFoundException;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    // Soft-deleted users stay in the table for the 30-day recovery window.
    // Uniqueness checks (existsByEmail/existsByUsernameIgnoreCase) and findByEmail
    // deliberately include them, so a deleted account's email and username
    // stay reserved. Profile and admin lookups use getActiveUser.

    boolean existsByEmail(String email);

    // usernames are unique ignoring case (the lower(username) unique index,
    // Flyway V2); emails are stored lowercased
    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    // the update flows' variants (#92): the caller's own row doesn't count
    boolean existsByEmailAndIdNot(String email, Long id);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username) and u.id <> :id")
    boolean existsByUsernameIgnoreCaseAndIdNot(@Param("username") String username, @Param("id") Long id);

    Optional<User> findByEmail(String email);

    // also includes soft-deleted users: login recovers them within the
    // retention window (AuthService)
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(@Param("username") String username);

    // active (not soft-deleted) user by id
    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    // the same lookup for the profile and admin services: a missing or
    // soft-deleted user is a 404
    default User getActiveUser(Long id) {
        return findByIdAndDeletedAtIsNull(id).orElseThrow(UserNotFoundException::new);
    }

    // the account-update flows' variant (#92): FOR UPDATE, taken FIRST in
    // every flow so operations on one account serialize in one lock order
    // (user row, then gate/pending rows). Unlocked, a gate code could be
    // read from the row, then mailed after a concurrent email-change
    // verify committed — a valid code sent to the address the account
    // just left (PR #157 Copilot review)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findWithLockByIdAndDeletedAtIsNull(@Param("id") Long id);

    default User getActiveUserWithLock(Long id) {
        return findWithLockByIdAndDeletedAtIsNull(id).orElseThrow(UserNotFoundException::new);
    }

    // serialises owner setup calls so two concurrent requests can't both pass
    // the email/username uniqueness checks
    @Query(value = "SELECT pg_advisory_xact_lock(1000)", nativeQuery = true)
    void acquireSetupLock();

    // bulk-deletes accounts whose 30-day recovery window has passed,
    // releasing their email/username. The caller (AccountPurgeScheduler)
    // provides the transaction and must remove dependent account_tokens
    // rows first — their user_id FK blocks this delete. (The otps table
    // this also used to name went with issue #88.)
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM User u WHERE u.deletedAt < :cutoff")
    int deleteByDeletedBefore(@Param("cutoff") Instant cutoff);
}
