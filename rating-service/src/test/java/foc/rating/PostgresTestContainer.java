/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: a copy of order-service's PostgresTestContainer (Claude Code
 * (Opus 5.5), 2026-10-22), for the Flyway migration test only — the
 * other rating-service tests stay on H2 per the scaffold's choice.
 * Author review: [pending]
 */
package foc.rating;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// One Postgres container for the whole test run, the same major version
// as rating-db in compose.yaml; Flyway builds schemas in it by hand.
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
