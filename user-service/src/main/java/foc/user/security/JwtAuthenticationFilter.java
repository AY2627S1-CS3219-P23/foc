/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: bearer-token filter for the Spring Security filter chain (issue #91),
       per design doc §3: validates the JWT and maps its role claim to a
       ROLE_ authority; a bad token is a problem+json 401. Skipping /auth/**
       (except /auth/logout, a bearer route in §3) and /actuator/health so a
       stale token can't block login was the author's decision. Not a Spring
       bean, so the servlet container doesn't also register it outside the
       security chain.
Author review: Ryan to review via the PR.
*/

package foc.user.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_SCHEME = "Bearer ";

    // the public routes never read the Authorization header, so a stale token
    // left in the browser can't block login or sign-up
    private static final RequestMatcher UNFILTERED = new OrRequestMatcher(
        PathPatternRequestMatcher.withDefaults().matcher("/auth/**"),
        PathPatternRequestMatcher.withDefaults().matcher("/actuator/health"));
    // ...except logout, which needs the caller's token (design doc §3). #90
    // still has to add an authenticated() rule for it above the /auth/**
    // permit in SecurityConfig
    private static final RequestMatcher LOGOUT =
        PathPatternRequestMatcher.withDefaults().matcher("/auth/logout");

    private final JwtVerifier verifier;
    private final SecurityProblemResponses problems;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public JwtAuthenticationFilter(JwtVerifier verifier, SecurityProblemResponses problems) {
        this.verifier = verifier;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return UNFILTERED.matches(request) && !LOGOUT.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        // no bearer token: carry on unauthenticated, and the authorization
        // rules answer 401 if the route needs a signed-in caller
        if (header == null || !header.regionMatches(true, 0, BEARER_SCHEME, 0, BEARER_SCHEME.length())) {
            chain.doFilter(request, response);
            return;
        }

        JwtVerifier.VerifiedToken token;
        try {
            token = verifier.verify(header.substring(BEARER_SCHEME.length()).trim());
        } catch (ExpiredJwtException e) {
            reject(request, response, SecurityProblemResponses.TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException: an empty token after "Bearer "
            reject(request, response, SecurityProblemResponses.TOKEN_INVALID);
            return;
        }

        // the principal name is the user id, which CallerId reads
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
            token.subject(), null, List.of(new SimpleGrantedAuthority("ROLE_" + token.role().name())));
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);

        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String detail)
            throws IOException {
        contextHolder.clearContext();
        problems.rejectToken(request, response, detail);
    }
}
