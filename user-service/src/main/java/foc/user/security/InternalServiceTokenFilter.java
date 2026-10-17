/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-17
Scope: internal service-token filter for issue #66 per the author's
       zero-trust decision (docs/notification-service.md D31, options
       Q&A): notification-service resolves party usernames at ingest,
       and GET /users/{id} must stay credentialed even inside the
       compose network — permitAll was explicitly rejected. The filter
       honors an X-Internal-Token header, constant-time-compared to the
       configured secret, and only on GET /users/* (the public-profile
       route); a wrong or out-of-scope token just stays unauthenticated
       so the authorization rules answer 401, leaking nothing.
Author review: Leong Wei Zhi (via pull request).
*/

package foc.user.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates sibling services (today: notification-service's
 * ingest-time username lookups, issue #66) by a shared secret in the
 * {@code X-Internal-Token} header — zero trust: the route keeps its
 * {@code authenticated()} rule rather than opening to the network
 * (D31), and the token is honored only on {@code GET /users/*}, so it
 * can never reach an admin or self-service route. An empty configured
 * secret disables the filter entirely (no header ever matches).
 *
 * <p>The principal is {@code internal-service} with
 * {@code ROLE_SERVICE} — a name {@code CallerId} cannot parse, so even
 * {@code GET /users/me} (which the path pattern also matches) answers
 * 401 rather than leaking another user's profile.
 */
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private static final RequestMatcher PUBLIC_PROFILE =
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/users/*");

    private final byte[] secret;
    private final SecurityContextHolderStrategy contextHolder =
        SecurityContextHolder.getContextHolderStrategy();

    public InternalServiceTokenFilter(String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return secret.length == 0 || !PUBLIC_PROFILE.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        // wrong or missing token: carry on unauthenticated — the caller
        // may still hold a user JWT, and otherwise the rules answer 401
        if (presented != null
                && MessageDigest.isEqual(secret, presented.getBytes(StandardCharsets.UTF_8))) {
            UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                    "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
            SecurityContext context = contextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            contextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
