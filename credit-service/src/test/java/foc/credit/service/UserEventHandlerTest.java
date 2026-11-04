/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267.
 * Scope: unit tests for the user event handler: user.created opens and
 * user.purged closes the named account (USR-6), and a blank userId is
 * refused before any operation (NO_USER since the PR review).
 * Author review: Ryan Ang, pending pull request review.
 
 * 2026-11-04, Claude Code (Fable 5.1), notification D34 (author
 * decision via options Q&A): the parties argument/assertions removed —
 * events carry no recipient list; RequestExpired gains a nullable
 * courierId. Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import foc.contracts.events.user.UserCreated;
import foc.contracts.events.user.UserPurged;

class UserEventHandlerTest {

    private static final Instant AT = Instant.parse("2026-10-28T03:00:00Z");

    private final CreditOperations operations = mock(CreditOperations.class);
    private final UserEventHandler handler = new UserEventHandler(operations);

    @Test
    void userCreatedOpensTheAccount() {
        when(operations.open("42")).thenReturn(AccountResult.CREATED);

        assertThat(handler.handle(new UserCreated("e-1", AT, "user-service", "e-1", "42")))
            .isEqualTo(AccountResult.CREATED);
    }

    @Test
    void userPurgedClosesTheAccount() {
        when(operations.close("42")).thenReturn(AccountResult.CLOSED);

        assertThat(handler.handle(new UserPurged("e-2", AT, "user-service", "e-2", "42")))
            .isEqualTo(AccountResult.CLOSED);
    }

    @Test
    void aBlankUserIsNoUserBeforeAnyOperation() {
        assertThat(handler.handle(new UserPurged("e-3", AT, "user-service", " ", " ")))
            .isEqualTo(AccountResult.NO_USER);

        verifyNoInteractions(operations);
    }
}
