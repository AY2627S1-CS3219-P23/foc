/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
 * Scope: rating-service's SecurityConfig (stateless bearer-token chain,
 * 401/403 problem+json, per-service CORS) with notification-service's
 * open /ws paths (the STOMP CONNECT frame authenticates instead) and its
 * REST-only CORS registration, plus the /internal/** calls, which carry
 * the internal token instead of a bearer token (C12).
 * 2026-10-31: POST allowed in CORS for the mark-read call (C19).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.config;

import foc.chat.security.JwtAuthenticationFilter;
import foc.chat.security.JwtVerifier;
import foc.chat.security.RestAccessDeniedHandler;
import foc.chat.security.RestAuthEntryPoint;
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
    // "CORS stays per-service")
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${chat.web-allowed-origin}") String webAllowedOrigin) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(webAllowedOrigin));
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // REST routes only, never /**: the /ws handshake has its own origin
        // policy (chat.websocket.allowed-origins), which a global
        // registration here would override (notification-service, PR #198)
        source.registerCorsConfiguration("/chats/**", cors);
        source.registerCorsConfiguration("/chats", cors);
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
                // every request carries its own bearer token: no session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /error must stay open: an exception on any route makes
                        // the container forward here to render the response
                        .requestMatchers("/error").permitAll()
                        // health checks (compose depends_on / probes) carry no credentials
                        .requestMatchers("/actuator/health").permitAll()
                        // the WS upgrade and SockJS requests carry no bearer
                        // header; the STOMP CONNECT frame authenticates instead
                        // (ChatChannelInterceptor)
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        // order-service's calls carry the internal token, which
                        // InternalChatController checks (C12)
                        .requestMatchers("/internal/**").permitAll()
                        // every chat route acts as the caller
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
