/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #253.
 * Scope: the two profile routes from the issue; page/size checked here,
 * not via Pageable (credit-service's CreditController precedent, bounds
 * and messages included). Any authenticated user may read — the issue's
 * scope, so no Authentication parameter.
 * Author review: [pending]
 */
package foc.rating.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import foc.rating.dto.PageResponse;
import foc.rating.dto.RatingSummaryResponse;
import foc.rating.dto.ReviewResponse;
import foc.rating.service.RatingService;

/**
 * Another user's ratings for the public profile (N1.3): the star-row
 * aggregate and the newest-first review history. Any authenticated user
 * may read; the ratee is the path id, so there is no caller identity in
 * play. No class-level prefix — these live under /users, not /ratings.
 */
@RestController
class UserRatingsController {

    static final int MAX_PAGE_SIZE = 100;

    private final RatingService ratingService;

    UserRatingsController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    // the profile's "4.2 (18 reviews)" row; average is null (not 0)
    // until the first rating, and the UI rounds for display
    @GetMapping("/users/{id}/ratings/summary")
    RatingSummaryResponse summary(@PathVariable String id) {
        return ratingService.summaryOf(id);
    }

    // page and size are plain integers checked here, not Spring's
    // Pageable: that would turn a negative page into 0, cap an oversized
    // size silently and admit a sort parameter (credit-service precedent)
    @GetMapping("/users/{id}/ratings")
    PageResponse<ReviewResponse> reviews(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new InvalidQueryParameterException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidQueryParameterException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageResponse.of(ratingService.reviewsOf(id, page, size), ReviewResponse::of);
    }
}
