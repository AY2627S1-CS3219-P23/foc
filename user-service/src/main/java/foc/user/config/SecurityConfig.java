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

*/

package foc.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Permit /auth/** while security is being built
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // /error must stay open: any exception thrown from a
                // permitAll route (e.g. /auth/**) makes the container
                // forward here to render the response, and without this
                // Spring Security blocks that internal forward, clobbering
                // the real status/body with a bare 403.
                .requestMatchers("/auth/**", "/error").permitAll()
                // health checks (compose depends_on / probes) carry no credentials
                .requestMatchers("/actuator/health").permitAll()
                // own-account routes, matched before the admin rules below
                // so /users/me isn't treated as an admin target
                .requestMatchers("/users/me").authenticated()
                // admin endpoints (#96). Checked here rather than on the
                // controller so a non-admin gets 403 before the path id or
                // body is parsed (which would otherwise give them a 400)
                .requestMatchers(HttpMethod.GET, "/users").hasAnyRole("ADMIN", "OWNER")
                .requestMatchers(HttpMethod.PATCH, "/users/*").hasAnyRole("ADMIN", "OWNER")
                .requestMatchers(HttpMethod.DELETE, "/users/*").hasAnyRole("ADMIN", "OWNER")
                .anyRequest().authenticated()
            )
            .build();
    }
}