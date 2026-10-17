/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17.
 * Scope: user-service adapter for issue #66 per the author's decisions
 * (options Q&A, docs/notification-service.md): D30 — names resolved at
 * ingest via GET /users/{id} behind a TTL cache; D31 — the call
 * authenticates with the shared X-Internal-Token secret (zero trust:
 * the route stays credentialed even inside the compose network;
 * permitAll was explicitly rejected). Failures answer empty and are
 * negative-cached briefly, never thrown (the port's contract).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.client;

import foc.notification.service.UserDirectory;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Resolves usernames against user-service's public-profile route,
 * {@code GET /users/{id}} (username only, F8.1.1), presenting the
 * shared internal token (D31). An empty base URL or token disables the
 * adapter — every lookup answers empty and the web's actor-less copy
 * takes over.
 *
 * <p>Results are cached per user id for {@code name-cache-ttl}
 * (bounding both call volume and rename staleness); misses and errors
 * are negative-cached for a short fixed window instead, so an outage
 * or an unknown id doesn't hammer user-service from a busy queue but
 * recovery isn't muted for the full TTL. Timeouts are finite and small
 * for the same reason the Redis ones are: the lookup runs on the
 * listener path, and a hung call there stalls the queue.
 */
@Component
class RestUserDirectory implements UserDirectory {

	static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

	private static final Logger log = LoggerFactory.getLogger(RestUserDirectory.class);
	private static final Duration TIMEOUT = Duration.ofSeconds(2);
	private static final Duration NEGATIVE_TTL = Duration.ofSeconds(30);
	/** Crude bound: a runaway id space resets the cache, not the heap. */
	private static final int MAX_CACHE_ENTRIES = 10_000;

	private record CachedName(String username, Instant expiresAt) {
	}

	private record PublicProfile(String username) {
	}

	private final RestClient restClient;
	private final String internalToken;
	private final Duration ttl;
	private final boolean enabled;
	private final Clock clock;
	private final Map<String, CachedName> cache = new ConcurrentHashMap<>();

	@Autowired
	RestUserDirectory(RestClient.Builder restClientBuilder,
			@Value("${notification.user-service.base-url}") String baseUrl,
			@Value("${notification.user-service.internal-token}") String internalToken,
			@Value("${notification.user-service.name-cache-ttl}") Duration ttl) {
		this(withTimeouts(restClientBuilder), baseUrl, internalToken, ttl, Clock.systemUTC());
	}

	/**
	 * Takes the builder as configured — the unit test binds a
	 * MockRestServiceServer to it, which a request factory set here
	 * would silently replace with a real HTTP client.
	 */
	RestUserDirectory(RestClient.Builder restClientBuilder, String baseUrl, String internalToken,
			Duration ttl, Clock clock) {
		this.enabled = !baseUrl.isBlank() && !internalToken.isBlank();
		this.internalToken = internalToken;
		this.ttl = ttl;
		this.clock = clock;
		this.restClient = restClientBuilder
				.baseUrl(enabled ? baseUrl : "http://disabled.invalid")
				.build();
	}

	private static RestClient.Builder withTimeouts(RestClient.Builder builder) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
		requestFactory.setReadTimeout(TIMEOUT);
		return builder.requestFactory(requestFactory);
	}

	@Override
	public Optional<String> usernameOf(String userId) {
		if (!enabled) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		CachedName cached = cache.get(userId);
		if (cached != null && now.isBefore(cached.expiresAt())) {
			return Optional.ofNullable(cached.username());
		}
		String username = fetch(userId);
		if (cache.size() >= MAX_CACHE_ENTRIES) {
			cache.clear();
		}
		cache.put(userId, new CachedName(username,
				now.plus(username == null ? NEGATIVE_TTL : ttl)));
		return Optional.ofNullable(username);
	}

	private String fetch(String userId) {
		try {
			PublicProfile profile = restClient.get()
					.uri("/users/{id}", userId)
					.header(INTERNAL_TOKEN_HEADER, internalToken)
					.retrieve()
					.body(PublicProfile.class);
			if (profile == null || profile.username() == null || profile.username().isBlank()) {
				return null;
			}
			return profile.username();
		} catch (RuntimeException ex) {
			// 404 (unknown or soft-deleted user), 401 (token mismatch),
			// timeouts, conversion errors: all mean "no name" (the port's
			// never-throw contract) — the row still stores, copy degrades
			log.warn("username lookup failed for user {}: {}", userId, ex.getMessage());
			return null;
		}
	}
}
