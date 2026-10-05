/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-09-25.
 * Scope: shared Testcontainers Postgres for the user-service test classes
 * (PR #131 review), replacing the per-class container declarations.
 * Author review: Ryan reviewed and ran the full test suite.
 * 2026-10-05, Claude Code (Opus 5.5), issue #154: the "test" profile and
 * the demo-seeder switch set here for every test class.
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
// and would seed the shared test database
@ActiveProfiles("test")
@TestPropertySource(properties = "user.seed.demo=false")
public abstract class PostgresTestContainer {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static {
        postgres.start();
    }
}
