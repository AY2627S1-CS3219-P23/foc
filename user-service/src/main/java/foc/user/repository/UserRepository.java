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
*/

package foc.user.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import foc.user.entity.User;
import foc.user.exception.UserNotFoundException;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    // Soft-deleted users stay in the table for the 30-day recovery window.
    // Uniqueness checks (existsByEmail/existsByUsername) and findByEmail
    // deliberately include them, so a deleted account's email and username
    // stay reserved. Profile and admin lookups use getActiveUser.

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    // also includes soft-deleted users: login recovers them within the
    // retention window (AuthService)
    Optional<User> findByUsername(String username);

    // active (not soft-deleted) user by id
    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    // the same lookup for the profile and admin services: a missing or
    // soft-deleted user is a 404
    default User getActiveUser(Long id) {
        return findByIdAndDeletedAtIsNull(id).orElseThrow(UserNotFoundException::new);
    }

    // serialises owner setup calls so two concurrent requests can't both pass
    // the email/username uniqueness checks
    @Query(value = "SELECT pg_advisory_xact_lock(1000)", nativeQuery = true)
    void acquireSetupLock();

    // bulk-deletes accounts whose 30-day recovery window has passed,
    // releasing their email/username. The caller (AccountPurgeScheduler)
    // provides the transaction and must remove dependent otps /
    // account_tokens rows first — their user_id FKs block this delete.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM User u WHERE u.deletedAt < :cutoff")
    int deleteByDeletedBefore(@Param("cutoff") Instant cutoff);
}
