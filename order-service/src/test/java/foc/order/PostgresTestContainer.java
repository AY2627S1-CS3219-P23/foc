/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-22.
 * Scope: issue #238: shared Testcontainers Postgres for the order-service
 * test classes, replacing in-memory H2. Follows credit-service's
 * PostgresTestContainer (one container per run, postgres:17 as in
 * compose.yaml) and user-service's skip without Docker.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.order;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// One Postgres container for the whole test run, the same major version as
// order-db in compose.yaml; Flyway builds the schema in it. Subclasses keep
// their own test annotation (@SpringBootTest). The container is shared by
// every test class, so rows one class stores are still there for the next:
// tests use their own ids rather than counting all rows.
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresTestContainer {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static {
        // The extension skips subclasses without Docker; guard the start too.
        if (DockerClientFactory.instance().isDockerAvailable()) {
            postgres.start();
        }
    }
}
