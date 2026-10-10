/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-09-25.
 * Scope: shared Testcontainers Postgres for the user-service test classes
 * (PR #131 review), replacing the per-class container declarations.
 * Author review: Ryan reviewed and ran the full test suite.
 * 2026-10-05, Claude Code (Opus 5.5), issue #154: the "test" profile and
 * the demo-seeder switch set here for every test class.
 * 2026-10-09, Claude Code (Opus 5.5), PR #162 review (Leong Wei Zhi): the values the tests
 * assert on are pinned here too, so exported or empty environment
 * variables can't change them or stop the context starting.
 * 2026-10-10, Claude Code (Opus 5.5), PR #162 re-review (Leong Wei Zhi):
 * user.retention.purge-cron and owner.setup.token pinned too (the token
 * moved here from application-test.yaml).
 * Author review: Ryan to review via the PR.
 */
package foc.user;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

// One Postgres container for the whole test run. Subclasses keep their own
// test slice annotation (@SpringBootTest, @DataJpaTest).
// "test" profile: application-test.yaml on top of the main application.yaml.
// The seeder switch is pinned off here rather than in that file, because
// USER_SEED_DEMO in the environment outranks config files (relaxed binding)
// and would seed the shared test database. The values the tests assert on
// are pinned here for the same reason: the main yaml reads them from
// ${ENV:default} placeholders, so an exported value would break the
// assertions, and an empty one (as .env.example ships) is used as-is
// rather than falling back to the default, which stops the context
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "user.seed.demo=false",
    "owner.setup.token=test-owner-setup-token",
    "user.retention.days=30",
    "user.retention.purge-cron=0 0 3 * * *",
    "user.jwt.access-token-ttl=1h",
    "user.web-allowed-origin=http://localhost:5173",
    "user.mail.from=no-reply@foc.local",
    "user.otp.ttl=10m",
    "user.otp.max-attempts=5",
    "spring.mail.host=localhost",
    "spring.mail.port=1025"
})
public abstract class PostgresTestContainer {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static {
        postgres.start();
    }
}
