/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-11-08
Scope: issue #308: the owner setup token check moved here unchanged from
       OwnerSignupService.checkOwnerKey, so the owner sign-up and the
       profile upgrade (POST /users/me/owner) compare the token the same
       way. Endpoint decisions by Leong Wei Zhi via options Q&A
       (2026-11-08); no design decision was made by AI.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import foc.user.controller.ProblemTypes;

@Component
public class OwnerSetupToken {

    private final String expectedOwnerKey;

    @Autowired
    public OwnerSetupToken(@Value("${owner.setup.token:}") String expectedOwnerKey) {
        this.expectedOwnerKey = expectedOwnerKey;
    }

    // constant-time compare; 503 when no token is configured, which every
    // caller answers the same way, whatever was provided
    public boolean matches(String providedOwnerKey) {
        if (expectedOwnerKey == null || expectedOwnerKey.isBlank()) {
            ResponseStatusException e =  new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Owner setup is unavailable"
            );
            e.getBody().setType(ProblemTypes.OWNER_SETUP_TOKEN_MISSING);
            throw e;
        }

        byte[] expected = expectedOwnerKey.getBytes(StandardCharsets.UTF_8);
        byte[] provided = (providedOwnerKey == null ? "" : providedOwnerKey).getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expected, provided);
    }
}
