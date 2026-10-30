/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: the two routes from the issue; caller identity from
 * Authentication#getName (credit-service precedent), 201 with the full
 * body and no Location (no per-rating GET until #253; the issue
 * author's decision).
 * Author review: [pending]
 */
package foc.rating.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import foc.rating.dto.MyRatingResponse;
import foc.rating.dto.RatingResponse;
import foc.rating.dto.SubmitRatingRequest;
import foc.rating.service.RatingService;

/**
 * Submitting a rating and reading one's own (N1.1, N1.2). The JWT
 * subject is the rater; eligibility and the 404/403/409 refusals are
 * {@link RatingService}'s.
 */
@RestController
@RequestMapping("/ratings")
class RatingController {

    private final RatingService ratingService;

    RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RatingResponse create(Authentication caller, @Valid @RequestBody SubmitRatingRequest body) {
        return RatingResponse.of(
                ratingService.submit(caller.getName(), body.requestId(), body.stars(), body.text()));
    }

    // the UI's rate button: null rating = not rated yet, so show it
    @GetMapping("/mine")
    MyRatingResponse mine(Authentication caller, @RequestParam String requestId) {
        return new MyRatingResponse(ratingService.myRating(caller.getName(), requestId)
                .map(RatingResponse::of)
                .orElse(null));
    }
}
