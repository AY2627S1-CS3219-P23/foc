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
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import foc.contracts.events.request.RequestAccepted;
import foc.contracts.events.request.RequestCollected;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
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
	}

	private static String token(String userId) {
		return Jwts.builder()
				.subject(userId)
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
