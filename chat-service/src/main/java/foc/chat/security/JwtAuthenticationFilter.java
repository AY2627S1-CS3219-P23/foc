/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
 * Scope: copied from rating-service's JwtAuthenticationFilter
 * (user ID and ROLE_<role> in the security context); only the package
 * and the property prefix changed.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.security;

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
