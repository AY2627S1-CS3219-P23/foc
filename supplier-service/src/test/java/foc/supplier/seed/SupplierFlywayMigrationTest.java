/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): Flyway baseline / advisory-locked seeding for
 * multi-replica starts (team decision, Leong Wei Zhi via options Q&A).
 * On an empty Postgres, Flyway applies V1 and Hibernate's validate
 * accepts it (the context would not start otherwise), and the seeder
 * still loads the CSV on top.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.supplier.seed;

import static org.assertj.core.api.Assertions.assertThat;

import foc.supplier.PostgresTestContainer;
import foc.supplier.repository.SuppliersRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {PostgresTestContainer.FLYWAY_ON, PostgresTestContainer.DDL_VALIDATE})
class SupplierFlywayMigrationTest extends PostgresTestContainer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SuppliersRepository suppliersRepository;

    @Test
    void flywayAppliesV1AndHibernateValidatesIt() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, type, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(history).hasSize(1);
        assertThat(history.get(0)).containsEntry("version", "1").containsEntry("type", "SQL").containsEntry("success", true);
        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'supplier%'",
                String.class))
                .containsExactlyInAnyOrder("suppliers", "supplier_categories", "supplier_bookmarks");
    }

    @Test
    void seederStillLoadsTheCsvAfterTheMigration() throws Exception {
        assertThat(suppliersRepository.count()).isEqualTo(SuppliersSeederConcurrencyTest.csvRowCount());
    }
}
