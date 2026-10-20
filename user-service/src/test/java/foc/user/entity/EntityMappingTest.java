/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-23.
 * Scope: persistence round-trip tests for issue #86 (scope chosen by
 * Leong Wei Zhi via options Q&A: boot-context plus per-entity
 * round-trips), following the merged Testcontainers pattern (PR #126)
 * so mappings are exercised against real PostgreSQL, not an embedded
 * substitute.
 * 2026-09-25, Claude Code (Opus 5.5): container moved to the shared
 * PostgresTestContainer base (PR #131 review).
 * 2026-09-29, Claude Code (Fable 5), issue #88: the Otp round-trip went
 * with the otps table; PendingSignup round-trip and its email-unique
 * violation added in its place.
 * 2026-09-29, Claude Code (Opus 5), PR #150 review: last_sent_at (the
 * resend cooldown's origin) is part of that round-trip.
 * 2026-09-29, Claude Code (Opus 5.5), issue #147: tables emptied before
 * each test, since the Flyway-built schema (unlike create-drop) keeps
 * rows other test classes committed.
 * 2026-09-30, Claude Code (Opus 5), merging main: that sweep clears
 * pending_signups instead of the otps table #88 dropped.
 * 2026-10-11, Claude Code (Fable 5), issue #151: the TokenDenylistEntry
 * round-trip went with the entity and its table (V6 — the denylist moved
 * to Redis TTL keys checked by the API gateway).
 * Reviewed by: Leong Wei Zhi (via pull request).
 * 2026-10-11, Claude Code (Fable 5), issue #94: the PasswordResetOtp
 * round-trip (the AccountToken scaffold, remade by V7 for the reset
 * flow) covers createdAt and attempts, and a case pins the
 * one-live-code-per-user index. No unique-hash case on purpose: the
 * credential is an OTP code, whose salted hash is never looked up.
 * 2026-10-20 (Claude Code, Fable 5), issue #177: pending_signups rows
 * carry the role they verify into (V8); the round-trip pins it.
 */
package foc.user.entity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import foc.user.PostgresTestContainer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * Persists and reloads one row per entity so a mapping mistake (wrong
 * nullability, bad column definition, broken enum or FK mapping)
 * fails here rather than in the feature issues (#87+).
 */
@DataJpaTest
class EntityMappingTest extends PostgresTestContainer {

    private static final Instant SENT_AT = Instant.parse("2026-09-24T11:50:00Z");
    private static final Instant EXPIRY = Instant.parse("2026-09-24T12:00:00Z");

    @Autowired
    private EntityManager entityManager;

    // rows committed by other test classes; the delete rolls back with the test
    @BeforeEach
    void emptyTables() {
        entityManager.createNativeQuery("delete from pending_signups").executeUpdate();
        entityManager.createNativeQuery("delete from password_reset_otps").executeUpdate();
        entityManager.createNativeQuery("delete from users").executeUpdate();
    }

    private <T> T persistAndFlush(T entity) {
        entityManager.persist(entity);
        entityManager.flush();
        return entity;
    }

    private User persistUser(String email, String username) {
        return persistAndFlush(new User(email, username, "bcrypt_hash", Role.USER));
    }

    @Test
    void userRoundTrip() {
        User saved = persistUser("e1234567@u.nus.edu", "round_trip");
        entityManager.clear();

        User found = entityManager.find(User.class, saved.getId());

        assertThat(found.getEmail()).isEqualTo("e1234567@u.nus.edu");
        assertThat(found.getUsername()).isEqualTo("round_trip");
        assertThat(found.getPasswordHash()).isEqualTo("bcrypt_hash");
        assertThat(found.getRole()).isEqualTo(Role.USER);
        assertThat(found.getFailedLoginAttempts()).isZero();
        assertThat(found.getLockedUntil()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getDeletedAt()).isNull();
        assertThat(found.isActive()).isTrue();
    }

    @Test
    void pendingSignupRoundTrip() {
        PendingSignup saved = persistAndFlush(new PendingSignup(
            "e2234567@u.nus.edu", "pending_user", "bcrypt_hash", "code_hash", SENT_AT, EXPIRY, Role.USER));
        entityManager.clear();

        PendingSignup found = entityManager.find(PendingSignup.class, saved.getId());

        assertThat(found.getEmail()).isEqualTo("e2234567@u.nus.edu");
        assertThat(found.getUsername()).isEqualTo("pending_user");
        assertThat(found.getPasswordHash()).isEqualTo("bcrypt_hash");
        assertThat(found.getCodeHash()).isEqualTo("code_hash");
        assertThat(found.getLastSentAt()).isEqualTo(SENT_AT);
        assertThat(found.getExpiresAt()).isEqualTo(EXPIRY);
        assertThat(found.getAttempts()).isZero();
        assertThat(found.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void pendingSignupEmailIsUnique() {
        persistAndFlush(new PendingSignup(
            "e2234567@u.nus.edu", "first_user", "bcrypt_hash", "code_hash", SENT_AT, EXPIRY, Role.USER));

        // one pending sign-up per email: the second insert must trip the
        // unique index (usernames may repeat — users' index decides those)
        assertThatThrownBy(() -> persistAndFlush(new PendingSignup(
                "e2234567@u.nus.edu", "second_user", "bcrypt_hash", "code_hash", SENT_AT, EXPIRY, Role.USER)))
            .isInstanceOf(PersistenceException.class);
    }

    @Test
    void passwordResetOtpRoundTrip() {
        User user = persistUser("e3234567@u.nus.edu", "token_user");
        PasswordResetOtp otp = new PasswordResetOtp(user, "code_hash", SENT_AT, EXPIRY);
        otp.incrementAttempts();
        PasswordResetOtp saved = persistAndFlush(otp);
        entityManager.clear();

        PasswordResetOtp found = entityManager.find(PasswordResetOtp.class, saved.getId());

        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getCodeHash()).isEqualTo("code_hash");
        assertThat(found.getCreatedAt()).isEqualTo(SENT_AT);
        assertThat(found.getExpiresAt()).isEqualTo(EXPIRY);
        assertThat(found.getAttempts()).isEqualTo(1);
    }

    @Test
    void passwordResetOtpOnePerUser() {
        // V7: one live reset code per account — a repeat request renews
        // the row instead of inserting a second
        User user = persistUser("e6234567@u.nus.edu", "one_token_user");
        persistAndFlush(new PasswordResetOtp(user, "first_hash", SENT_AT, EXPIRY));

        assertThatThrownBy(() -> persistAndFlush(new PasswordResetOtp(user, "second_hash",
                SENT_AT, EXPIRY)))
            .isInstanceOf(PersistenceException.class);
    }

}
