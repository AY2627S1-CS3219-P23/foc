/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-27
Scope: Generated tests for optimistic locking on users (PR #135 review:
       a role change racing a delete could write deleted_at = null back).
       Optimistic locking per team decision. Covers both save paths: a
       managed entity flushed at commit (AdminService) and a detached
       entity merged by save (ProfileService).
Author review: Ryan to review via the PR.
*/

package foc.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import foc.user.PostgresTestContainer;
import foc.user.entity.Role;
import foc.user.entity.User;

@SpringBootTest
class UserOptimisticLockTest extends PostgresTestContainer {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long userId;

    @BeforeEach
    void seedUser() {
        userRepository.deleteAll();
        userId = userRepository.save(
            new User("e1234567@u.nus.edu", "student_alex", "hashed_password", Role.USER)).getId();
    }

    @Test
    @DisplayName("A role change loaded before a committed delete should fail instead of undoing it")
    void staleManagedUpdateFails() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            User stale = userRepository.findById(userId).orElseThrow();
            // another request deletes the user and commits first
            concurrent.executeWithoutResult(inner -> {
                User fresh = userRepository.findById(userId).orElseThrow();
                fresh.softDelete(Instant.now());
            });
            stale.setRole(Role.ADMIN);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        User reloaded = userRepository.findById(userId).orElseThrow();
        assertThat(reloaded.getDeletedAt()).isNotNull();
        assertThat(reloaded.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("Saving a stale detached copy should fail instead of overwriting a newer save")
    void staleDetachedSaveFails() {
        User first = userRepository.findById(userId).orElseThrow();
        User second = userRepository.findById(userId).orElseThrow();

        first.setRole(Role.ADMIN);
        userRepository.save(first);

        second.softDelete(Instant.now());
        assertThatThrownBy(() -> userRepository.save(second))
            .isInstanceOf(OptimisticLockingFailureException.class);

        User reloaded = userRepository.findById(userId).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.ADMIN);
        assertThat(reloaded.getDeletedAt()).isNull();
    }
}
