/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13, issue #172.
 * Scope: the read-only REST API of docs/credit-service.md "Read API
 * contract": the two routes, their parameters and the checks behind the
 * contract's 400s. The routes, shapes, bounds and errors are the team's
 * design; this transcribes them.
 * 2026-10-17, Claude Code (Opus 5.5), issue #196: GET /credits/me/expiries,
 * the route and its shape as the team decided.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.controller;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import foc.credit.dto.BalanceResponse;
import foc.credit.dto.ExpiriesResponse;
import foc.credit.dto.HistoryEntryResponse;
import foc.credit.dto.PageResponse;
import foc.credit.service.CreditQueryService;
import foc.credit.service.HistoryFilter;

/**
 * The caller's own balance and history (Credit F4.1, F4.1.2, F4.1.3).
 * The caller is the token's user, set by {@code JwtAuthenticationFilter};
 * no route takes a user ID, so there is no way to another user's data.
 */
@RestController
@RequestMapping("/credits/me")
public class CreditController {

    static final int MAX_PAGE_SIZE = 100;

    private final CreditQueryService credits;

    public CreditController(CreditQueryService credits) {
        this.credits = credits;
    }

    @GetMapping
    public BalanceResponse balance(Authentication caller) {
        return BalanceResponse.of(credits.balance(caller.getName()));
    }

    // page and size are plain integers checked here, not Spring's
    // Pageable: that would turn a negative page into 0, cap an oversized
    // size silently and admit a sort parameter. from and to bind as
    // OffsetDateTime, so an instant needs its offset and a date alone
    // does not parse.
    @GetMapping("/history")
    public PageResponse<HistoryEntryResponse> history(
            Authentication caller,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer minAmount,
            @RequestParam(required = false) Integer maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        if (page < 0) {
            throw new InvalidQueryParameterException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidQueryParameterException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        requireAtLeastOne("minAmount", minAmount);
        requireAtLeastOne("maxAmount", maxAmount);
        if (minAmount != null && maxAmount != null && minAmount > maxAmount) {
            throw new InvalidQueryParameterException("minAmount must not be above maxAmount");
        }
        Instant fromInstant = from == null ? null : from.toInstant();
        Instant toInstant = to == null ? null : to.toInstant();
        // to is exclusive, so an equal from and to would be an empty range
        if (fromInstant != null && toInstant != null && !fromInstant.isBefore(toInstant)) {
            throw new InvalidQueryParameterException("from must be before to");
        }

        HistoryFilter filter = new HistoryFilter(minAmount, maxAmount, fromInstant, toInstant);
        return PageResponse.of(credits.history(caller.getName(), filter, page, size), HistoryEntryResponse::of);
    }

    // no parameters, so no 400s: fetched when the credits page opens and
    // when the balance changes, not on the 30 s balance poll
    @GetMapping("/expiries")
    public ExpiriesResponse expiries(Authentication caller) {
        return ExpiriesResponse.of(credits.upcomingExpiries(caller.getName()));
    }

    // history amounts are always positive, so a bound below 1 is a mistake
    private static void requireAtLeastOne(String name, Integer amount) {
        if (amount != null && amount < 1) {
            throw new InvalidQueryParameterException(name + " must be 1 or more");
        }
    }
}
