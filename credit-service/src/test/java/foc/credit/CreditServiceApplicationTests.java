/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-09.
 * Scope: scaffolded following the user-service pattern: the context loads
 * against a real Postgres, and Flyway's V1 has created the schema from
 * docs/credit-service.md, including the common pool's single row.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class CreditServiceApplicationTests extends PostgresTestContainer {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void contextLoads() {
	}

	@Test
	void baselineCreatesTheDesignTables() {
		List<String> tables = jdbc.queryForList(
				"select table_name from information_schema.tables where table_schema = 'public'", String.class);

		assertThat(tables).contains("credit_account", "credit_reservation", "credit_reservation_slice",
				"credit_lot", "credit_history", "common_pool", "redistribution_run", "outbox_event");
	}

	@Test
	void commonPoolStartsAsOneEmptyRow() {
		assertThat(jdbc.queryForList("select balance from common_pool", Integer.class)).containsExactly(0);
		// a second row breaks the single-row rule
		assertThatThrownBy(() -> jdbc.update("insert into common_pool (id, balance) values (2, 0)"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void negativeBalanceIsRejected() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into credit_account (user_id, available, reserved, created_at) values ('u-neg', -1, 0, now())"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
