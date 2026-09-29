/* 
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated placeholder class to enable use of password encoder.
Author review: Ryan validated correctness.
2026-09-23 (Claude Code, Opus 5.5): /error permitted so exceptions from /auth/**
keep their real status instead of a bare 403.
2026-09-23 (Claude Code, Opus 5.5): /actuator/health permitted so container
health checks work (PR #126 review).
2026-09-26 (Claude Code, Opus 5.5), issue #96: URL rules restrict the admin
endpoints (GET /users, PATCH and DELETE /users/{id}) to ADMIN/OWNER, so a
non-admin gets 403 before the request is parsed; /users/me stays open to
any signed-in user.
2026-09-27 (Claude Code, Opus 5.5), PR #135 review: HEAD /users restricted
like GET, since Spring MVC serves HEAD through the GET list handler.
2026-09-27 (Claude Code, Opus 5.5), PR #135 review: /users rules rewritten to
fail closed — only GET /users/* and DELETE /users/me are open to any
signed-in user; every other method on /users and /users/* needs ADMIN/OWNER
(replaces the per-method admin lines and the HEAD rule).
2026-09-29 (Claude Code, Opus 5.5), issues #87/#89: CORS for the web origin
(WEB_ALLOWED_ORIGIN, the variable supplier-service already uses), applied in
the filter chain so browser preflights pass before the fail-closed rules.
CORS in this PR per team decision.
2026-09-29 (Claude Code, Opus 5.5), issue #91: JwtAuthenticationFilter added
to the chain (bearer JWT -> ROLE_ authority, design doc §3), stateless
sessions, and problem+json 401/403 from SecurityProblemResponses (a missing
token is now 401, not Spring's default 403).

*/

package foc.user.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import foc.user.security.JwtAuthenticationFilter;
import foc.user.security.JwtVerifier;
import foc.user.security.SecurityProblemResponses;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // the SPA calls this service directly from the browser (no gateway)
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${user.web-allowed-origin}") String webAllowedOrigin) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(webAllowedOrigin));
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtVerifier jwtVerifier,
            SecurityProblemResponses problems) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            // uses the corsConfigurationSource bean; answers preflights
            // before the authorization rules below
            .cors(Customizer.withDefaults())
            // every request carries its own bearer token: no session
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // the caller comes from the Authorization header (design doc §3)
            .addFilterBefore(new JwtAuthenticationFilter(jwtVerifier, problems),
                UsernamePasswordAuthenticationFilter.class)
            // 401 without usable credentials, 403 for the wrong role, both
            // problem+json like the controllers' errors
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems))
            .authorizeHttpRequests(auth -> auth
                // /error must stay open: any exception thrown from a
                // permitAll route (e.g. /auth/**) makes the container
                // forward here to render the response, and without this
                // Spring Security blocks that internal forward, clobbering
                // the real status/body with a bare 403.
                // #90's POST /auth/logout needs an authenticated() rule
                // above this line (JwtAuthenticationFilter already reads
                // its token)
                .requestMatchers("/auth/**", "/error").permitAll()
                // health checks (compose depends_on / probes) carry no credentials
                .requestMatchers("/actuator/health").permitAll()
                // routes open to any signed-in user, each listed by method:
                // own and public profile, and self-deletion. A new /users/me
                // route (e.g. PATCH from #92) needs its own line here.
                .requestMatchers(HttpMethod.GET, "/users/*").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/users/me").authenticated()
                // everything else under /users, whatever the method, is an
                // admin endpoint (#96). Method-less so unlisted methods (HEAD,
                // OPTIONS, POST, ...) fail closed, and checked here rather
                // than on the controller so a non-admin gets 403 before the
                // path id or body is parsed (which would otherwise give a 400)
                .requestMatchers("/users", "/users/*").hasAnyRole("ADMIN", "OWNER")
                .anyRequest().authenticated()
            )
            .build();
    }
}