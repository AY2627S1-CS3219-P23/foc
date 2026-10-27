/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-29.
 * Scope: Spring Security filter chain for issue #106, per design doc D2
 * (docs/supplier-service.md): CRUD endpoints admin-gated via the JWT
 * role claim, browse/search open to any authenticated user. Fail-closed
 * URL rules mirror user-service's SecurityConfig (PR #135/#141): GET is
 * the only method open to any signed-in user, everything else under
 * /suppliers needs ADMIN so an unlisted method (HEAD, POST, ...) is
 * rejected before it reaches a controller. CORS moves here from the old
 * CorsConfig (WebMvcConfigurer), replacing it — wired into the filter
 * chain so browser preflights pass before the fail-closed rules
 * (mirrors user-service PR #141's CORS-in-Security-chain fix).
 * PR #143 review (LeongWZ): admin CRUD gate changed from ADMIN-only to
 * ADMIN-or-OWNER — team decision (author, 2026-09-29): OWNER is the
 * platform's admin-equivalent super admin (issue #97) and should have
 * every ADMIN capability, supplier CRUD included, mirroring
 * user-service's own hasAnyRole("ADMIN", "OWNER") pattern.
 * 2026-10-26, Claude Code (Sonnet 5.5), issue #225 (N8): PUT/DELETE on
 * /suppliers/{id}/bookmark open to any authenticated user, ahead of the
 * admin rule that would otherwise cover them. /purge/** requires the
 * SERVICE role (the account-purge cleanup, D12).
 * Reviewed by: [pending]
 */
package foc.supplier.config;

import foc.supplier.security.JwtAuthenticationFilter;
import foc.supplier.security.RestAccessDeniedHandler;
import foc.supplier.security.RestAuthEntryPoint;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    // the SPA calls this service directly from the browser (no gateway)
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${supplier.web-allowed-origin}") String webAllowedOrigin) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(webAllowedOrigin));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
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
                        // /error must stay open: any exception thrown from a
                        // permitAll route makes the container forward here to
                        // render the response, and without this Spring
                        // Security blocks that internal forward.
                        .requestMatchers("/error").permitAll()
                        // health checks (compose depends_on / probes) carry no credentials
                        .requestMatchers("/actuator/health").permitAll()
                        // browse/search: open to any authenticated user (D2)
                        .requestMatchers(HttpMethod.GET, "/suppliers", "/suppliers/**").authenticated()
                        // internal: user-service's purge job removing the bookmarks of
                        // accounts it hard-deletes (issue #225, D12). Only a token
                        // minted by a service holding JWT_SECRET carries SERVICE; no
                        // login token does, so no user or admin can call it.
                        .requestMatchers("/purge", "/purge/**").hasRole("SERVICE")
                        // a user's own bookmark add/remove (issue #225) lives under
                        // /suppliers/{id}/bookmark but is not admin CRUD; matched
                        // before the admin rule below. The caller is the JWT subject.
                        .requestMatchers(HttpMethod.PUT, "/suppliers/*/bookmark").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/suppliers/*/bookmark").authenticated()
                        // everything else under /suppliers, whatever the method, is an
                        // admin CRUD endpoint (D2, #6). Method-less so unlisted methods
                        // fail closed. OWNER included: PR #143 review (LeongWZ) —
                        // Role.OWNER (issue #97) is the platform's admin-equivalent
                        // super admin and user-service's own SecurityConfig treats it
                        // as such everywhere (hasAnyRole("ADMIN", "OWNER")); ADMIN-only
                        // here had no basis in design doc D2.
                        .requestMatchers("/suppliers", "/suppliers/**").hasAnyRole("ADMIN", "OWNER")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restAuthEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
