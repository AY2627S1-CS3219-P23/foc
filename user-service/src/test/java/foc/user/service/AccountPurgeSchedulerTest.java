/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-27
Scope: integration test for issue #93's day-31 purge, following
       notification-service's RetentionPurgeSchedulerTest: the purge method
       is called directly rather than waiting on the cron trigger. Rows are
       seeded with backdated deleted_at values (the column is updatable, so
       no JPQL backdating is needed).
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import foc.user.PostgresTestContainer;
import foc.user.entity.AccountToken;
import foc.user.entity.Otp;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.AccountTokenRepository;
import foc.user.repository.OtpRepository;
import foc.user.repository.UserRepository;

/**
 * The test-configured retention window is 30 days
 * ({@code src/test/resources/application.yaml}). {@code @Transactional}
 * lets the purge's bulk deletes and the assertions share one
 * transaction; the seeding flushes explicitly since bulk JPQL deletes
 * bypass the persistence context.
 */
@SpringBootTest
@Transactional
class AccountPurgeSchedulerTest extends PostgresTestContainer {

    @Autowired
    private AccountPurgeScheduler scheduler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private AccountTokenRepository accountTokenRepository;

    @BeforeEach
    void cleanDatabase() {
        otpRepository.deleteAll();
        accountTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should purge accounts deleted beyond the 30-day window, with their otps and tokens")
    void purgesOnlyAccountsBeyondTheRetentionWindow() {
        User active = saveUser("e1111111@u.nus.edu", "active_user", null);
        User inWindow = saveUser("e2222222@u.nus.edu", "recently_deleted",
            Instant.now().minus(29, ChronoUnit.DAYS));
        User expired = saveUser("e3333333@u.nus.edu", "expired_deleted",
            Instant.now().minus(31, ChronoUnit.DAYS));
        for (User user : new User[] {active, inWindow, expired}) {
            otpRepository.save(new Otp(user, "code_hash", Otp.Purpose.SIGNUP,
                Instant.now().plus(1, ChronoUnit.DAYS)));
            accountTokenRepository.save(new AccountToken(user, "token_hash",
                AccountToken.Kind.RECOVERY, Instant.now().plus(1, ChronoUnit.DAYS)));
        }
        userRepository.flush();

        scheduler.purgeExpiredDeletedAccounts();

        assertThat(userRepository.findAll()).extracting(User::getId)
            .containsExactlyInAnyOrder(active.getId(), inWindow.getId());
        assertThat(otpRepository.findAll()).extracting(otp -> otp.getUser().getId())
            .containsExactlyInAnyOrder(active.getId(), inWindow.getId());
        assertThat(accountTokenRepository.findAll()).extracting(token -> token.getUser().getId())
            .containsExactlyInAnyOrder(active.getId(), inWindow.getId());
    }

    @Test
    @DisplayName("Should release a purged account's email and username for reuse")
    void purgeReleasesIdentifiers() {
        saveUser("e3333333@u.nus.edu", "expired_deleted",
            Instant.now().minus(31, ChronoUnit.DAYS));
        userRepository.flush();

        scheduler.purgeExpiredDeletedAccounts();

        // the reuse block is the row itself (unique indexes span all rows);
        // purging it is what frees the identifiers
        assertThat(userRepository.existsByEmail("e3333333@u.nus.edu")).isFalse();
        assertThat(userRepository.existsByUsername("expired_deleted")).isFalse();
        userRepository.save(
            new User("e3333333@u.nus.edu", "expired_deleted", "hashed_password", Role.USER));
        userRepository.flush();
    }

    private User saveUser(String email, String username, Instant deletedAt) {
        User user = new User(email, username, "hashed_password", Role.USER);
        if (deletedAt != null) {
            user.softDelete(deletedAt);
        }
        return userRepository.save(user);
    }
}
