/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: STOMP gateway configuration for issue #67 per team decisions
 * D2 (STOMP simple broker, per-user destinations), D10 (single
 * instance, no relay) and the author's session-mechanics decisions
 * (CONNECT-frame JWT auth, /ws endpoint with SockJS fallback, 10 s
 * heartbeats; docs/notification-service.md "Session mechanics").
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.stomp;

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
import tools.jackson.databind.json.JsonMapper;

/**
 * The push gateway's transport wiring (D2): STOMP over WebSocket at
 * {@code /ws} with SockJS fallback (native WebSocket is SockJS's
 * first transport, so capable browsers pay nothing; upgrade-hostile
 * networks degrade to XHR transports), the in-memory simple broker on
 * {@code /queue} with per-user destinations (D10: single instance, no
 * relay), 10 s heartbeats both ways, and JWT authentication at
 * CONNECT via {@link JwtChannelInterceptor}. Clients never SEND, so
 * no application destination prefix is registered. Frames are
 * serialized by Boot's auto-configured Jackson mapper, mirroring the
 * AMQP converter (D15).
 *
 * <p>The dedicated heartbeat {@link TaskScheduler} bean is required
 * (heartbeats without a scheduler fail at startup). Note for issue
 * #68: Boot's TaskSchedulingAutoConfiguration backs off because of
 * this bean, so a future {@code @Scheduled} purge job will run on it.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketStompConfig implements WebSocketMessageBrokerConfigurer {

	private final JwtChannelInterceptor jwtChannelInterceptor;
	private final JsonMapper jsonMapper;
	private final String[] allowedOriginPatterns;

	WebSocketStompConfig(JwtChannelInterceptor jwtChannelInterceptor, JsonMapper jsonMapper,
			@Value("${notification.websocket.allowed-origins}") String allowedOrigins) {
		this.jwtChannelInterceptor = jwtChannelInterceptor;
		this.jsonMapper = jsonMapper;
		this.allowedOriginPatterns = allowedOrigins.split(",");
	}

	@Bean
	TaskScheduler webSocketHeartbeatScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("ws-heartbeat-");
		return scheduler;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
				.setAllowedOriginPatterns(allowedOriginPatterns)
				.withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/queue")
				.setHeartbeatValue(new long[] {10_000, 10_000})
				.setTaskScheduler(webSocketHeartbeatScheduler());
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(jwtChannelInterceptor);
	}

	@Override
	public boolean configureMessageConverters(List<MessageConverter> converters) {
		converters.add(new JacksonJsonMessageConverter(jsonMapper));
		return false;
	}
}
