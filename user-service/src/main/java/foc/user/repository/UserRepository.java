/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated repository queries (owner count, uniqueness checks) and the
       advisory-lock query guarding concurrent owner setup.
Author review: Ryan validated that the endpoint logic matches the feature design.
File renamed from userRepository.java to match the public type (Claude Code, Opus 5.5, 2026-09-23).
2026-09-23 (Claude Code, Fable 5), issue #86: countByRole takes the Role enum,
following the entity's String-to-enum conversion.
*/

package foc.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import foc.user.entity.Role;
import foc.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // checks whether there are any owners yet
    long countByRole(Role role);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    // meant to guard against concurrent calls to prevent double owner creation
    @Query(value = "SELECT pg_advisory_xact_lock(1000)", nativeQuery = true)
    void acquireSetupLock();
}
