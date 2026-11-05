/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): Flyway baseline / advisory-locked seeding for
 * multi-replica starts (team decision, Leong Wei Zhi via options Q&A).
 * Shared Testcontainers Postgres for the tests that need the real
 * database (Flyway's V1, Hibernate validate, the seeding advisory lock),
 * following order-service's PostgresTestContainer. The other supplier
 * tests stay on H2.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.supplier;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// One Postgres container for every subclass, the same major version as
// supplier-db in compose.yaml. Subclasses add @SpringBootTest with
// POSTGRES_PROPERTIES so they share one cached context: Flyway on, ddl-auto
// validate, as in production. The @ServiceConnection replaces the H2
// datasource from the test application.yaml.
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresTestContainer {

    public static final String FLYWAY_ON = "spring.flyway.enabled=true";
    public static final String DDL_VALIDATE = "spring.jpa.hibernate.ddl-auto=validate";

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    static {
        // The extension skips subclasses without Docker; guard the start too.
        if (DockerClientFactory.instance().isDockerAvailable()) {
            postgres.start();
        }
    }
}
