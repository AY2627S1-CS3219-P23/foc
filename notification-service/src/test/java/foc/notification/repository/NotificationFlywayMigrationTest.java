/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (D36, team decision
 * via options Q&A: Flyway replaces ddl-auto: update).
 * Scope: the V1 baseline applies on an empty PostgreSQL and validates
 * against the entities; a pre-Flyway (ddl-auto) database is baselined at
 * V1 with its rows kept. Author review: Leong Wei Zhi, pending pull
 * request review.
 * Same day, PR #306 review (Sinnez1): V2 drops the pre-typed-contracts
 * columns and table, so a legacy volume baselined at V1 stores
 * notifications again. Author review: Leong Wei Zhi, pending pull request
 * review.
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
	void flywayAppliesTheMigrationsAndHibernateValidatesThem() {
		List<Map<String, Object>> history = jdbcTemplate.queryForList(
				"SELECT version, type, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

		assertThat(history).extracting(row -> row.get("version") + " " + row.get("type") + " " + row.get("success"))
				.containsExactly("1 SQL true", "2 SQL true");
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
		JdbcTemplate jdbc = preFlywaySchema();
		jdbc.update("INSERT INTO processed_events (event_id, processed_at) VALUES ('e-kept', now())");

		migrate(jdbc);

		List<Map<String, Object>> history = jdbc.queryForList(
				"SELECT version, type FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
		assertThat(history).extracting(row -> row.get("version") + " " + row.get("type"))
				.containsExactly("1 BASELINE", "2 SQL");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Integer.class)).isEqualTo(1);
		jdbcTemplate.execute("DROP SCHEMA " + PRE_FLYWAY_SCHEMA + " CASCADE");
	}

	// A volume from before the typed-contracts refactor: baselined at V1
	// unchecked, then V2 drops what the entities no longer write.
	@Test
	void aLegacyEntityColumnsDatabaseConvergesAndStoresNotifications() throws Exception {
		JdbcTemplate jdbc = preFlywaySchema();
		jdbc.execute("ALTER TABLE notifications ADD COLUMN entity_type varchar(255) NOT NULL");
		jdbc.execute("ALTER TABLE notifications ADD COLUMN entity_id varchar(255) NOT NULL");
		jdbc.execute("CREATE TABLE entity_sequences (entity_type varchar(255) NOT NULL,"
				+ " entity_id varchar(255) NOT NULL, last_applied_sequence bigint NOT NULL,"
				+ " PRIMARY KEY (entity_type, entity_id))");

		migrate(jdbc);

		jdbc.update("INSERT INTO notifications (created_at, event_id, event_type, occurred_at, payload,"
				+ " is_read, recipient_id) VALUES (now(), 'e-1', 'request.accepted', now(), '{}', false, 'usr-1')");
		assertThat(jdbc.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = 'notifications'",
				String.class, PRE_FLYWAY_SCHEMA)).doesNotContain("entity_type", "entity_id");
		assertThat(jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = ?",
				String.class, PRE_FLYWAY_SCHEMA)).doesNotContain("entity_sequences");
		jdbcTemplate.execute("DROP SCHEMA " + PRE_FLYWAY_SCHEMA + " CASCADE");
	}

	// V1 is pg_dump-identical to what ddl-auto: update built, so running it
	// by hand, outside Flyway, stands in for a pre-Flyway volume
	private JdbcTemplate preFlywaySchema() throws Exception {
		jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + PRE_FLYWAY_SCHEMA + " CASCADE");
		jdbcTemplate.execute("CREATE SCHEMA " + PRE_FLYWAY_SCHEMA);
		JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
				postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?")
						+ "currentSchema=" + PRE_FLYWAY_SCHEMA,
				postgres.getUsername(), postgres.getPassword()));
		jdbc.execute(new ClassPathResource("db/migration/V1__baseline.sql")
				.getContentAsString(StandardCharsets.UTF_8));
		return jdbc;
	}

	private static void migrate(JdbcTemplate jdbc) {
		Flyway.configure()
				.dataSource(jdbc.getDataSource())
				.schemas(PRE_FLYWAY_SCHEMA)
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion("1")
				.load()
				.migrate();
	}
}
