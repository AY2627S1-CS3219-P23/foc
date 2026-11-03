/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
 * Scope: a copy of rating-service's PostgresTestContainer (itself
 * order-service's); every chat-service test that needs the database
 * extends it, since the schema is Postgres-only (partial index, bytea).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// One Postgres container for the whole test run, the same major version
// as chat-db in compose.yaml.
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
