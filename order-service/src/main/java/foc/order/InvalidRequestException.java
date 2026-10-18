/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: thrown by RequestsService.create when the new request has
 * missing fields or invalid values; RequestsController answers it with
 * 400 and this message (the author's decision).
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

/** A new request that cannot be stored as given; the message says why. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
