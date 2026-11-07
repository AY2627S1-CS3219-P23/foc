/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-11-07, issue #300.
 * Scope: copied from rating-service's PostgresTestContainer (same
 * postgres:17 image, @ServiceConnection, skipped when Docker is
 * unavailable) so supplier-service's NFR tests run on a real Postgres
 * like the other services' tests. Author review: [pending]
 */
package foc.supplier;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for tests that need a real PostgreSQL instead of the in-memory
 * H2 the other supplier tests use. One container is started per JVM and
 * shared; {@code @ServiceConnection} points the datasource at it, overriding
 * the H2 URL in src/test/resources/application.yaml.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresTestContainer {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            postgres.start();
        }
    }
}
