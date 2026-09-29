/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: problem+json 401/403 bodies for the Spring Security filter chain
       (issue #91), per design doc §3 ("violations → problem+json 401/403")
       and the body shape in its supplier-service example (type, title,
       status, detail, instance). These responses are written by the filter
       chain before any controller runs, so ProblemDetailAdvice can't
       render them.
Author review: Ryan to review via the PR.
*/

package foc.user.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityProblemResponses implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String AUTHENTICATION_REQUIRED = "Authentication required";
    static final String TOKEN_EXPIRED = "Token has expired";
    static final String TOKEN_INVALID = "Invalid token";
    static final String FORBIDDEN = "You do not have permission to access this resource";

    private final JsonMapper jsonMapper;

    public SecurityProblemResponses(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    // no usable credentials on a route that needs them
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, AUTHENTICATION_REQUIRED);
    }

    // signed in, but the role doesn't reach the route
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, FORBIDDEN);
    }

    // a bearer token was sent but failed verification (RFC 6750 invalid_token)
    void rejectToken(HttpServletRequest request, HttpServletResponse response, String detail)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
        write(request, response, HttpStatus.UNAUTHORIZED, detail);
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String detail) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        // the same members Spring MVC writes for a ProblemDetail
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(jsonMapper.writeValueAsBytes(body));
    }
}
