/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-04, Order F8.1.
 * Scope: the proximity check could not be made (supplier-service
 * unreachable, or a pickup without coordinates). Answered 503: accept
 * fails closed (author's decision).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

public class SupplierLookupException extends RuntimeException {

    public SupplierLookupException(String message) {
        super(message);
    }

    public SupplierLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
