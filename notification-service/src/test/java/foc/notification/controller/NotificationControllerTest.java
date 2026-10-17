/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: behavior tests for the REST API contract
 * (docs/notification-service.md "REST API contract", decisions
 * D24-D27): list order and envelope, the unread filter and count, the
 * pagination 400s, the read-flag writes with their idempotency, and
 * the ownership rules — one caller can never see or touch another's
 * rows, and a foreign ID answers the same 404 as a missing one.
 * Test style follows credit-service's CreditControllerTest (real JWTs
 * through the whole filter chain, H2 database).
 * 2026-10-17, PR #198 Codex review: non-boolean read values ("true",
 * 1) pinned as 400s — Jackson's default coercion was binding them.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.notification.entity.Notification;
import foc.notification.repository.NotificationRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest {

	private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";
	private static final String ALICE = "usr-req-1001";
	private static final String BOB = "usr-cou-2002";
	private static final Instant OCCURRED_AT = Instant.parse("2026-10-17T08:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private NotificationRepository notifications;

	@BeforeEach
	void clean() {
		notifications.deleteAll();
	}

	private static String bearer(String userId) {
		return "Bearer " + Jwts.builder()
				.subject(userId)
				.issuedAt(Date.from(Instant.now()))
				.expiration(Date.from(Instant.now().plusSeconds(3600)))
				.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
				.compact();
	}

	private Notification stored(String recipientId, String eventType, String payload) {
		return notifications.save(new Notification(
				recipientId, UUID.randomUUID().toString(), eventType, payload, OCCURRED_AT));
	}

	@Test
	@DisplayName("The list is the caller's rows, newest stored first, payload as a parsed object")
	void listIsNewestFirstWithParsedPayload() throws Exception {
		Notification oldest = stored(ALICE, "request.created", "{\"requestId\":\"req-1\"}");
		Notification newest = stored(ALICE, "request.accepted",
				"{\"requestId\":\"req-1\",\"courierName\":\"marcus\"}");

		mockMvc.perform(get("/notifications/me").header("Authorization", bearer(ALICE)))
				.andExpect(status().isOk())
				// IDs are monotonic, so with equal created_at instants the ID
				// tiebreaker still puts the later insert first (D25)
				.andExpect(jsonPath("$.content[0].id").value(newest.getId()))
				.andExpect(jsonPath("$.content[0].eventType").value("request.accepted"))
				.andExpect(jsonPath("$.content[0].payload.courierName").value("marcus"))
				.andExpect(jsonPath("$.content[0].read").value(false))
				.andExpect(jsonPath("$.content[1].id").value(oldest.getId()))
				// the envelope the web app reads everywhere
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.totalPages").value(1))
				// internals stay internal (the DTO omits them deliberately)
				.andExpect(jsonPath("$.content[0].recipientId").doesNotExist())
				.andExpect(jsonPath("$.content[0].eventId").doesNotExist());
	}

	@Test
	@DisplayName("page and size page through the list")
	void listPaginates() throws Exception {
		for (int i = 0; i < 3; i++) {
			stored(ALICE, "request.created", "{\"requestId\":\"req-" + i + "\"}");
		}
		mockMvc.perform(get("/notifications/me").header("Authorization", bearer(ALICE))
						.param("page", "1").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	@DisplayName("unread=true narrows the list to unread rows")
	void unreadFilter() throws Exception {
		Notification read = stored(ALICE, "request.created", "{\"requestId\":\"req-1\"}");
		Notification unread = stored(ALICE, "request.accepted", "{\"requestId\":\"req-1\"}");
		notifications.updateReadFlag(read.getId(), ALICE, true);

		mockMvc.perform(get("/notifications/me").header("Authorization", bearer(ALICE))
						.param("unread", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(unread.getId()))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@ParameterizedTest
	@CsvSource({
			"page, -1, page must be 0 or more",
			"size, 0, size must be between 1 and 100",
			"size, 101, size must be between 1 and 100",
	})
	@DisplayName("Out-of-range page and size are 400 problem+json")
	void outOfRangePagingIs400(String name, String value, String detail) throws Exception {
		mockMvc.perform(get("/notifications/me").header("Authorization", bearer(ALICE))
						.param(name, value))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value(detail));
	}

	@Test
	@DisplayName("A non-integer page is 400 problem+json")
	void nonIntegerPageIs400() throws Exception {
		mockMvc.perform(get("/notifications/me").header("Authorization", bearer(ALICE))
						.param("page", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("page must be an integer"));
	}

	@Test
	@DisplayName("One caller never sees another's rows")
	void listIsOwnershipScoped() throws Exception {
		stored(ALICE, "request.created", "{\"requestId\":\"req-1\"}");
		stored(BOB, "request.created", "{\"requestId\":\"req-2\"}");

		mockMvc.perform(get("/notifications/me").header("Authorization", bearer(ALICE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].payload.requestId").value("req-1"));
	}

	@Test
	@DisplayName("PATCH flips the read flag both ways and repeating it is the same 204")
	void patchTogglesReadIdempotently() throws Exception {
		Notification row = stored(ALICE, "request.created", "{\"requestId\":\"req-1\"}");

		mockMvc.perform(patchRead(row.getId(), ALICE, true)).andExpect(status().isNoContent());
		assertThat(notifications.findById(row.getId()).orElseThrow().isRead()).isTrue();

		// same state again: still a 204, not an error (D26 counts matched rows)
		mockMvc.perform(patchRead(row.getId(), ALICE, true)).andExpect(status().isNoContent());

		mockMvc.perform(patchRead(row.getId(), ALICE, false)).andExpect(status().isNoContent());
		assertThat(notifications.findById(row.getId()).orElseThrow().isRead()).isFalse();
	}

	@Test
	@DisplayName("A foreign ID and a missing ID are the same 404")
	void patchForeignOrMissingIdIs404() throws Exception {
		Notification bobs = stored(BOB, "request.created", "{\"requestId\":\"req-2\"}");

		// indistinguishable by design: no existence oracle (D26) — same
		// status, same detail (only problem+json's instance URI differs)
		mockMvc.perform(patchRead(bobs.getId(), ALICE, true))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("No such notification"));
		mockMvc.perform(patchRead(999_999L, ALICE, true))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No such notification"));

		assertThat(notifications.findById(bobs.getId()).orElseThrow().isRead()).isFalse();
	}

	@Test
	@DisplayName("A PATCH body without the read flag is 400")
	void patchWithoutReadIs400() throws Exception {
		Notification row = stored(ALICE, "request.created", "{\"requestId\":\"req-1\"}");
		mockMvc.perform(patch("/notifications/me/" + row.getId())
						.header("Authorization", bearer(ALICE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("read is required and must be true or false"));
	}

	@ParameterizedTest
	@CsvSource({
			"'{\"read\":\"true\"}'",
			"'{\"read\":1}'",
			"'{\"read\":\"yes\"}'",
	})
	@DisplayName("A non-boolean read is 400, never coerced (PR #198 review)")
	void patchWithNonBooleanReadIs400(String body) throws Exception {
		Notification row = stored(ALICE, "request.created", "{\"requestId\":\"req-1\"}");
		mockMvc.perform(patch("/notifications/me/" + row.getId())
						.header("Authorization", bearer(ALICE))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		// the refused write changed nothing
		assertThat(notifications.findById(row.getId()).orElseThrow().isRead()).isFalse();
	}

	@Test
	@DisplayName("A non-numeric ID is 400, not 500")
	void patchNonNumericIdIs400() throws Exception {
		mockMvc.perform(patch("/notifications/me/abc")
						.header("Authorization", bearer(ALICE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"read\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("id must be an integer"));
	}

	@Test
	@DisplayName("read-all marks only the caller's rows, and the count follows")
	void readAllIsOwnershipScoped() throws Exception {
		stored(ALICE, "request.created", "{\"requestId\":\"req-1\"}");
		stored(ALICE, "request.accepted", "{\"requestId\":\"req-1\"}");
		Notification bobs = stored(BOB, "request.created", "{\"requestId\":\"req-2\"}");

		mockMvc.perform(get("/notifications/me/unread-count").header("Authorization", bearer(ALICE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(2));

		mockMvc.perform(post("/notifications/me/read-all").header("Authorization", bearer(ALICE)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/notifications/me/unread-count").header("Authorization", bearer(ALICE)))
				.andExpect(jsonPath("$.count").value(0));
		// Bob's row is untouched
		assertThat(notifications.findById(bobs.getId()).orElseThrow().isRead()).isFalse();

		// nothing left unread: still a 204, not an error
		mockMvc.perform(post("/notifications/me/read-all").header("Authorization", bearer(ALICE)))
				.andExpect(status().isNoContent());
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder patchRead(
			long id, String userId, boolean read) {
		return patch("/notifications/me/" + id)
				.header("Authorization", bearer(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"read\":" + read + "}");
	}
}
