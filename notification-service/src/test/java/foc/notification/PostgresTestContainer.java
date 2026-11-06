/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-06.
 * Scope: issue #48 (DP10): notification-service safe at multiple replicas
 * (team decision, Leong Wei Zhi via options Q&A). Shared Testcontainers
 * Postgres for the tests that need the real database (Flyway's V1/V2,
 * Hibernate validate, ShedLock, the concurrent dedupe), a copy of
 * supplier-service's PostgresTestContainer. The other notification tests
 * stay on H2.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// One Postgres container for every subclass, the same major version as
// notification-db in compose.yaml. Subclasses add @SpringBootTest with
// FLYWAY_ON and DDL_VALIDATE, so the context runs as in production: Flyway
// builds the schema, Hibernate only validates. The @ServiceConnection
// replaces the H2 datasource from the test application.yaml.
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresTestContainer {

	public static final String FLYWAY_ON = "spring.flyway.enabled=true";
	public static final String DDL_VALIDATE = "spring.jpa.hibernate.ddl-auto=validate";

	@ServiceConnection
	protected static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	static {
		// The extension skips subclasses without Docker; guard the start too.
		if (DockerClientFactory.instance().isDockerAvailable()) {
			postgres.start();
		}
	}
}
