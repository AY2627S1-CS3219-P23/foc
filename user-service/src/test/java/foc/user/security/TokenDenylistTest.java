/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-11
Scope: unit tests for TokenDenylist (issue #151) over a mocked
       StringRedisTemplate: key shape and TTL of the write, the
       already-expired skip, and the best-effort swallow of a Redis
       failure (team decision G9 — logout must not fail on an outage).
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TokenDenylistTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private TokenDenylist denylist;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        denylist = new TokenDenylist(redis, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Revoking writes denylist:<jti> expiring when the token itself would")
    void revoke_writesTtlKey() {
        denylist.revoke("a-jti", NOW.plus(Duration.ofMinutes(42)));

        verify(values).set("denylist:a-jti", "1", Duration.ofMinutes(42));
    }

    @Test
    @DisplayName("An already-expired token writes nothing — Redis has nothing left to deny")
    void revoke_expiredTokenSkipped() {
        denylist.revoke("a-jti", NOW.minusSeconds(1));
        denylist.revoke("a-jti", NOW);

        verifyNoInteractions(values);
    }

    @Test
    @DisplayName("A Redis failure is swallowed — logout is best-effort (G9)")
    void revoke_swallowsRedisFailure() {
        doThrow(new RedisConnectionFailureException("redis is down"))
            .when(values).set("denylist:a-jti", "1", Duration.ofHours(1));

        assertThatCode(() -> denylist.revoke("a-jti", NOW.plus(Duration.ofHours(1))))
            .doesNotThrowAnyException();
    }
}
