/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8.1, N8.2), the caller's bookmark endpoints. The
 * user is the verified JWT subject and is never taken from the request,
 * so one user cannot read or change another's bookmarks. Errors are
 * RFC 9457 problem+json like the rest of the service.
 * Reviewed by: [pending]
 */
package foc.supplier.controller;

import foc.supplier.dto.BookmarkListResponse;
import foc.supplier.dto.BookmarkResponse;
import foc.supplier.exception.BookmarkLimitException;
import foc.supplier.exception.BookmarkTooFastException;
import foc.supplier.exception.SupplierNotFoundException;
import foc.supplier.service.BookmarkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @GetMapping("/bookmarks")
    public BookmarkListResponse list(Authentication authentication) {
        return bookmarkService.list(userId(authentication));
    }

    // any authenticated user (SecurityConfig), unlike the admin-only
    // writes on /suppliers/{id}
    @PutMapping("/suppliers/{id}/bookmark")
    public BookmarkResponse add(@PathVariable Long id, Authentication authentication) {
        return bookmarkService.add(userId(authentication), id);
    }

    @DeleteMapping("/suppliers/{id}/bookmark")
    public ResponseEntity<Void> remove(@PathVariable Long id, Authentication authentication) {
        bookmarkService.remove(userId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private static Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    public ProblemDetail handleSupplierNotFound(SupplierNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BookmarkLimitException.class)
    public ProblemDetail handleLimit(BookmarkLimitException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Bookmark limit reached");
        return problem;
    }

    @ExceptionHandler(BookmarkTooFastException.class)
    public ProblemDetail handleTooFast(BookmarkTooFastException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Bookmarking too fast");
        return problem;
    }

    // a non-numeric subject cannot be one of user-service's ids
    @ExceptionHandler(NumberFormatException.class)
    public ProblemDetail handleBadSubject() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid token subject");
    }
}
