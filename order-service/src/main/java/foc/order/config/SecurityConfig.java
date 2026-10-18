/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: copied from credit-service's SecurityConfig for the ownership
 * checks: stateless bearer-token chain, every route but /error and
 * /actuator/health authenticated, 401s from RestAuthEntryPoint, and
 * per-service CORS for the SPA (docs/gateway.md, "CORS stays
 * per-service") with POST allowed, since every order route is a POST.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.config;

import foc.order.security.JwtAuthenticationFilter;
import foc.order.security.JwtVerifier;
import foc.order.security.RestAuthEntryPoint;
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
            @Value("${order.web-allowed-origin}") String webAllowedOrigin) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(webAllowedOrigin));
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtVerifier jwtVerifier,
            RestAuthEntryPoint restAuthEntryPoint) throws Exception {
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
                        // every request route acts as the caller
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(restAuthEntryPoint))
                // built here, not a bean: a Filter bean is also registered with
                // the servlet container and would run outside this chain
                .addFilterBefore(new JwtAuthenticationFilter(jwtVerifier),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
