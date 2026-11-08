/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (D36).
 * Scope: a copy of user-service's PostgresTestContainer for the Flyway
 * migration test; the other suites stay on H2 (Flyway off in the test
 * yaml). Author review: Leong Wei Zhi, pending pull request review.
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
