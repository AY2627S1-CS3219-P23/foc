/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-11-08
Scope: issue #308: the owner setup token check moved here unchanged from
       OwnerSignupService.checkOwnerKey, so the owner sign-up and the
       profile upgrade (POST /users/me/owner) compare the token the same
       way. Endpoint decisions by Leong Wei Zhi via options Q&A
       (2026-11-08); no design decision was made by AI.
Author review: Leong Wei Zhi to review via the PR.
2026-11-09 (Claude Code, Opus 5.5), PR #309 review (@Sinnez1): the field
is final again; tests use the pinned property, or their own context,
instead of changing the shared bean by reflection. requireConfigured lets
the upgrade answer 503 before it spends the gate code. The 503 comes from
OwnerSetupTokenException.
*/

package foc.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import foc.user.exception.OwnerSetupTokenException;

@Component
public class OwnerSetupToken {

    private final String expectedOwnerKey;

    @Autowired
    public OwnerSetupToken(@Value("${owner.setup.token:}") String expectedOwnerKey) {
        this.expectedOwnerKey = expectedOwnerKey;
    }

    // 503 when no token is configured, which every caller answers the
    // same way, whatever was provided
    public void requireConfigured() {
        if (expectedOwnerKey == null || expectedOwnerKey.isBlank()) {
            throw OwnerSetupTokenException.missing();
        }
    }

    // constant-time compare, after requireConfigured
    public boolean matches(String providedOwnerKey) {
        requireConfigured();

        byte[] expected = expectedOwnerKey.getBytes(StandardCharsets.UTF_8);
        byte[] provided = (providedOwnerKey == null ? "" : providedOwnerKey).getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expected, provided);
    }
}
