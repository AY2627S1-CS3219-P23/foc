/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: end-to-end over a real WebSocket on the raw SockJS transport
 * the web app uses: a party sends a 2 MB image in the frame (C7, C8) and
 * both parties' subscriptions receive it; a close reaches them as a
 * CLOSED frame; a refused send comes back on the sender's error queue.
 * 2026-10-31: the receiver's own queue gets the unread frame (C16-C19),
 * without being subscribed to the chat.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.stomp;

import static org.assertj.core.api.Assertions.assertThat;

import foc.chat.PostgresTestContainer;
import foc.chat.TestTokens;
import foc.chat.dto.ChatView;
import foc.chat.service.ChatService;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Base64;
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
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatStompIntegrationTest extends PostgresTestContainer {

    private static final long REQUESTER = 11;
    private static final long COURIER = 22;
    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @LocalServerPort
    private int port;

    @Autowired
    private ChatService chatService;

    private WebSocketStompClient client;

    @AfterEach
    void stop() {
        if (client != null) {
            client.stop();
        }
    }

    private StompSession connect(String token) throws Exception {
        client = client == null ? newClient() : client;
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return client.connectAsync("ws://localhost:" + port + "/ws/websocket", new WebSocketHttpHeaders(),
                // generous: the first connect of a fresh context on a busy machine
                connectHeaders, new StompSessionHandlerAdapter() { }).get(15, TimeUnit.SECONDS);
    }

    private static WebSocketStompClient newClient() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        return stompClient;
    }

    private static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination)
            throws InterruptedException {
        BlockingQueue<Map<String, Object>> frames = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add((Map<String, Object>) payload);
            }
        });
        // SUBSCRIBE has no receipt here; give the broker a moment to register it
        Thread.sleep(300);
        return frames;
    }

    @Test
    void aTwoMegabyteImageReachesBothParties() throws Exception {
        ChatView chat = chatService.open(System.nanoTime(), REQUESTER, COURIER);
        StompSession requester = connect(TestTokens.user(REQUESTER));
        StompSession courier = connect(TestTokens.user(COURIER));
        BlockingQueue<Map<String, Object>> requesterFrames = subscribe(requester, "/topic/chats/" + chat.id());
        BlockingQueue<Map<String, Object>> courierFrames = subscribe(courier, "/topic/chats/" + chat.id());

        byte[] image = Arrays.copyOf(PNG_HEADER, 2 * 1024 * 1024);
        requester.send("/app/chats/" + chat.id() + "/messages", Map.of(
                "text", "this one?",
                "image", Map.of("contentType", "image/png", "data", Base64.getEncoder().encodeToString(image))));

        Map<String, Object> received = courierFrames.poll(10, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.get("type")).isEqualTo("MESSAGE");
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) received.get("message");
        assertThat(message.get("text")).isEqualTo("this one?");
        assertThat(message.get("hasImage")).isEqualTo(true);
        assertThat(requesterFrames.poll(10, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void theReceiverIsToldOfAnUnreadMessageOnTheirOwnQueue() throws Exception {
        long request = System.nanoTime();
        ChatView chat = chatService.open(request, REQUESTER, COURIER);
        StompSession courier = connect(TestTokens.user(COURIER));
        StompSession requester = connect(TestTokens.user(REQUESTER));
        BlockingQueue<Map<String, Object>> unread = subscribe(requester, "/user/queue/chat");
        BlockingQueue<Map<String, Object>> courierUnread = subscribe(courier, "/user/queue/chat");

        courier.send("/app/chats/" + chat.id() + "/messages", Map.of("text", "outside now"));

        Map<String, Object> frame = unread.poll(10, TimeUnit.SECONDS);
        assertThat(frame).isNotNull();
        assertThat(frame.get("chatId")).isEqualTo(chat.id().toString());
        assertThat(((Number) frame.get("requestId")).longValue()).isEqualTo(request);
        assertThat(frame.get("first")).isEqualTo(true);
        assertThat(frame.get("preview")).isEqualTo("outside now");
        assertThat(((Number) frame.get("senderId")).longValue()).isEqualTo(COURIER);
        // the sender is not told of their own message
        assertThat(courierUnread.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void aCloseReachesTheSubscribers() throws Exception {
        long request = System.nanoTime();
        ChatView chat = chatService.open(request, REQUESTER, COURIER);
        StompSession courier = connect(TestTokens.user(COURIER));
        BlockingQueue<Map<String, Object>> frames = subscribe(courier, "/topic/chats/" + chat.id());

        chatService.close(request);

        Map<String, Object> closed = frames.poll(10, TimeUnit.SECONDS);
        assertThat(closed).isNotNull();
        assertThat(closed.get("type")).isEqualTo("CLOSED");
        assertThat(closed.get("chatId")).isEqualTo(chat.id().toString());
    }

    @Test
    void aRefusedSendComesBackToTheSender() throws Exception {
        ChatView chat = chatService.open(System.nanoTime(), REQUESTER, COURIER);
        StompSession requester = connect(TestTokens.user(REQUESTER));
        BlockingQueue<Map<String, Object>> errors = subscribe(requester, "/user/queue/errors");

        requester.send("/app/chats/" + chat.id() + "/messages", Map.of("text", "x".repeat(501)));

        Map<String, Object> error = errors.poll(10, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat((String) error.get("detail")).contains("500");
    }
}
