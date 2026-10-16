/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-12.
 * Scope: unit tests for issue #67's D23 denylist adapter — revoked /
 * not-revoked lookups and the fail-open path on a Redis failure.
 * 2026-10-14, PR #185 review (@Sinnez1): per-user cutoff cases added.
 * 2026-10-16, PR #185 re-review (@Sinnez1): untranslated-exception
 * fail-open cases added — the contract says any store failure.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.security.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisTokenDenylistTest {

	private static final Instant ISSUED_AT = Instant.ofEpochSecond(1_760_000_000L);

	private StringRedisTemplate redis;
	private ValueOperations<String, String> values;
	private RedisTokenDenylist denylist;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		redis = mock(StringRedisTemplate.class);
		values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		denylist = new RedisTokenDenylist(redis);
	}

	@Test
	void revokedWhenKeyExists() {
		when(redis.hasKey("denylist:jti-1")).thenReturn(true);

		assertThat(denylist.isRevoked("jti-1")).isTrue();
	}

	@Test
	void notRevokedWhenKeyAbsent() {
		when(redis.hasKey("denylist:jti-1")).thenReturn(false);

		assertThat(denylist.isRevoked("jti-1")).isFalse();
	}

	@Test
	void failsOpenWhenRedisIsUnreachable() {
		when(redis.hasKey("denylist:jti-1"))
				.thenThrow(new RedisConnectionFailureException("down"));

		// G6/D23: an outage degrades revocation, never valid CONNECTs.
		assertThat(denylist.isRevoked("jti-1")).isFalse();
	}

	@Test
	void failsOpenOnAnUntranslatedClientError() {
		// Not a DataAccessException: e.g. Lettuce while the connection
		// factory is shutting down or reconnecting. The TokenDenylist
		// contract says any store failure answers false.
		when(redis.hasKey("denylist:jti-1"))
				.thenThrow(new IllegalStateException("connection factory shutting down"));

		assertThat(denylist.isRevoked("jti-1")).isFalse();
	}

	@Test
	void userRevokedWhenTheResetStampIsNewerThanTheToken() {
		// G11: a password reset stamps the subject's cutoff instead of
		// each outstanding jti.
		when(values.get("denylist:user:42"))
				.thenReturn(String.valueOf(ISSUED_AT.getEpochSecond() + 1));

		assertThat(denylist.isUserRevokedSince("42", ISSUED_AT)).isTrue();
	}

	@Test
	void userNotRevokedWhenTheTokenIsAsNewAsTheStamp() {
		// Strictly before, like auth.lua: a token minted in the stamp's
		// own second is the owner's post-reset login.
		when(values.get("denylist:user:42"))
				.thenReturn(String.valueOf(ISSUED_AT.getEpochSecond()));

		assertThat(denylist.isUserRevokedSince("42", ISSUED_AT)).isFalse();
	}

	@Test
	void userNotRevokedWithoutAStamp() {
		when(values.get("denylist:user:42")).thenReturn(null);

		assertThat(denylist.isUserRevokedSince("42", ISSUED_AT)).isFalse();
	}

	@Test
	void userCutoffFailsOpenWhenRedisIsUnreachable() {
		when(values.get("denylist:user:42"))
				.thenThrow(new RedisConnectionFailureException("down"));

		assertThat(denylist.isUserRevokedSince("42", ISSUED_AT)).isFalse();
	}

	@Test
	void userCutoffFailsOpenOnAnUntranslatedClientError() {
		when(values.get("denylist:user:42"))
				.thenThrow(new IllegalStateException("connection factory shutting down"));

		assertThat(denylist.isUserRevokedSince("42", ISSUED_AT)).isFalse();
	}

	@Test
	void userCutoffFailsOpenOnAnUnparseableStamp() {
		// The gateway reads a non-numeric stamp as 0, i.e. no cutoff.
		when(values.get("denylist:user:42")).thenReturn("not-a-number");

		assertThat(denylist.isUserRevokedSince("42", ISSUED_AT)).isFalse();
	}
}
