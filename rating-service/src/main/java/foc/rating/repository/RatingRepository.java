/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: the rating repository; the one derived query serves both the
 * submit pre-check (409) and GET /ratings/mine.
 * Author review: [pending]
 */
package foc.rating.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import foc.rating.entity.Rating;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    // one rating per (request, rater): the submit pre-check and the
    // caller's own rating for the UI's rate button
    Optional<Rating> findByRequestIdAndRaterId(String requestId, String raterId);
}
