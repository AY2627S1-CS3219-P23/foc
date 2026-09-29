/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: tests for JwtIssuer (issue #90): tokens verify the way
       notification-service's JwtVerifier checks them (HS256, shared
       secret, sub = user id) and carry role, jti and a 1 h expiry; a short
       secret fails at construction.
       PR #141 review: a TTL with no unit binds as seconds.
Author review: Ryan to review via the PR.
*/

package foc.user.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.test.util.ReflectionTestUtils;

import foc.user.entity.Role;
import foc.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtIssuerTest {

    private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";

    private static User admin() {
        User user = new User("e1234567@u.nus.edu", "nus_courier_99", "hash", Role.ADMIN);
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }

    // what notification-service's JwtVerifier does
    private static Jws<Claims> verify(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(token);
    }

    @Test
    @DisplayName("Token is HS256 and carries sub, role, jti and a 1 h expiry")
    void issue_claims() {
        // a fixed clock just now, so the token hasn't expired when parsed
        Instant now = Instant.now().minusSeconds(5).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        JwtIssuer issuer = new JwtIssuer(SECRET, Duration.ofHours(1), Clock.fixed(now, ZoneOffset.UTC));

        Jws<Claims> jws = verify(issuer.issue(admin()));

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        Claims claims = jws.getPayload();
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(now);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(now.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("Each token gets its own jti (the logout denylist key)")
    void issue_uniqueJti() {
        JwtIssuer issuer = new JwtIssuer(SECRET, Duration.ofHours(1));

        String first = verify(issuer.issue(admin())).getPayload().getId();
        String second = verify(issuer.issue(admin())).getPayload().getId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("An unset TTL (empty JWT_ACCESS_TOKEN_TTL) defaults to 1 hour")
    void nullTtlDefaultsToOneHour() {
        assertThat(new JwtIssuer(SECRET, null).ttl()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("A TTL with no unit (JWT_ACCESS_TOKEN_TTL=3600) binds as seconds")
    void unitlessTtlIsSeconds() throws Exception {
        // the conversion Spring Boot applies to the constructor's @Value TTL
        TypeDescriptor ttlParam = new TypeDescriptor(MethodParameter.forExecutable(
            JwtIssuer.class.getConstructor(String.class, Duration.class), 1));

        Object ttl = ApplicationConversionService.getSharedInstance()
            .convert("3600", TypeDescriptor.valueOf(String.class), ttlParam);

        assertThat(ttl).isEqualTo(Duration.ofHours(1));
        assertThat(ApplicationConversionService.getSharedInstance()
            .convert("30m", TypeDescriptor.valueOf(String.class), ttlParam)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("A zero or negative TTL fails at construction")
    void nonPositiveTtlRejected() {
        assertThatThrownBy(() -> new JwtIssuer(SECRET, Duration.ZERO))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_ACCESS_TOKEN_TTL");
    }

    @Test
    @DisplayName("A secret shorter than 32 bytes fails at construction")
    void shortSecretRejected() {
        assertThatThrownBy(() -> new JwtIssuer("too-short", Duration.ofHours(1)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
    }
}
