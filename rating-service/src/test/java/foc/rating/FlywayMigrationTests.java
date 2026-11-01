/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: V2 run on real Postgres, following order-service's
 * FlywayMigrationTests — the H2 test yaml disables Flyway, so this is
 * the only place the PostgreSQL-flavored migration actually executes
 * before compose does it.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), issue #252: V3's constraints on
 * real Postgres — one rating per (request, rater), the stars range
 * check, the FK to rateable_request, and the outbox primary key.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), issue #253: V4's read index exists
 * on real Postgres — the H2 tests never run the migration, so this is
 * the only check it parses and lands.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), issue #254: V5's user_report table
 * on real Postgres — the required columns, the optional ones, and the
 * list index.
 * Author review: [pending]
 */
package foc.rating;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.OffsetDateTime;
import java.util.UUID;

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

    @Test
    void v3EnforcesTheRatingAndOutboxConstraints() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        OffsetDateTime now = OffsetDateTime.parse("2026-09-19T09:30:00Z");
        jdbc.update("insert into " + SCHEMA + ".rateable_request "
                + "(request_id, requester_id, courier_id, completed_at) values (?, ?, ?, ?)",
                "req-2", "usr-req-1001", "usr-cou-2002", now);
        jdbc.update("insert into " + SCHEMA + ".rating "
                + "(id, request_id, rater_id, ratee_id, stars, body, created_at) values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "req-2", "usr-req-1001", "usr-cou-2002", 5, "Fast and friendly", now);

        // one rating per (request, rater), whatever its fresh id: the
        // race backstop behind the service's pre-check
        assertThrows(DuplicateKeyException.class, () ->
                jdbc.update("insert into " + SCHEMA + ".rating "
                        + "(id, request_id, rater_id, ratee_id, stars, body, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "req-2", "usr-req-1001", "usr-cou-2002", 1, null, now));
        // the other direction is its own rating
        jdbc.update("insert into " + SCHEMA + ".rating "
                + "(id, request_id, rater_id, ratee_id, stars, body, created_at) values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "req-2", "usr-cou-2002", "usr-req-1001", 3, null, now);
        // stars outside 1-5 never reach the table, whatever the service missed
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("insert into " + SCHEMA + ".rating "
                        + "(id, request_id, rater_id, ratee_id, stars, body, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "req-2", "usr-str-9999", "usr-req-1001", 6, null, now));
        // a rating must point at a completed request the ingest recorded
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("insert into " + SCHEMA + ".rating "
                        + "(id, request_id, rater_id, ratee_id, stars, body, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "req-missing", "usr-req-1001", "usr-cou-2002", 5, null, now));

        // the outbox insert-never-update contract rests on the primary key
        UUID eventId = UUID.randomUUID();
        jdbc.update("insert into " + SCHEMA + ".outbox_event (event_id, event_type, payload, created_at) "
                + "values (?, ?, ?, ?)", eventId, "rating.submitted", "{}", now);
        assertThrows(DuplicateKeyException.class, () ->
                jdbc.update("insert into " + SCHEMA + ".outbox_event (event_id, event_type, payload, created_at) "
                        + "values (?, ?, ?, ?)", eventId, "rating.submitted", "{}", now));
    }

    @Test
    void v4CreatesTheRatingReadIndex() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // the profile reads' index (#253): by ratee, newest first
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from pg_indexes where schemaname = ? and indexname = ?",
                Integer.class, SCHEMA, "idx_rating_ratee_created"));
    }

    @Test
    void v5CreatesTheUserReportTableAndItsListIndex() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        OffsetDateTime now = OffsetDateTime.parse("2026-10-30T09:30:00Z");
        // a full report, and a minimal one: request_id and details are optional
        jdbc.update("insert into " + SCHEMA + ".user_report "
                + "(id, reported_user_id, reporter_id, request_id, reason, details, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "usr-bad-3003", "usr-rep-1001", "req-5", "NO_SHOW",
                "Never arrived", now);
        jdbc.update("insert into " + SCHEMA + ".user_report "
                + "(id, reported_user_id, reporter_id, request_id, reason, details, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "usr-bad-3003", "usr-rep-1002", null, "OTHER", null, now);

        // the reported user and the reason are not optional
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("insert into " + SCHEMA + ".user_report "
                        + "(id, reported_user_id, reporter_id, request_id, reason, details, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), null, "usr-rep-1001", null, "OTHER", null, now));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("insert into " + SCHEMA + ".user_report "
                        + "(id, reported_user_id, reporter_id, request_id, reason, details, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "usr-bad-3003", "usr-rep-1001", null, null, null, now));

        // the admin list's index (#254): newest first
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from pg_indexes where schemaname = ? and indexname = ?",
                Integer.class, SCHEMA, "idx_user_report_created"));
    }
}
