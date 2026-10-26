/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8), thrown when another bookmark request from the
 * same user is still in flight, so the cap cannot be checked safely;
 * nothing is saved. BookmarkController maps it to 409.
 * Reviewed by: [pending]
 */
package foc.supplier.exception;

public class BookmarkTooFastException extends RuntimeException {

    public BookmarkTooFastException() {
        super("You are bookmarking too fast, please try again.");
    }
}
