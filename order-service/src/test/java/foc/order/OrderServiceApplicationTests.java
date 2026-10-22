/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: scaffolded following the supplier-service / notification-service
 * pattern; runs against in-memory H2 (test application.yaml).
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-22, Claude Code (Opus 5.5), issue #238: runs against
 * Testcontainers Postgres built by Flyway (extends PostgresTestContainer)
 * instead of H2. Author review: Ryan Ang, pending pull request review.
 */
package foc.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderServiceApplicationTests extends PostgresTestContainer {

	@Test
	void contextLoads() {
	}

}
