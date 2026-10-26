/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8.5, D12), the internal endpoint user-service's
 * account-purge job calls to remove the bookmarks of the accounts it is
 * about to hard-delete. Not a user endpoint: SecurityConfig requires the
 * SERVICE role, which only a token minted by a service holding JWT_SECRET
 * carries (login never issues it), and the gateway answers 404 for the
 * path from outside. Idempotent — ids with no bookmarks, or repeated
 * calls, just delete nothing — so a purge retried after a failure is safe.
 * Batch limit chosen by the tool (user-service sends 500 at a time).
 * Reviewed by: [pending]
 */
package foc.supplier.controller;

import foc.supplier.dto.PurgeBookmarksRequest;
import foc.supplier.dto.PurgeBookmarksResponse;
import foc.supplier.service.BookmarkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PurgeController {

    private final BookmarkService bookmarkService;

    public PurgeController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @PostMapping("/purge/bookmarks")
    public PurgeBookmarksResponse purgeBookmarks(@Valid @RequestBody PurgeBookmarksRequest request) {
        return new PurgeBookmarksResponse(bookmarkService.purgeUsers(request.userIds()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ProblemDetail handleBadRequest() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid purge request — send {\"userIds\":[...]} with 0 to "
                        + PurgeBookmarksRequest.MAX_USER_IDS + " ids");
    }
}
