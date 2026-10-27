/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8), thrown when a user already holds the maximum
 * number of bookmarks (D10); BookmarkController maps it to 409 with the
 * limit in the message.
 * Reviewed by: [pending]
 */
package foc.supplier.exception;

public class BookmarkLimitException extends RuntimeException {

    public BookmarkLimitException(int limit) {
        super("You have reached the limit of " + limit + " bookmarks.");
    }
}
