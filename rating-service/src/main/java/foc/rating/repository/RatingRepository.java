/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: the rating repository; the one derived query serves both the
 * submit pre-check (409) and GET /ratings/mine.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), issue #253: the profile reads —
 * the ratee's page and the aggregate (avg null for no rows, the
 * author's decision via options Q&A).
 * Author review: [pending]
 */
package foc.rating.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import foc.rating.dto.RatingSummaryResponse;
import foc.rating.entity.Rating;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    // one rating per (request, rater): the submit pre-check and the
    // caller's own rating for the UI's rate button
    Optional<Rating> findByRequestIdAndRaterId(String requestId, String raterId);

    // a user's review history for the public profile (#253)
    Page<Rating> findByRateeId(String rateeId, Pageable page);

    // the profile's star row; avg is null when the user has no ratings,
    // and the API passes that through rather than faking a 0
    @Query("select new foc.rating.dto.RatingSummaryResponse(avg(r.stars), count(r)) "
            + "from Rating r where r.rateeId = :rateeId")
    RatingSummaryResponse summarize(@Param("rateeId") String rateeId);
}
