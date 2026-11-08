/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (D36, team decision
 * via options Q&A: Flyway replaces ddl-auto: update).
 * Scope: the V1 baseline applies on an empty PostgreSQL and validates
 * against the entities; a pre-Flyway (ddl-auto) database is baselined at
 * V1 with its rows kept. Author review: Leong Wei Zhi, pending pull
 * request review.
 */
package foc.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import foc.notification.PostgresTestContainer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@SpringBootTest(properties = {PostgresTestContainer.FLYWAY_ON, PostgresTestContainer.DDL_VALIDATE})
class NotificationFlywayMigrationTest extends PostgresTestContainer {

	// not the application's schema, so it starts without Flyway history
	private static final String PRE_FLYWAY_SCHEMA = "pre_flyway";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayAppliesTheBaselineAndHibernateValidatesIt() {
		List<Map<String, Object>> history = jdbcTemplate.queryForList(
				"SELECT version, type, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

		assertThat(history).hasSize(1);
		assertThat(history.get(0)).containsEntry("version", "1").containsEntry("type", "SQL")
				.containsEntry("success", true);
		assertThat(jdbcTemplate.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()"
						+ " AND table_name <> 'flyway_schema_history'",
				String.class))
				.containsExactlyInAnyOrder("notifications", "processed_events");
	}

	// The main application.yaml's baseline-on-migrate / baseline-version: 1
	// (the test yaml shadows it, so Flyway is configured the same way here):
	// a ddl-auto volume keeps its tables and rows and is recorded as V1.
	@Test
	void aPreFlywayDatabaseIsBaselinedAtV1WithItsRowsKept() throws Exception {
		jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + PRE_FLYWAY_SCHEMA + " CASCADE");
		jdbcTemplate.execute("CREATE SCHEMA " + PRE_FLYWAY_SCHEMA);
		DriverManagerDataSource preFlyway = new DriverManagerDataSource(
				postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?")
						+ "currentSchema=" + PRE_FLYWAY_SCHEMA,
				postgres.getUsername(), postgres.getPassword());
		JdbcTemplate jdbc = new JdbcTemplate(preFlyway);
		// V1 is pg_dump-identical to what ddl-auto: update built, so running
		// it by hand, outside Flyway, stands in for a pre-Flyway volume
		jdbc.execute(new ClassPathResource("db/migration/V1__baseline.sql")
				.getContentAsString(StandardCharsets.UTF_8));
		jdbc.update("INSERT INTO processed_events (event_id, processed_at) VALUES ('e-kept', now())");

		Flyway.configure()
				.dataSource(preFlyway)
				.schemas(PRE_FLYWAY_SCHEMA)
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion("1")
				.load()
				.migrate();

		List<Map<String, Object>> history = jdbc.queryForList(
				"SELECT version, type FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
		assertThat(history).extracting(row -> row.get("version") + " " + row.get("type"))
				.containsExactly("1 BASELINE");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Integer.class)).isEqualTo(1);
		jdbcTemplate.execute("DROP SCHEMA " + PRE_FLYWAY_SCHEMA + " CASCADE");
	}
}
