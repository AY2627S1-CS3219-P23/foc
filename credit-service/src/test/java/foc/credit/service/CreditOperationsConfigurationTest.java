/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-11, PR #175 review (Copilot).
 * Scope: the two credit properties: values other than the defaults are
 * the ones applied, and a value below 1 is refused when the service is
 * constructed. Written by the tool from the reviewer's comment.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import foc.credit.PostgresTestContainer;

// Commits, like CreditOperationsServiceTest, whose after-each checks read
// the whole database: fresh IDs, and the doc's invariants kept.
@SpringBootTest(properties = { "credit.sign-up-credits=7", "credit.earned-credit-lifetime-months=1" })
class CreditOperationsConfigurationTest extends PostgresTestContainer {

    private final String requester = "u-req-" + UUID.randomUUID();
    private final String courier = "u-cou-" + UUID.randomUUID();
    private final String request = "r-" + UUID.randomUUID();

    @Autowired
    private CreditOperations operations;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theConfiguredSignUpAmountAndLifetimeAreApplied() {
        assertThat(operations.getOrCreate(requester).getAvailable()).isEqualTo(7);

        operations.reserve(request, requester, 2);
        operations.transfer(request, courier);

        assertThat(operations.getOrCreate(courier).getAvailable()).isEqualTo(9);
        Instant earnedAt = jdbc.queryForObject(
            "select earned_at from credit_lot where owner_id = ? and expires_at is not null",
            (rs, row) -> rs.getTimestamp(1).toInstant(), courier);
        Instant expiresAt = jdbc.queryForObject(
            "select expires_at from credit_lot where owner_id = ? and expires_at is not null",
            (rs, row) -> rs.getTimestamp(1).toInstant(), courier);
        assertThat(expiresAt).isEqualTo(earnedAt.atOffset(ZoneOffset.UTC).plusMonths(1).toInstant());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1 })
    void aSignUpAmountBelowOneIsRefused(int signUpCredits) {
        assertThatThrownBy(() -> new CreditOperationsService(null, null, null, null, null, signUpCredits, 3))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("credit.sign-up-credits");
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1 })
    void aLifetimeBelowOneMonthIsRefused(int lifetimeMonths) {
        assertThatThrownBy(() -> new CreditOperationsService(null, null, null, null, null, 5, lifetimeMonths))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("credit.earned-credit-lifetime-months");
    }
}
