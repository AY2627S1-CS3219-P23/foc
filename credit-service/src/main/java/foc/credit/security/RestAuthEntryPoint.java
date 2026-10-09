/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-09.
 * Scope: copied from supplier-service's RestAuthEntryPoint with the
 * package changed: a missing or invalid bearer token is a 401
 * problem+json body, the error format the web client reads.
 * 2026-10-10, Claude Code (Opus 5.5), PR #166 Copilot review: the 401
 * carries WWW-Authenticate: Bearer, as user-service's does.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "A valid bearer token is required");
        // names the scheme the client should authenticate with (RFC 6750)
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
