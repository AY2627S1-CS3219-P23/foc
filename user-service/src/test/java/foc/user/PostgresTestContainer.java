/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-09-25.
 * Scope: shared Testcontainers Postgres for the user-service test classes
 * (PR #131 review), replacing the per-class container declarations.
 * Author review: Ryan reviewed and ran the full test suite.
 */
package foc.user;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

// One Postgres container for the whole test run. Subclasses keep their own
// test slice annotation (@SpringBootTest, @DataJpaTest).
public abstract class PostgresTestContainer {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static {
        postgres.start();
    }
}
