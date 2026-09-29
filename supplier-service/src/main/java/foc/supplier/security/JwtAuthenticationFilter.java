/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-29.
 * Scope: JWT authentication filter for issue #106. Reads the bearer
 * token, verifies it via JwtVerifier, and populates the security
 * context with the caller's user ID and a single ROLE_<role> authority
 * (Spring Security's hasRole(...) convention) from the token's role
 * claim. A missing or invalid token leaves the context unauthenticated
 * rather than rejecting the request here, so SecurityConfig's
 * authorization rules and RestAuthEntryPoint render a single,
 * consistent 401 for both cases.
 * Reviewed by: [pending]
 */
package foc.supplier.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                JwtVerifier.VerifiedToken verified = jwtVerifier.verify(header.substring(BEARER_PREFIX.length()));
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + verified.role()));
                var authentication =
                        new UsernamePasswordAuthenticationToken(verified.userId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
