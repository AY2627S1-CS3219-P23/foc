/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: copied from credit-service's JwtAuthenticationFilter with the
 * package changed and the authorities emptied: this service's verifier
 * exposes no role claim and no route here is role-gated (author
 * decision via options Q&A, docs/notification-service.md D28). Reads
 * the bearer token, verifies it, and puts the caller's user ID in the
 * security context. A missing or invalid token leaves the context
 * unauthenticated, so SecurityConfig and RestAuthEntryPoint render one
 * consistent 401. Not a @Component: SecurityConfig builds it, so it
 * runs only in the security chain, never as a second servlet-container
 * filter.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
				// no authorities: the verifier carries no role claim and
				// every route is the caller's own data (D28)
				var authentication =
						new UsernamePasswordAuthenticationToken(verified.subject(), null, List.of());
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
