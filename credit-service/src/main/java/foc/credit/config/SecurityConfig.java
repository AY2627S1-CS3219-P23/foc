/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-09.
 * Scope: Spring Security filter chain scaffolded from supplier-service's
 * SecurityConfig. The Credit Service's REST API is read-only and for the
 * signed-in user's own balance and history (docs/credit-service.md), so
 * every route except health and /error needs a valid bearer token; there
 * is no role gate yet. CORS sits in the chain so browser preflights pass
 * before the authorization rules.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.config;

import foc.credit.security.JwtAuthenticationFilter;
import foc.credit.security.RestAccessDeniedHandler;
import foc.credit.security.RestAuthEntryPoint;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    // the SPA calls this service directly from the browser (no gateway);
    // the API only reads, so GET is the only method it needs
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${credit.web-allowed-origin}") String webAllowedOrigin) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(webAllowedOrigin));
        cors.setAllowedMethods(List.of("GET", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthEntryPoint restAuthEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // uses the corsConfigurationSource bean; answers preflights
                // before the authorization rules below
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // /error must stay open: an exception on any route makes
                        // the container forward here to render the response
                        .requestMatchers("/error").permitAll()
                        // health checks (compose depends_on / probes) carry no credentials
                        .requestMatchers("/actuator/health").permitAll()
                        // everything else is the caller's own credit data
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restAuthEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
