/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17.
 * Scope: unit tests for issue #66's user-service adapter (author
 * decisions D30/D31 via options Q&A): the lookup URL and internal-token
 * header, TTL caching with its shorter negative window (driven by a
 * mutable fixed clock), the never-throw degradation on 404/5xx, and
 * the disabled mode when base-url or token is unset. Uses
 * MockRestServiceServer bound to the builder — no live HTTP.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestUserDirectoryTest {

	private static final Duration TTL = Duration.ofMinutes(10);

	/** A fixed clock the tests can advance past the cache windows. */
	private static final class MutableClock extends Clock {
		private Instant now = Instant.parse("2026-10-17T12:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
	}

	private final MutableClock clock = new MutableClock();
	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final RestUserDirectory directory =
			new RestUserDirectory(builder, "http://user-service:8080", "secret-token", TTL, clock);

	private void respondUsername(String id, String username) {
		server.expect(requestTo("http://user-service:8080/users/" + id))
				.andExpect(header(RestUserDirectory.INTERNAL_TOKEN_HEADER, "secret-token"))
				.andRespond(withSuccess("{\"username\":\"" + username + "\"}",
						MediaType.APPLICATION_JSON));
	}

	@Test
	void resolvesUsernameWithInternalToken() {
		respondUsername("42", "maya");

		assertThat(directory.usernameOf("42")).contains("maya");
		server.verify();
	}

	@Test
	void cachesWithinTtlAndRefetchesAfter() {
		respondUsername("42", "maya");
		assertThat(directory.usernameOf("42")).contains("maya");
		// inside the TTL: answered from cache, no second request
		clock.advance(TTL.minusSeconds(1));
		assertThat(directory.usernameOf("42")).contains("maya");
		server.verify();
		server.reset();

		// past the TTL: fetched again (a rename shows up)
		respondUsername("42", "maya-renamed");
		clock.advance(Duration.ofSeconds(2));
		assertThat(directory.usernameOf("42")).contains("maya-renamed");
		server.verify();
	}

	@Test
	void unknownUserIsEmptyAndNegativeCachedBriefly() {
		server.expect(requestTo("http://user-service:8080/users/9000"))
				.andRespond(withResourceNotFound());
		assertThat(directory.usernameOf("9000")).isEmpty();
		// inside the negative window: no retry
		assertThat(directory.usernameOf("9000")).isEmpty();
		server.verify();
		server.reset();

		// the negative window is shorter than the TTL: recovery retries
		respondUsername("9000", "late-signup");
		clock.advance(Duration.ofSeconds(31));
		assertThat(directory.usernameOf("9000")).contains("late-signup");
		server.verify();
	}

	@Test
	void serverErrorDegradesToEmpty() {
		server.expect(requestTo("http://user-service:8080/users/42"))
				.andRespond(withServerError());

		assertThat(directory.usernameOf("42")).isEmpty();
		server.verify();
	}

	@Test
	void blankBaseUrlDisablesLookups() {
		RestClient.Builder disabledBuilder = RestClient.builder();
		MockRestServiceServer disabledServer = MockRestServiceServer.bindTo(disabledBuilder).build();
		RestUserDirectory disabled =
				new RestUserDirectory(disabledBuilder, "", "secret-token", TTL, clock);

		assertThat(disabled.usernameOf("42")).isEmpty();
		disabledServer.verify(); // zero expectations: nothing was called
	}

	@Test
	void blankTokenDisablesLookups() {
		RestClient.Builder disabledBuilder = RestClient.builder();
		MockRestServiceServer disabledServer = MockRestServiceServer.bindTo(disabledBuilder).build();
		RestUserDirectory disabled =
				new RestUserDirectory(disabledBuilder, "http://user-service:8080", " ", TTL, clock);

		assertThat(disabled.usernameOf("42")).isEmpty();
		disabledServer.verify();
	}
}
