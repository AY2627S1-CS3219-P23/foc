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
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.user.entity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import foc.user.PostgresTestContainer;
import jakarta.persistence.EntityManager;

/**
 * Persists and reloads one row per entity so a mapping mistake (wrong
 * nullability, bad column definition, broken enum or FK mapping)
 * fails here rather than in the feature issues (#87+).
 */
@DataJpaTest
class EntityMappingTest extends PostgresTestContainer {

    private static final Instant EXPIRY = Instant.parse("2026-09-24T12:00:00Z");

    @Autowired
    private EntityManager entityManager;

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
    void otpRoundTrip() {
        User user = persistUser("e2234567@u.nus.edu", "otp_user");
        Otp saved = persistAndFlush(
            new Otp(user, "code_hash", Otp.Purpose.SIGNUP, EXPIRY));
        entityManager.clear();

        Otp found = entityManager.find(Otp.class, saved.getId());

        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getCodeHash()).isEqualTo("code_hash");
        assertThat(found.getPurpose()).isEqualTo(Otp.Purpose.SIGNUP);
        assertThat(found.getExpiresAt()).isEqualTo(EXPIRY);
        assertThat(found.getAttempts()).isZero();
    }

    @Test
    void accountTokenRoundTrip() {
        User user = persistUser("e3234567@u.nus.edu", "token_user");
        AccountToken saved = persistAndFlush(
            new AccountToken(user, "token_hash", AccountToken.Kind.RECOVERY, EXPIRY));
        entityManager.clear();

        AccountToken found = entityManager.find(AccountToken.class, saved.getId());

        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getTokenHash()).isEqualTo("token_hash");
        assertThat(found.getKind()).isEqualTo(AccountToken.Kind.RECOVERY);
        assertThat(found.getExpiresAt()).isEqualTo(EXPIRY);
    }

    @Test
    void tokenDenylistRoundTrip() {
        persistAndFlush(new TokenDenylistEntry("jti-123", EXPIRY));
        entityManager.clear();

        TokenDenylistEntry found = entityManager.find(TokenDenylistEntry.class, "jti-123");

        assertThat(found.getJti()).isEqualTo("jti-123");
        assertThat(found.getExpiresAt()).isEqualTo(EXPIRY);
    }
}
