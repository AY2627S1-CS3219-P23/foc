/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-20.
 * Scope: scaffolded for issue #84 following the notification-service
 * pattern.
 * 2026-09-22, Claude Code (Sonnet 4.6), Ryan Ang: Testcontainers Postgres
 * @ServiceConnection wiring added so the context has a datasource.
 * 2026-09-23, Claude Code (Opus 5.5): moved to the Testcontainers 2.x
 * PostgreSQLContainer (PR #126 review).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class UserServiceApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Test
	void contextLoads() {
	}

}
