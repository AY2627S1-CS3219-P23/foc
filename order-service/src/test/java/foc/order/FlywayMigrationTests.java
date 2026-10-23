/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-23.
 * Scope: issue #240: V2's back-fill tested on outbox rows written before
 * it. Flyway is run by hand on a schema of its own in the shared test
 * container: V1, then rows as V1's service wrote them, then V2.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.order;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationTests extends PostgresTestContainer {

	// not the application's schema, so its tables start empty and at V1
	private static final String SCHEMA = "migration_backfill";

	@Test
	void v2BackFillsRequestIdFromThePayloadsCorrelationId() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		migrate(dataSource, "1");
		UUID submitted = insertV1Event(jdbc, "request.submitted", "41");
		UUID completed = insertV1Event(jdbc, "request.completed", "42");

		migrate(dataSource, "2");

		assertEquals(41L, requestId(jdbc, submitted));
		assertEquals(42L, requestId(jdbc, completed));
	}

	private static void migrate(DriverManagerDataSource dataSource, String target) {
		Flyway.configure()
				.dataSource(dataSource)
				.schemas(SCHEMA)
				.locations("classpath:db/migration")
				.target(target)
				.load()
				.migrate();
	}

	// A row as EventOutbox wrote it before V2: no request_id, the request's
	// id only as the payload's correlationId.
	private static UUID insertV1Event(JdbcTemplate jdbc, String eventType, String requestId) {
		UUID eventId = UUID.randomUUID();
		jdbc.update("insert into " + SCHEMA + ".outbox_event (event_id, event_type, payload, created_at) "
						+ "values (?, ?, ?, now())",
				eventId, eventType,
				"{\"eventId\": \"" + eventId + "\", \"correlationId\": \"" + requestId + "\", "
						+ "\"requestId\": \"" + requestId + "\", \"eventType\": \"" + eventType + "\"}");
		return eventId;
	}

	private static Long requestId(JdbcTemplate jdbc, UUID eventId) {
		return jdbc.queryForObject("select request_id from " + SCHEMA + ".outbox_event where event_id = ?",
				Long.class, eventId);
	}
}
