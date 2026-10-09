/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-09.
 * Scope: copied from supplier-service's JwtAuthenticationFilter with the
 * package changed. Reads the bearer token, verifies it, and puts the
 * caller's user ID and a ROLE_<role> authority in the security context.
 * A missing or invalid token leaves the context unauthenticated, so
 * SecurityConfig and RestAuthEntryPoint render one consistent 401.
 * 2026-10-10, Claude Code (Opus 5.5), PR #166 Copilot review: no longer a
 * @Component: SecurityConfig builds it, so it runs only in the security
 * chain, never as a second servlet-container filter.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.security;

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
import org.springframework.web.filter.OncePerRequestFilter;

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
                String token = header.substring(BEARER_PREFIX.length()).trim();
                JwtVerifier.VerifiedToken verified = jwtVerifier.verify(token);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + verified.role()));
                var authentication =
                        new UsernamePasswordAuthenticationToken(verified.userId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // "Bearer " with nothing after it is an IllegalArgumentException
                // in jjwt, not a JwtException; both are a 401, never a 500
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
