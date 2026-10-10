/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-20.
 * Scope: scaffolded for issue #84 following the notification-service
 * pattern.
 * 2026-09-22, Claude Code (Sonnet 4.6), Ryan Ang: Testcontainers Postgres
 * @ServiceConnection wiring added so the context has a datasource.
 * 2026-09-23, Claude Code (Opus 5.5): moved to the Testcontainers 2.x
 * PostgreSQLContainer (PR #126 review).
 * 2026-09-25, Claude Code (Opus 5.5): container moved to the shared
 * PostgresTestContainer base (PR #131 review).
 * 2026-09-29, Claude Code (Opus 5.5): demoSeederOffByDefault — the
 * demo-account seeder isn't created unless USER_SEED_DEMO is set. PR #155
 * Copilot review: user.seed.demo pinned to false on this context, so a
 * USER_SEED_DEMO=true environment can't change the result.
 * 2026-10-05, Claude Code (Opus 5.5), issue #154: that pin moved to
 * PostgresTestContainer, so it covers every test class.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.user;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import foc.user.seed.DemoAccountsSeeder;

// the seeder switch is pinned off in PostgresTestContainer
@SpringBootTest
class UserServiceApplicationTests extends PostgresTestContainer {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
	}

	// demo accounts only when USER_SEED_DEMO is set (team decision)
	@Test
	void demoSeederOffByDefault() {
		assertThat(context.getBeanProvider(DemoAccountsSeeder.class).getIfAvailable()).isNull();
	}

}
