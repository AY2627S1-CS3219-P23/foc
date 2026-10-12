/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-12.
 * Scope: Redis adapter for the D23 denylist check (issue #67) per the
 * author's decisions (options Q&A): EXISTS on the same denylist:<jti>
 * keys user-service writes and gateway/lua/auth.lua checks; fail open
 * on Redis failure with finite client timeouts (G6 semantics).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.security.redis;

import foc.notification.security.TokenDenylist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Looks up {@code denylist:<jti>} keys in the platform Redis — the
 * same keys user-service's logout writes and the gateway checks on
 * REST calls. Fails open: a Redis error is logged and reported as
 * not-revoked, so an outage degrades revocation (the token stays
 * usable until its {@code exp}) rather than blocking every CONNECT.
 * The finite cost of an outage comes from the client timeouts in
 * application.yaml, not from logic here.
 */
@Component
class RedisTokenDenylist implements TokenDenylist {

	private static final Logger log = LoggerFactory.getLogger(RedisTokenDenylist.class);

	static final String KEY_PREFIX = "denylist:";

	private final StringRedisTemplate redis;

	RedisTokenDenylist(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public boolean isRevoked(String jti) {
		try {
			return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
		} catch (DataAccessException e) {
			log.warn("denylist lookup failed for jti {}; failing open", jti, e);
			return false;
		}
	}
}
