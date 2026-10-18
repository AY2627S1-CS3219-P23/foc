/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-12.
 * Scope: Redis adapter for the D23 denylist check (issue #67) per the
 * author's decisions (options Q&A): EXISTS on the same denylist:<jti>
 * keys user-service writes and gateway/lua/auth.lua checks; fail open
 * on Redis failure with finite client timeouts (G6 semantics).
 * 2026-10-14, Claude Code (Fable 5), PR #185 review (@Sinnez1): the
 * per-user cutoff lookup added — same GET and strict iat comparison
 * the gateway's auth.lua does for G11.
 * 2026-10-16, Claude Code (Fable 5), PR #185 re-review (@Sinnez1):
 * fail open on any RuntimeException, not just DataAccessException —
 * the port's contract says any store failure, and untranslated client
 * errors (e.g. during a connection-factory restart) escaped preSend
 * and rejected the CONNECT.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.security.redis;

import foc.notification.security.TokenDenylist;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Looks up the platform Redis keys user-service writes and the gateway
 * checks on REST calls: {@code denylist:<jti>} for a logged-out token
 * (G10) and {@code denylist:user:<id>} for the epoch-second cutoff a
 * password reset stamps (G11). Fails open: any runtime failure of a
 * lookup — translated or not — is logged and reported as not-revoked,
 * so an outage degrades revocation (the token stays usable until its
 * {@code exp}) rather than blocking every CONNECT. The finite cost of
 * an outage comes from the client timeouts in application.yaml, not
 * from logic here.
 */
@Component
class RedisTokenDenylist implements TokenDenylist {

	private static final Logger log = LoggerFactory.getLogger(RedisTokenDenylist.class);

	static final String KEY_PREFIX = "denylist:";
	static final String USER_KEY_PREFIX = "denylist:user:";

	private final StringRedisTemplate redis;

	RedisTokenDenylist(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public boolean isRevoked(String jti) {
		try {
			return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
		} catch (RuntimeException e) {
			log.warn("denylist lookup failed for jti {}; failing open", jti, e);
			return false;
		}
	}

	@Override
	public boolean isUserRevokedSince(String subject, Instant issuedAt) {
		String stamp;
		try {
			stamp = redis.opsForValue().get(USER_KEY_PREFIX + subject);
		} catch (RuntimeException e) {
			log.warn("user denylist lookup failed for subject {}; failing open", subject, e);
			return false;
		}
		if (stamp == null) {
			return false;
		}
		try {
			// Strictly before, like auth.lua: a token minted in the same
			// second as the reset survives (the owner's own new session).
			return issuedAt.getEpochSecond() < Long.parseLong(stamp.trim());
		} catch (NumberFormatException e) {
			// A stamp we cannot read is no reason to refuse the session —
			// the gateway reads an unparseable stamp as 0 (never a cutoff).
			log.warn("unparseable user denylist stamp {} for subject {}; failing open",
					stamp, subject);
			return false;
		}
	}
}
