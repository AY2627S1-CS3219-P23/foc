/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-12.
 * Scope: unit tests for issue #67's D23 denylist adapter — revoked /
 * not-revoked lookups and the fail-open path on a Redis failure.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.security.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisTokenDenylistTest {

	private StringRedisTemplate redis;
	private RedisTokenDenylist denylist;

	@BeforeEach
	void setUp() {
		redis = mock(StringRedisTemplate.class);
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
}
