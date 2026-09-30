/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: issue #147. Runs the Flyway migrations against their own schemas in
       the shared Testcontainers Postgres: a database ddl-auto built before
       Flyway is baselined at V1 and gets only V2, whose clean-up renames
       usernames that differ only in case (team decision: the oldest keeps
       its name, later ones get a numbered suffix); a fresh database runs
       V1 and V2.
       2026-09-30, Claude Code (Opus 5), issue #88 (PR #150): V3 joins
       them (drops otps, creates pending_signups), so the counts move to
       2 for a baselined database and MIGRATION_SCRIPTS for a fresh one.
       2026-09-30, Claude Code (Fable 5), issue #92: V4 and V5 join them
       (pending_email_changes and account_update_otps), moving the counts
       to 4 and 5.
Author review: Ryan to review via the PR.
*/

package foc.user;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FlywayMigrationTest extends PostgresTestContainer {

    // scripts in db/migration; a new V<n> bumps this
    private static final int MIGRATION_SCRIPTS = 5;

    private static Connection connect(String schema) throws SQLException {
        Connection connection = DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("drop schema if exists " + schema + " cascade");
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
        }
        return connection;
    }

    // the same settings as application.yaml's spring.flyway
    private static MigrateResult migrate(String schema) {
        return Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema)
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .load()
            .migrate();
    }

    private static void insertUser(Statement statement, String email, String username, String createdAt)
            throws SQLException {
        statement.execute("insert into users (email, username, password_hash, role, failed_login_attempts,"
            + " created_at) values ('" + email + "', '" + username + "', 'hash', 'USER', 0, '" + createdAt + "')");
    }

    private static Map<String, String> usernamesByEmail(Statement statement) throws SQLException {
        Map<String, String> usernames = new LinkedHashMap<>();
        try (ResultSet rows = statement.executeQuery("select email, username from users order by id")) {
            while (rows.next()) {
                usernames.put(rows.getString("email"), rows.getString("username"));
            }
        }
        return usernames;
    }

    @Test
    @DisplayName("A pre-Flyway database is baselined at V1, and V2 renames case-duplicates before adding the index")
    void existingDatabase_baselinedAndDeduplicated() throws Exception {
        String schema = "migration_existing";
        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            // what ddl-auto built: the V1 schema, but no Flyway history
            statement.execute(new ClassPathResource("db/migration/V1__baseline.sql")
                .getContentAsString(StandardCharsets.UTF_8));
            String longName = "a".repeat(30);
            insertUser(statement, "e1000001@u.nus.edu", "Bob", "2026-01-01T00:00:00Z");
            insertUser(statement, "e1000002@u.nus.edu", "bob", "2026-01-02T00:00:00Z");
            insertUser(statement, "e1000003@u.nus.edu", "BOB", "2026-01-03T00:00:00Z");
            // already holds the first suffix the clean-up would try
            insertUser(statement, "e1000004@u.nus.edu", "bob_3", "2026-01-04T00:00:00Z");
            insertUser(statement, "e1000005@u.nus.edu", longName, "2026-01-05T00:00:00Z");
            insertUser(statement, "e1000006@u.nus.edu", longName.toUpperCase(), "2026-01-06T00:00:00Z");
            insertUser(statement, "e1000007@u.nus.edu", "alice", "2026-01-07T00:00:00Z");

            MigrateResult result = migrate(schema);

            // V1 is recorded as the baseline, not run again; V2..V5 run
            assertThat(result.migrationsExecuted).isEqualTo(MIGRATION_SCRIPTS - 1);
            assertThat(usernamesByEmail(statement)).containsExactly(
                Map.entry("e1000001@u.nus.edu", "Bob"),
                Map.entry("e1000002@u.nus.edu", "bob_2"),
                Map.entry("e1000003@u.nus.edu", "BOB_4"),
                Map.entry("e1000004@u.nus.edu", "bob_3"),
                Map.entry("e1000005@u.nus.edu", longName),
                // cut to fit the 30-character limit with its suffix
                Map.entry("e1000006@u.nus.edu", "A".repeat(28) + "_2"),
                Map.entry("e1000007@u.nus.edu", "alice"));

            assertThatThrownBy(() -> insertUser(statement, "e1000008@u.nus.edu", "ALICE", "2026-01-08T00:00:00Z"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("idx_users_username_lower");
        }
    }

    @Test
    @DisplayName("A fresh database runs every migration")
    void freshDatabase_runsAllMigrations() throws Exception {
        String schema = "migration_fresh";
        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            MigrateResult result = migrate(schema);

            assertThat(result.migrationsExecuted).isEqualTo(MIGRATION_SCRIPTS);
            insertUser(statement, "e1000001@u.nus.edu", "Bob", "2026-01-01T00:00:00Z");
            assertThatThrownBy(() -> insertUser(statement, "e1000002@u.nus.edu", "bob", "2026-01-02T00:00:00Z"))
                .isInstanceOf(SQLException.class);
        }
    }
}
