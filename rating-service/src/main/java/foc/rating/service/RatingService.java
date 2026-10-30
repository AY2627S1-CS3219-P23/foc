/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: the submit flow the issue specifies (404 unknown request, 403
 * not a party, 409 already rated, rating + outbox row in one
 * transaction, order-service's RequestsService.create precedent) and
 * the /mine read behind the same eligibility gate. Blank write-ups
 * stored as null is the author's decision.
 * Author review: [pending]
 */
package foc.rating.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import foc.rating.entity.RateableRequest;
import foc.rating.entity.Rating;
import foc.rating.repository.RateableRequestRepository;
import foc.rating.repository.RatingRepository;

/**
 * Submitting and reading one's own rating (N1.1, N1.2). Eligibility
 * comes from the {@code rateable_request} rows the #251 ingest writes:
 * only a party of a completed request may rate, the ratee is the other
 * party, and each party rates at most once (the database's unique key
 * backs the pre-check against a concurrent duplicate).
 */
@Service
public class RatingService {

    private final RateableRequestRepository rateableRequests;
    private final RatingRepository ratings;
    private final EventOutbox outbox;

    public RatingService(RateableRequestRepository rateableRequests, RatingRepository ratings,
            EventOutbox outbox) {
        this.rateableRequests = rateableRequests;
        this.ratings = ratings;
        this.outbox = outbox;
    }

    /**
     * Stores the caller's rating of the other party and enqueues
     * {@code rating.submitted} in the same transaction: the event exists
     * exactly when the rating does.
     */
    @Transactional
    public Rating submit(String raterId, String requestId, int stars, String text) {
        String rateeId = rateeOf(requestId, raterId);
        // the friendly 409; the unique key answers a concurrent duplicate
        if (ratings.findByRequestIdAndRaterId(requestId, raterId).isPresent()) {
            throw new AlreadyRatedException();
        }
        Rating rating = new Rating(requestId, raterId, rateeId, (short) stars,
                normalized(text), Instant.now());
        ratings.save(rating);
        outbox.enqueue(RatingEvents.submitted(rating));
        return rating;
    }

    /** The caller's own rating of this request, if any (the UI's rate button). */
    @Transactional(readOnly = true)
    public Optional<Rating> myRating(String callerId, String requestId) {
        rateeOf(requestId, callerId); // the same 404/403 gate as submit
        return ratings.findByRequestIdAndRaterId(requestId, callerId);
    }

    // the eligibility gate: 404 for a request not completed here, 403 for
    // a caller who is neither party; else the other party
    private String rateeOf(String requestId, String callerId) {
        RateableRequest request = rateableRequests.findById(requestId)
                .orElseThrow(UnknownRequestException::new);
        if (callerId.equals(request.getRequesterId())) {
            return request.getCourierId();
        }
        if (callerId.equals(request.getCourierId())) {
            return request.getRequesterId();
        }
        throw new NotAPartyException();
    }

    // a write-up of only whitespace is no write-up; the column stays null
    private static String normalized(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
