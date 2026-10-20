/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: issue #67's gateway integration test: real STOMP sessions
 * against a RANDOM_PORT context (no broker, no Docker) — CONNECT
 * auth accept/reject, per-user delivery after commit within the 5 s
 * budget, SockJS fallback path, and silence for discarded events.
 * 2026-09-19, Method-B refactor (D16-D19): drives the port with typed
 * events; entity/stale assertions removed with the mechanism (D19).
 * 2026-09-20: order→request event vocabulary rename applied (author
 * decision D22, docs/notification-service.md).
 * 2026-10-12, issue #67: D23 denylist cases added against a fake
 * in-memory TokenDenylist (the Redis mechanics have their own unit
 * test; this context stays Docker- and Redis-free).
 * 2026-10-14, PR #185 review (@Sinnez1): per-user reset-cutoff CONNECT
 * cases added (G11), driven through the same fake.
 * 2026-10-16, PR #185 re-review (@Sinnez1): raw-frame cases added for
 * the two auth gaps the reviewer demonstrated — a STOMP 1.2 STOMP
 * frame must be authenticated like CONNECT, client SEND frames must
 * be rejected, and SUBSCRIBE is limited to the one destination.
 * Reviewed by: Leong Wei Zhi (via pull request).
 * 2026-10-17, Claude Code (Fable 5), issue #66: fixtures updated for
 * the display-name fields added to the request event records (D29);
 * no behavior change in this test. Same day, D29 superseded by the
 * author's D30 (options Q&A): the fields left the records again, and
 * the pushed frame's names now prove the ingest-time UserDirectory
 * enrichment (stubbed, like the denylist) reaches the live push.
 * 2026-10-19, Claude Code (Fable 5), issue #221: UserDirectory stub
 * removed with the lookup (D32 supersedes D30/D31) — the frame
 * assertion now pins an IDs-only payload; the web hydrates names.
 */
package foc.notification.messaging.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import foc.contracts.events.request.RequestAccepted;
import foc.contracts.events.request.RequestCollected;
import foc.notification.security.TokenDenylist;
import foc.notification.service.EventProcessor;
import foc.notification.service.NotificationDto;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;

/**
 * Drives {@link EventProcessor} directly (real transaction commit →
 * AFTER_COMMIT push) and asserts delivery through real STOMP sessions.
 * The 5-second receive bound doubles as the Order NFR1.1-1.2 budget
 * assertion. Subscription readiness is awaited via
 * {@link SimpUserRegistry} — never with sleeps — and negative cases
 * are proven with sentinel frames.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		// Own H2 instance so this context never shares tables with the
		// cached broker-free context used by the other test classes.
		"spring.datasource.url=jdbc:h2:mem:notification-stomp;DB_CLOSE_DELAY=-1"
})
class StompPushIntegrationTest {

	private static final String TEST_SECRET = "test-secret-0123456789abcdef0123456789abcdef";
	private static final Instant OCCURRED_AT = Instant.parse("2026-09-19T08:30:00Z");

	/** Revoked jtis for the fake denylist; cleared after each test. */
	private static final Set<String> REVOKED_JTIS = ConcurrentHashMap.newKeySet();

	/** Per-user reset cutoffs (G11) for the fake denylist. */
	private static final ConcurrentMap<String, Instant> USER_CUTOFFS = new ConcurrentHashMap<>();

	/**
	 * Replaces the Redis adapter so the D23 interceptor path is
	 * exercised without a running Redis — the key shapes, the strict
	 * iat comparison and the fail-open mechanics are covered by
	 * RedisTokenDenylistTest.
	 */
	@TestConfiguration
	static class FakeDenylistConfig {
		@Bean
		@Primary
		TokenDenylist fakeTokenDenylist() {
			return new TokenDenylist() {
				@Override
				public boolean isRevoked(String jti) {
					return REVOKED_JTIS.contains(jti);
				}

				@Override
				public boolean isUserRevokedSince(String subject, Instant issuedAt) {
					Instant cutoff = USER_CUTOFFS.get(subject);
					return cutoff != null && issuedAt.isBefore(cutoff);
				}
			};
		}
	}

	@LocalServerPort
	private int port;

	@Autowired
	private EventProcessor eventProcessor;

	@Autowired
	private SimpUserRegistry userRegistry;

	private final List<StompSession> sessions = new java.util.ArrayList<>();

	@AfterEach
	void closeSessions() {
		sessions.forEach(session -> {
			if (session.isConnected()) {
				session.disconnect();
			}
		});
		sessions.clear();
		REVOKED_JTIS.clear();
		USER_CUTOFFS.clear();
	}

	private static String token(String userId) {
		return token(userId, null);
	}

	private static String token(String userId, String jti) {
		return token(userId, jti, Instant.now());
	}

	private static String token(String userId, String jti, Instant issuedAt) {
		return Jwts.builder()
				.subject(userId)
				.id(jti)
				.issuedAt(issuedAt == null ? null : Date.from(issuedAt))
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
				.compact();
	}

	private static RequestAccepted accepted(String eventId, String requestId, List<String> parties) {
		return new RequestAccepted(eventId, OCCURRED_AT, "order-service", "c-push", parties,
				requestId, "usr-req-1001", "usr-cou-2002", "Techno Edge", "COM3-01-19", "push");
	}

	private static RequestCollected collected(String eventId, String requestId, List<String> parties) {
		return new RequestCollected(eventId, OCCURRED_AT, "order-service", "c-push", parties,
				requestId, "usr-req-1001", "usr-cou-2002", "Techno Edge", "COM3-01-19", "push");
	}

	private static WebSocketStompClient rawWebSocketClient() {
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		client.setMessageConverter(new JacksonJsonMessageConverter());
		return client;
	}

	private StompSession connect(WebSocketStompClient client, String url, String authorization)
			throws Exception {
		StompHeaders connectHeaders = new StompHeaders();
		if (authorization != null) {
			connectHeaders.add("Authorization", authorization);
		}
		StompSession session = client
				.connectAsync(url, (org.springframework.web.socket.WebSocketHttpHeaders) null,
						connectHeaders, new StompSessionHandlerAdapter() {
						})
				.get(5, TimeUnit.SECONDS);
		sessions.add(session);
		return session;
	}

	/** Connects over the raw WebSocket transport and subscribes; returns the frame queue. */
	private BlockingQueue<NotificationDto> subscribeAsUser(String userId) throws Exception {
		StompSession session = connect(rawWebSocketClient(),
				"ws://localhost:" + port + "/ws/websocket", "Bearer " + token(userId));
		return subscribe(session, userId);
	}

	private BlockingQueue<NotificationDto> subscribe(StompSession session, String userId) {
		BlockingQueue<NotificationDto> frames = new LinkedBlockingQueue<>();
		session.subscribe("/user/queue/notifications", new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return NotificationDto.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				frames.add((NotificationDto) payload);
			}
		});
		// The subscription is registered asynchronously; await it so the
		// push cannot race ahead of the broker registration.
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			var user = userRegistry.getUser(userId);
			assertThat(user).isNotNull();
			assertThat(user.getSessions()).anySatisfy(simpSession ->
					assertThat(simpSession.getSubscriptions()).isNotEmpty());
		});
		return frames;
	}

	@Test
	void pushesStoredNotificationToSubscribedUserAfterCommit() throws Exception {
		BlockingQueue<NotificationDto> frames = subscribeAsUser("usr-push-1");

		eventProcessor.process(accepted("push-e1", "ord-push-1", List.of("usr-push-1")));

		NotificationDto frame = frames.poll(5, TimeUnit.SECONDS);
		assertThat(frame).isNotNull();
		assertThat(frame.id()).isNotNull();
		assertThat(frame.eventType()).isEqualTo("request.accepted");
		// Party fields stay IDs — no display-name keys in the frame; the
		// web hydrates courierId at read time (D32).
		assertThat(frame.payload()).isEqualTo(Map.of(
				"requestId", "ord-push-1",
				"requesterId", "usr-req-1001",
				"courierId", "usr-cou-2002",
				"pickupLocation", "Techno Edge",
				"dropoffLocation", "COM3-01-19",
				"note", "push"));
		assertThat(frame.occurredAt()).isEqualTo(OCCURRED_AT);
		assertThat(frame.read()).isFalse();
		assertThat(frame.createdAt()).isNotNull();
	}

	@Test
	void deliversOnlyToEachNotificationsOwnRecipient() throws Exception {
		BlockingQueue<NotificationDto> userA = subscribeAsUser("usr-iso-a");
		BlockingQueue<NotificationDto> userB = subscribeAsUser("usr-iso-b");

		eventProcessor.process(accepted("iso-e1", "ord-iso-1", List.of("usr-iso-a", "usr-iso-b")));
		assertThat(userA.poll(5, TimeUnit.SECONDS)).isNotNull();
		assertThat(userB.poll(5, TimeUnit.SECONDS)).isNotNull();

		// Only A is a party; B must stay silent. A's frame is the sentinel
		// proving the push cycle completed before we assert B's silence.
		eventProcessor.process(collected("iso-e2", "ord-iso-1", List.of("usr-iso-a")));
		assertThat(userA.poll(5, TimeUnit.SECONDS)).isNotNull();
		assertThat(userB.poll(200, TimeUnit.MILLISECONDS)).isNull();
	}

	@Test
	void pushesNothingForDuplicateEvents() throws Exception {
		BlockingQueue<NotificationDto> frames = subscribeAsUser("usr-dis-1");

		eventProcessor.process(accepted("dis-e1", "ord-dis-1", List.of("usr-dis-1")));
		assertThat(frames.poll(5, TimeUnit.SECONDS)).isNotNull();

		// Duplicate event ID: discarded, no push.
		eventProcessor.process(accepted("dis-e1", "ord-dis-1", List.of("usr-dis-1")));

		// Sentinel: once it arrives, the discarded duplicate is fully
		// processed — and must not have produced a frame of its own.
		eventProcessor.process(collected("dis-e3", "ord-dis-1", List.of("usr-dis-1")));
		NotificationDto sentinel = frames.poll(5, TimeUnit.SECONDS);
		assertThat(sentinel).isNotNull();
		assertThat(sentinel.eventType()).isEqualTo("request.collected");
		assertThat(frames).isEmpty();
	}

	@Test
	void rejectsConnectWithoutToken() {
		assertThatThrownBy(() -> connect(rawWebSocketClient(),
				"ws://localhost:" + port + "/ws/websocket", null))
				.isInstanceOf(Exception.class);
	}

	@Test
	void rejectsConnectWithInvalidToken() {
		assertThatThrownBy(() -> connect(rawWebSocketClient(),
				"ws://localhost:" + port + "/ws/websocket", "Bearer not-a-jwt"))
				.isInstanceOf(Exception.class);
	}

	@Test
	void rejectsConnectWithExpiredToken() {
		String expired = Jwts.builder()
				.subject("usr-exp-1")
				.expiration(new Date(System.currentTimeMillis() - 60_000))
				.signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
				.compact();

		assertThatThrownBy(() -> connect(rawWebSocketClient(),
				"ws://localhost:" + port + "/ws/websocket", "Bearer " + expired))
				.isInstanceOf(Exception.class);
	}

	@Test
	void rejectsConnectWithRevokedToken() {
		REVOKED_JTIS.add("revoked-jti");

		assertThatThrownBy(() -> connect(rawWebSocketClient(),
				"ws://localhost:" + port + "/ws/websocket",
				"Bearer " + token("usr-rev-1", "revoked-jti")))
				.isInstanceOf(Exception.class);
	}

	@Test
	void connectsWhenJtiIsNotRevoked() throws Exception {
		REVOKED_JTIS.add("someone-elses-jti");

		StompSession session = connect(rawWebSocketClient(),
				"ws://localhost:" + port + "/ws/websocket",
				"Bearer " + token("usr-rev-2", "live-jti"));

		assertThat(session.isConnected()).isTrue();
	}

	@Test
	void connectsWithTokenCarryingNoJti() throws Exception {
		// Parity with the gateway's auth.lua: no jti means nothing to
		// look up, not a reason to refuse the session.
		StompSession session = connect(rawWebSocketClient(),
				"ws://localhost:" + port + "/ws/websocket", "Bearer " + token("usr-rev-3"));

		assertThat(session.isConnected()).isTrue();
	}

	@Test
	void rejectsConnectWithTokenIssuedBeforeThePasswordResetCutoff() {
		// G11 (PR #185 review): a reset stamps the account's cutoff
		// instead of each outstanding jti, so the jti lookup alone would
		// let this token open a session until its own exp.
		Instant issuedAt = Instant.now().minusSeconds(600);
		USER_CUTOFFS.put("usr-cut-1", issuedAt.plusSeconds(1));

		assertThatThrownBy(() -> connect(rawWebSocketClient(),
				"ws://localhost:" + port + "/ws/websocket",
				"Bearer " + token("usr-cut-1", "live-jti", issuedAt)))
				.isInstanceOf(Exception.class);
	}

	@Test
	void connectsWithTokenIssuedAfterThePasswordResetCutoff() throws Exception {
		Instant cutoff = Instant.now().minusSeconds(600);
		USER_CUTOFFS.put("usr-cut-2", cutoff);

		StompSession session = connect(rawWebSocketClient(),
				"ws://localhost:" + port + "/ws/websocket",
				"Bearer " + token("usr-cut-2", "live-jti", cutoff.plusSeconds(1)));

		assertThat(session.isConnected()).isTrue();
	}

	@Test
	void connectsWithTokenCarryingNoIssuedAt() throws Exception {
		// Parity with auth.lua: a missing iat skips the cutoff lookup
		// rather than refusing the session.
		USER_CUTOFFS.put("usr-cut-3", Instant.now());

		StompSession session = connect(rawWebSocketClient(),
				"ws://localhost:" + port + "/ws/websocket",
				"Bearer " + token("usr-cut-3", "live-jti", null));

		assertThat(session.isConnected()).isTrue();
	}

	/**
	 * Opens a bare WebSocket (no STOMP client) to {@code /ws/websocket},
	 * sends one raw frame and returns the first text the server replies
	 * with — for the frames Spring's StompSession cannot be made to send.
	 */
	private String sendRawFrame(String frame) throws Exception {
		BlockingQueue<String> replies = new LinkedBlockingQueue<>();
		WebSocketSession wsSession = new StandardWebSocketClient()
				.execute(new TextWebSocketHandler() {
					@Override
					protected void handleTextMessage(WebSocketSession session, TextMessage message) {
						replies.add(message.getPayload());
					}
				}, "ws://localhost:" + port + "/ws/websocket")
				.get(5, TimeUnit.SECONDS);
		try {
			wsSession.sendMessage(new TextMessage(frame));
			String reply = replies.poll(5, TimeUnit.SECONDS);
			assertThat(reply).isNotNull();
			return reply;
		} finally {
			wsSession.close();
		}
	}

	@Test
	void rejectsStompFrameWithoutToken() throws Exception {
		// STOMP 1.2 lets a client open the session with a STOMP frame
		// instead of CONNECT; Spring treats it as a connect, so it must
		// pass the same checks (PR #185 review — this got CONNECTED).
		String reply = sendRawFrame("STOMP\naccept-version:1.2\nhost:localhost\n\n\0");

		assertThat(reply).startsWith("ERROR");
	}

	@Test
	void authenticatesStompFrameLikeConnect() throws Exception {
		String reply = sendRawFrame("STOMP\naccept-version:1.2\nhost:localhost\n"
				+ "Authorization:Bearer " + token("usr-stomp-frame-1") + "\n\n\0");

		assertThat(reply).startsWith("CONNECTED");
	}

	@Test
	void rejectsClientSendToAnotherUsersQueue() throws Exception {
		// PR #185 review: with no application destination prefix, a SEND
		// to /user/{id}/queue/notifications was resolved and delivered to
		// that user — a tokenless spoofed badge bump. Clients never SEND
		// here, so every SEND is refused.
		BlockingQueue<NotificationDto> victim = subscribeAsUser("usr-spoof-victim");
		StompSession attacker = connect(rawWebSocketClient(),
				"ws://localhost:" + port + "/ws/websocket",
				"Bearer " + token("usr-spoof-attacker"));

		attacker.send("/user/usr-spoof-victim/queue/notifications",
				Map.of("eventType", "request.accepted"));

		// The refusal is an ERROR frame, which closes the sender's session.
		await().atMost(Duration.ofSeconds(5)).until(() -> !attacker.isConnected());

		// Sentinel: a real push still arrives, and arrives alone — the
		// spoofed frame would have been queued ahead of it.
		eventProcessor.process(accepted("spoof-e1", "ord-spoof-1", List.of("usr-spoof-victim")));
		NotificationDto sentinel = victim.poll(5, TimeUnit.SECONDS);
		assertThat(sentinel).isNotNull();
		assertThat(sentinel.eventType()).isEqualTo("request.accepted");
		assertThat(victim).isEmpty();
	}

	@Test
	void rejectsSubscriptionOutsideTheNotificationsDestination() throws Exception {
		StompSession session = connect(rawWebSocketClient(),
				"ws://localhost:" + port + "/ws/websocket", "Bearer " + token("usr-sub-stray"));

		// Straight at the broker, bypassing user-destination resolution.
		session.subscribe("/queue/notifications-userstolen", new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return NotificationDto.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
			}
		});

		await().atMost(Duration.ofSeconds(5)).until(() -> !session.isConnected());
	}

	@Test
	void deliversThroughSockJsXhrFallbackWithoutWebSocket() throws Exception {
		// XHR-only transport list: forces the SockJS fallback path a
		// WebSocket-capable local client would otherwise never take.
		SockJsClient sockJsClient = new SockJsClient(List.of(new RestTemplateXhrTransport()));
		WebSocketStompClient client = new WebSocketStompClient(sockJsClient);
		client.setMessageConverter(new JacksonJsonMessageConverter());

		StompSession session = connect(client, "http://localhost:" + port + "/ws",
				"Bearer " + token("usr-sockjs-1"));
		BlockingQueue<NotificationDto> frames = subscribe(session, "usr-sockjs-1");

		eventProcessor.process(accepted("sockjs-e1", "ord-sockjs-1", List.of("usr-sockjs-1")));

		assertThat(frames.poll(5, TimeUnit.SECONDS)).isNotNull();
	}
}
