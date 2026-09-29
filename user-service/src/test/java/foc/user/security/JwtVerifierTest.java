/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: tests for JwtVerifier (issue #91): tokens from JwtIssuer verify to
       their sub and role; expired, wrongly signed, non-HS256, unsigned and
       claim-less tokens are rejected; a short secret fails at construction.
Author review: Ryan to review via the PR.
Tool: Github Copilot SDK, date: 2026-09-29
Scope: Wrote tests for JwtVerifier for issuer and audience.
Author review: Xiu Xi.

*/

package foc.user.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import foc.user.entity.Role;
import foc.user.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtVerifierTest {

    // 64 bytes, long enough to also sign HS512 for the algorithm check
    private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long-and-64-for-hs512!";

    private final JwtVerifier verifier = new JwtVerifier(SECRET);

    private static User user(long id, Role role) {
        User user = new User("e1234567@u.nus.edu", "student_alex", "hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // a well-formed token, for the tests to break one piece at a time
    private static JwtBuilder token() {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject("7")
            .claim("role", "USER")
            .id("a-jti")
            .issuer("cs3219group23")
            .audience().add("cs3219group23").and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofHours(1))));
    }

    private static String signed(JwtBuilder builder) {
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
            .compact();
    }

    @Test
    @DisplayName("A token from JwtIssuer verifies its sub and role")
    void issuedToken() {
        String token = new JwtIssuer(SECRET, Duration.ofHours(1)).issue(user(7L, Role.ADMIN));

        JwtVerifier.VerifiedToken verified = verifier.verify(token);

        assertThat(verified.subject()).isEqualTo("7");
        assertThat(verified.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Every role round-trips")
    void everyRole() {
        JwtIssuer issuer = new JwtIssuer(SECRET, Duration.ofHours(1));
        for (Role role : Role.values()) {
            assertThat(verifier.verify(issuer.issue(user(3L, role))).role()).isEqualTo(role);
        }
    }

    @Test
    @DisplayName("An expired token is rejected as expired")
    void expired() {
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        String token = new JwtIssuer(SECRET, Duration.ofHours(1), Clock.fixed(twoHoursAgo, ZoneOffset.UTC))
            .issue(user(7L, Role.USER));

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("A token signed with another secret is rejected")
    void wrongSecret() {
        String token = new JwtIssuer("another-secret-that-is-also-at-least-32-bytes", Duration.ofHours(1))
            .issue(user(7L, Role.USER));

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("A token signed with HS512, even with the shared secret, is rejected")
    void notHs256() {
        String token = token()
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS512)
            .compact();

        assertThatThrownBy(() -> verifier.verify(token))
            .isInstanceOf(JwtException.class)
            .hasMessageContaining("algorithm");
    }

    @Test
    @DisplayName("An unsigned (alg none) token is rejected")
    void unsigned() {
        String token = token().compact();

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("A garbage token is rejected")
    void garbage() {
        assertThatThrownBy(() -> verifier.verify("not.a.jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("A token without a numeric sub is rejected")
    void badSubject() {
        String noSubject = signed(token().subject(null));
        String nonNumeric = signed(token().subject("alex"));
        String tooLarge = signed(token().subject("99999999999999999999"));

        for (String token : new String[] {noSubject, nonNumeric, tooLarge}) {
            assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("subject");
        }
    }

    @Test
    @DisplayName("A token without a known role is rejected")
    void badRole() {
        String noRole = signed(token().claim("role", null));
        String unknown = signed(token().claim("role", "SUPERUSER"));
        String lowerCase = signed(token().claim("role", "admin"));
        String notText = signed(token().claim("role", 1));

        for (String token : new String[] {noRole, unknown, lowerCase, notText}) {
            assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("role");
        }
    }

    @Test
    @DisplayName("A token without an exp is rejected")
    void noExpiry() {
        String token = signed(token().expiration(null));

        assertThatThrownBy(() -> verifier.verify(token))
            .isInstanceOf(JwtException.class)
            .hasMessageContaining("expiry");
    }
    
    @Test
    @DisplayName("A token with the wrong issuer is rejected")
    void badIssuer() {
        String wrongIssuer = signed(token().issuer("wrong-issuer"));
        String noIssuer = signed(token().issuer(null));

        for (String token : new String[] {wrongIssuer, noIssuer}) {
            assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("issuer");
        }
    }

    @Test
    @DisplayName("A token with the wrong audience is rejected")
    void badAudience() {
        String wrongAudience = signed(token().audience().clear().add("wrong").and());
        String noAudience = signed(token().audience().clear().and());

        for (String token : new String[] {wrongAudience, noAudience}) {
            assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("audience");
        }
    }

    @Test
    @DisplayName("A secret shorter than 32 bytes fails at construction")
    void shortSecretRejected() {
        assertThatThrownBy(() -> new JwtVerifier("too-short"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtVerifier(""))
            .isInstanceOf(IllegalStateException.class);
    }
}
