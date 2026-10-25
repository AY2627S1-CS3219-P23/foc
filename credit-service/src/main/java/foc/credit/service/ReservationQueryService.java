/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-25, issue #236.
 * Scope: the admin read of long-held reservations, written to the team's
 * decisions (DSP-5, DSP-11): HELD only, held at or before now minus the
 * given duration, oldest first, at most 100.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import foc.credit.entity.ReservationStatus;
import foc.credit.entity.Reserved;
import foc.credit.repository.ReservedRepository;

/**
 * What an admin may read of the reservations: the ones still held, so the
 * dashboard can check them against the Order Service and flag credits
 * stuck between the two (DSP-11). Nothing here locks or writes.
 */
@Service
public class ReservationQueryService {

    public static final int MAX_HELD = 100;

    // the request ref settles rows of the same instant, so the list is
    // the same on every read
    private static final Sort OLDEST_FIRST = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("requestRef"));

    private final ReservedRepository reservations;

    public ReservationQueryService(ReservedRepository reservations) {
        this.reservations = reservations;
    }

    /** {@code olderThan} is not negative; the controller refuses one that is. */
    @Transactional(readOnly = true)
    public List<Reserved> heldLongerThan(Duration olderThan, Instant now) {
        // no reservation predates the epoch, and a longer duration could
        // take the cutoff past what Instant or PostgreSQL can hold
        Instant cutoff = olderThan.compareTo(Duration.between(Instant.EPOCH, now)) >= 0
            ? Instant.EPOCH
            : now.minus(olderThan);
        return reservations.findByStatusAndCreatedAtLessThanEqual(ReservationStatus.HELD, cutoff,
            PageRequest.of(0, MAX_HELD, OLDEST_FIRST));
    }
}
