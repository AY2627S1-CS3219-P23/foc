/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: chat-service's own STOMP endpoint (C2), following
 * notification-service's WebSocketStompConfig (/ws with SockJS, the
 * in-memory simple broker, 10 s heartbeats, CONNECT-frame auth, the
 * Jackson converter), plus an application prefix for the send
 * destination and frame limits large enough for a 2 MB image sent
 * base64-encoded in the frame (C7, C8).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.stomp;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import tools.jackson.databind.json.JsonMapper;

/**
 * STOMP over WebSocket at {@code /ws} (SockJS endpoint; the web app
 * connects straight to its raw-WebSocket transport, {@code /ws/websocket},
 * as it does for notification-service). Clients subscribe to
 * {@code /topic/chats/{chatId}}, send to {@code /app/chats/{chatId}/messages}
 * and get their own errors on {@code /user/queue/errors}.
 * {@link ChatChannelInterceptor} authenticates the CONNECT and polices
 * every SUBSCRIBE and SEND; {@link WebSocketBufferSizes} raises the
 * servlet container's own buffer to the same frame limit.
 *
 * <p>The dedicated heartbeat scheduler makes Boot's own scheduler back
 * off, so the retention purge's {@code @Scheduled} job runs on it too
 * (notification-service's note), hence two threads.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketStompConfig implements WebSocketMessageBrokerConfigurer {

    // a 2 MB image is about 2.7 MB in base64, plus the JSON around it
    static final int MAX_FRAME_BYTES = 3 * 1024 * 1024;

    private final ChatChannelInterceptor channelInterceptor;
    private final JsonMapper jsonMapper;
    private final String[] allowedOriginPatterns;

    WebSocketStompConfig(ChatChannelInterceptor channelInterceptor, JsonMapper jsonMapper,
            @Value("${chat.websocket.allowed-origins}") String allowedOrigins) {
        this.channelInterceptor = channelInterceptor;
        this.jsonMapper = jsonMapper;
        // trimmed, so "http://a, http://b" doesn't yield a pattern with a
        // leading space that never matches
        this.allowedOriginPatterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toArray(String[]::new);
    }

    @Bean
    TaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .withSockJS()
                .setStreamBytesLimit(MAX_FRAME_BYTES);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MAX_FRAME_BYTES)
                .setSendBufferSizeLimit(MAX_FRAME_BYTES);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(webSocketHeartbeatScheduler());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(channelInterceptor);
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> converters) {
        converters.add(new JacksonJsonMessageConverter(jsonMapper));
        return false;
    }
}
