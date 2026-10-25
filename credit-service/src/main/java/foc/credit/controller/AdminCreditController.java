/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-17, issue #174 (admin dashboard
 * pool box).
 * Scope: the admin routes the team chose: the pool summary and the paged
 * redistribution runs, ADMIN and OWNER only (SecurityConfig). Paging is
 * checked as the history route checks it.
 * 2026-10-25, Claude Code (Opus 5.5), issue #236: the long-held
 * reservations route, with olderThan an ISO-8601 duration that is not
 * negative (team decision).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.controller;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import foc.credit.dto.HeldReservationsResponse;
import foc.credit.dto.PageResponse;
import foc.credit.dto.PoolSummaryResponse;
import foc.credit.dto.RedistributionRunResponse;
import foc.credit.service.PoolQueryService;
import foc.credit.service.ReservationQueryService;

/**
 * The common pool and its redistributions (F6.1), and the reservations
 * still held (DSP-11), for the admin dashboard.
 */
@RestController
@RequestMapping("/credits/admin")
public class AdminCreditController {

    private final PoolQueryService pool;
    private final ReservationQueryService reservations;

    public AdminCreditController(PoolQueryService pool, ReservationQueryService reservations) {
        this.pool = pool;
        this.reservations = reservations;
    }

    @GetMapping("/pool")
    public PoolSummaryResponse pool() {
        return PoolSummaryResponse.of(pool.summary(Instant.now()));
    }

    // plain integers, as on /credits/me/history: Pageable would hide the 400s
    @GetMapping("/redistributions")
    public PageResponse<RedistributionRunResponse> redistributions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new InvalidQueryParameterException("page must be 0 or more");
        }
        if (size < 1 || size > CreditController.MAX_PAGE_SIZE) {
            throw new InvalidQueryParameterException("size must be between 1 and " + CreditController.MAX_PAGE_SIZE);
        }
        return PageResponse.of(pool.runs(page, size), RedistributionRunResponse::of);
    }

    // a string, parsed here, so a bad duration is the contract's 400
    // rather than a conversion failure; PT0S lists every held reservation
    @GetMapping("/reservations/held")
    public HeldReservationsResponse heldReservations(@RequestParam(defaultValue = "PT1H") String olderThan) {
        Duration duration;
        try {
            duration = Duration.parse(olderThan);
        } catch (DateTimeParseException e) {
            throw new InvalidQueryParameterException("olderThan must be an ISO-8601 duration such as PT1H");
        }
        if (duration.isNegative()) {
            throw new InvalidQueryParameterException("olderThan must not be negative");
        }
        return HeldReservationsResponse.of(reservations.heldLongerThan(duration, Instant.now()));
    }
}
