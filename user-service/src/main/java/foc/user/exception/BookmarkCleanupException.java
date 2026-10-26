/*
AI Assistance Disclosure:
Tool: Claude Code (Sonnet 5.5), date: 2026-10-26
Scope: issue #225 (N8.5, D12), thrown by SupplierBookmarkCleanupClient when
       supplier-service could not remove a purged account's bookmarks;
       AccountPurgeScheduler catches it and skips that run's account delete.
Author review: Alastair Tan Choon Wei to review via the PR.
*/

package foc.user.exception;

public class BookmarkCleanupException extends RuntimeException {

    public BookmarkCleanupException(String message, Throwable cause) {
        super(message, cause);
    }
}
