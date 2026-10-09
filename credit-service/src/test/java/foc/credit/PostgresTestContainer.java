/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-09.
 * Scope: shared Testcontainers Postgres for the credit-service test
 * classes, following user-service's PostgresTestContainer (one container
 * per run, the "test" profile on top of the main application.yaml).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

// One Postgres container for the whole test run, the same major version as
// credit-db in compose.yaml. Subclasses keep their own test slice annotation.
// "test" profile: application-test.yaml on top of the main application.yaml,
// so the tests run Flyway and the open-in-view setting as in production.
@ActiveProfiles("test")
public abstract class PostgresTestContainer {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static {
        postgres.start();
    }
}
