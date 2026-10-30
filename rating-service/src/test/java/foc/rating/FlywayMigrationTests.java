/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: V2 run on real Postgres, following order-service's
 * FlywayMigrationTests — the H2 test yaml disables Flyway, so this is
 * the only place the PostgreSQL-flavored migration actually executes
 * before compose does it.
 * Author review: [pending]
 */
package foc.rating;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlywayMigrationTests extends PostgresTestContainer {

    // not the application's schema, so its tables start empty
    private static final String SCHEMA = "migration_eligibility";

    @Test
    void v2CreatesTheEligibilityTablesWithTheirPrimaryKeys() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        OffsetDateTime now = OffsetDateTime.parse("2026-09-19T09:05:00Z");
        jdbc.update("insert into " + SCHEMA + ".processed_events (event_id, processed_at) values (?, ?)",
                "e-1", now);
        jdbc.update("insert into " + SCHEMA + ".rateable_request "
                + "(request_id, requester_id, courier_id, completed_at) values (?, ?, ?, ?)",
                "req-1", "usr-req-1001", "usr-cou-2002", now);

        // the dedupe backstop: a duplicate event id must be refused
        assertThrows(DuplicateKeyException.class, () ->
                jdbc.update("insert into " + SCHEMA + ".processed_events (event_id, processed_at) values (?, ?)",
                        "e-1", now));
        // one row per request: a duplicate request id must be refused too
        assertThrows(DuplicateKeyException.class, () ->
                jdbc.update("insert into " + SCHEMA + ".rateable_request "
                        + "(request_id, requester_id, courier_id, completed_at) values (?, ?, ?, ?)",
                        "req-1", "usr-req-1001", "usr-cou-2002", now));

        assertEquals("usr-cou-2002", jdbc.queryForObject(
                "select courier_id from " + SCHEMA + ".rateable_request where request_id = ?",
                String.class, "req-1"));
    }
}
