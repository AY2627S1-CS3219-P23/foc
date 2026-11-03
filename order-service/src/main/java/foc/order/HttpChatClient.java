/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the HTTP adapter of ChatClient: POST /internal/chats/open|close
 * on chat-service with the shared internal token (C12), short timeouts.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

import foc.order.model.ChatCall;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class HttpChatClient implements ChatClient {

    private static final Logger log = LoggerFactory.getLogger(HttpChatClient.class);

    static final String TOKEN_HEADER = "X-Internal-Token";

    private final RestClient rest;
    private final String token;

    HttpChatClient(@Value("${order.chat.base-url}") String baseUrl,
            @Value("${order.chat.internal-token}") String token) {
        // a stalled chat-service must not hold up the dispatcher's poll
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
        this.token = token;
    }

    @Override
    public boolean send(ChatCall call) {
        String path;
        Map<String, Object> body;
        if (call.getKind() == ChatCall.Kind.OPEN) {
            path = "/internal/chats/open";
            body = Map.of("requestId", call.getRequestId(), "requesterId", call.getRequesterId(),
                    "courierId", call.getCourierId());
        } else {
            path = "/internal/chats/close";
            body = Map.of("requestId", call.getRequestId());
        }
        try {
            rest.post().uri(path)
                    .header(TOKEN_HEADER, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("chat {} for request {} not taken (attempt {}): {}", call.getKind(), call.getRequestId(),
                    call.getAttempts() + 1, e.getMessage());
            return false;
        }
    }
}
