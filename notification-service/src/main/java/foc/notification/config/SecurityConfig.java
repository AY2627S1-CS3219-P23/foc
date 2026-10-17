/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: Spring Security filter chain scaffolded from credit-service's
 * SecurityConfig for the REST API (F3.1, F3.2). Every route except
 * health, /error and the WebSocket handshake needs a valid bearer
 * token; the handshake paths stay open because a browser's WS upgrade
 * and SockJS fallbacks carry no Authorization header — the STOMP
 * CONNECT frame carries the JWT instead, checked by
 * JwtChannelInterceptor with the D23 denylist. The HTTP routes do not
 * re-check the denylist: the gateway does that for every /api request
 * (docs/gateway.md, G4). Stateless sessions and the JWT filter built
 * inline, both as credit-service does.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.config;

import foc.notification.security.JwtAuthenticationFilter;
import foc.notification.security.JwtVerifier;
import foc.notification.security.RestAccessDeniedHandler;
import foc.notification.security.RestAuthEntryPoint;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	// the SPA calls this service from the browser through the API gateway,
	// which passes these CORS headers through unchanged (docs/gateway.md,
	// "CORS stays per-service"); PATCH marks read/unread and POST marks
	// all read, so both write methods must be allowed or the browser's
	// preflight refuses them
	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${notification.web-allowed-origin}") String webAllowedOrigin) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(List.of(webAllowedOrigin));
		cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cors);
		return source;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtVerifier jwtVerifier,
			RestAuthEntryPoint restAuthEntryPoint,
			RestAccessDeniedHandler restAccessDeniedHandler) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				// uses the corsConfigurationSource bean; answers preflights
				// before the authorization rules below
				.cors(Customizer.withDefaults())
				// every request carries its own bearer token: no session, so
				// a rejected request doesn't leave one behind either
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// /error must stay open: an exception on any route makes
						// the container forward here to render the response
						.requestMatchers("/error").permitAll()
						// health checks (compose depends_on / probes) carry no credentials
						.requestMatchers("/actuator/health").permitAll()
						// the WS upgrade and SockJS fallback requests (/ws/info,
						// /ws/{server}/{session}/{transport}) carry no bearer
						// header; the STOMP CONNECT frame authenticates instead
						// (JwtChannelInterceptor, D23)
						.requestMatchers("/ws", "/ws/**").permitAll()
						// everything else is the caller's own notifications
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(restAuthEntryPoint)
						.accessDeniedHandler(restAccessDeniedHandler))
				// built here, not a bean: a Filter bean is also registered with
				// the servlet container and would run outside this chain
				.addFilterBefore(new JwtAuthenticationFilter(jwtVerifier),
						UsernamePasswordAuthenticationFilter.class)
				.build();
	}
}
