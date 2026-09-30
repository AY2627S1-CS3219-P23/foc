/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-27
Scope: integration test for issue #93's day-31 purge, following
       notification-service's RetentionPurgeSchedulerTest: the purge method
       is called directly rather than waiting on the cron trigger. Rows are
       seeded with backdated deleted_at values (the column is updatable, so
       no JPQL backdating is needed).
Reviewed by: Leong Wei Zhi (via pull request).
2026-09-29 (Claude Code, Opus 5.5), PR #141 review: username checks use
       existsByUsernameIgnoreCase (usernames now ignore case, team decision).
2026-09-29 (Claude Code, Fable 5), issue #88: the otps seeding went with
       the otps table; a case for the expired pending sign-up sweep the
       same purge run now performs is added instead.
2026-09-30 (Claude Code, Fable 5), issue #92: cases for the gate-code and
       pending-email-change sweeps, including the FK-ordering proof — a
       purged user's (necessarily expired) rows go first, so the users
       delete never trips their user_id FKs.
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
import foc.user.entity.AccountUpdateOtp;
import foc.user.entity.PendingEmailChange;
import foc.user.entity.PendingSignup;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.AccountTokenRepository;
import foc.user.repository.AccountUpdateOtpRepository;
import foc.user.repository.PendingEmailChangeRepository;
import foc.user.repository.PendingSignupRepository;
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
    private AccountTokenRepository accountTokenRepository;

    @Autowired
    private PendingSignupRepository pendingSignupRepository;

    @Autowired
    private AccountUpdateOtpRepository accountUpdateOtpRepository;

    @Autowired
    private PendingEmailChangeRepository pendingEmailChangeRepository;

    @BeforeEach
    void cleanDatabase() {
        accountTokenRepository.deleteAll();
        pendingSignupRepository.deleteAll();
        accountUpdateOtpRepository.deleteAll();
        pendingEmailChangeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should purge accounts deleted beyond the 30-day window, with their tokens")
    void purgesOnlyAccountsBeyondTheRetentionWindow() {
        User active = saveUser("e1111111@u.nus.edu", "active_user", null);
        User inWindow = saveUser("e2222222@u.nus.edu", "recently_deleted",
            Instant.now().minus(29, ChronoUnit.DAYS));
        User expired = saveUser("e3333333@u.nus.edu", "expired_deleted",
            Instant.now().minus(31, ChronoUnit.DAYS));
        for (User user : new User[] {active, inWindow, expired}) {
            accountTokenRepository.save(new AccountToken(user, "token_hash",
                AccountToken.Kind.RECOVERY, Instant.now().plus(1, ChronoUnit.DAYS)));
        }
        userRepository.flush();

        scheduler.purgeExpiredDeletedAccounts();

        assertThat(userRepository.findAll()).extracting(User::getId)
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
        assertThat(userRepository.existsByUsernameIgnoreCase("expired_deleted")).isFalse();
        userRepository.save(
            new User("e3333333@u.nus.edu", "expired_deleted", "hashed_password", Role.USER));
        userRepository.flush();
    }

    @Test
    @DisplayName("Should sweep expired pending sign-ups and keep live ones")
    void sweepsExpiredPendingSignups() {
        pendingSignupRepository.save(new PendingSignup("e4444444@u.nus.edu", "expired_pending",
            "password_hash", "code_hash", Instant.now().minusSeconds(660), Instant.now().minusSeconds(60)));
        PendingSignup live = pendingSignupRepository.save(
            new PendingSignup("e5555555@u.nus.edu", "live_pending",
                "password_hash", "code_hash", Instant.now(), Instant.now().plusSeconds(600)));
        pendingSignupRepository.flush();

        scheduler.purgeExpiredDeletedAccounts();

        assertThat(pendingSignupRepository.findAll()).extracting(PendingSignup::getId)
            .containsExactly(live.getId());
    }

    @Test
    @DisplayName("Should sweep expired gate codes and pending email changes, keeping live ones")
    void sweepsExpiredAccountUpdateRows() {
        User alex = saveUser("e1111111@u.nus.edu", "active_user", null);
        accountUpdateOtpRepository.save(new AccountUpdateOtp(alex.getId(), "code_hash",
            Instant.now().minusSeconds(660), Instant.now().minusSeconds(60)));
        pendingEmailChangeRepository.save(new PendingEmailChange(alex.getId(),
            "e2222222@u.nus.edu", "code_hash",
            Instant.now().minusSeconds(660), Instant.now().minusSeconds(60)));
        User bob = saveUser("e3333333@u.nus.edu", "other_user", null);
        AccountUpdateOtp liveGate = accountUpdateOtpRepository.save(new AccountUpdateOtp(
            bob.getId(), "code_hash", Instant.now(), Instant.now().plusSeconds(600)));
        PendingEmailChange liveChange = pendingEmailChangeRepository.save(new PendingEmailChange(
            bob.getId(), "e4444444@u.nus.edu", "code_hash",
            Instant.now(), Instant.now().plusSeconds(600)));
        userRepository.flush();

        scheduler.purgeExpiredDeletedAccounts();

        assertThat(accountUpdateOtpRepository.findAll()).extracting(AccountUpdateOtp::getId)
            .containsExactly(liveGate.getId());
        assertThat(pendingEmailChangeRepository.findAll()).extracting(PendingEmailChange::getId)
            .containsExactly(liveChange.getId());
    }

    @Test
    @DisplayName("A purged account's expired #92 rows never trip its FKs")
    void purgedAccountsRowsGoFirst() {
        User expired = saveUser("e3333333@u.nus.edu", "expired_deleted",
            Instant.now().minus(31, ChronoUnit.DAYS));
        // whatever such an account left behind expired within its TTL,
        // weeks before day 31 — seeded here as the purge will find it
        accountUpdateOtpRepository.save(new AccountUpdateOtp(expired.getId(), "code_hash",
            Instant.now().minus(31, ChronoUnit.DAYS), Instant.now().minus(31, ChronoUnit.DAYS).plusSeconds(600)));
        pendingEmailChangeRepository.save(new PendingEmailChange(expired.getId(),
            "e4444444@u.nus.edu", "code_hash",
            Instant.now().minus(31, ChronoUnit.DAYS), Instant.now().minus(31, ChronoUnit.DAYS).plusSeconds(600)));
        userRepository.flush();

        scheduler.purgeExpiredDeletedAccounts();

        assertThat(userRepository.findAll()).isEmpty();
        assertThat(accountUpdateOtpRepository.findAll()).isEmpty();
        assertThat(pendingEmailChangeRepository.findAll()).isEmpty();
    }

    private User saveUser(String email, String username, Instant deletedAt) {
        User user = new User(email, username, "hashed_password", Role.USER);
        if (deletedAt != null) {
            user.softDelete(deletedAt);
        }
        return userRepository.save(user);
    }
}
