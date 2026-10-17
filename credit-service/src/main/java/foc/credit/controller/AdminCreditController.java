/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-17, issue #174 (admin dashboard
 * pool box).
 * Scope: the admin routes the team chose: the pool summary and the paged
 * redistribution runs, ADMIN and OWNER only (SecurityConfig). Paging is
 * checked as the history route checks it.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import foc.credit.dto.PageResponse;
import foc.credit.dto.PoolSummaryResponse;
import foc.credit.dto.RedistributionRunResponse;
import foc.credit.service.PoolQueryService;

/** The common pool and its redistributions, for the admin dashboard (F6.1). */
@RestController
@RequestMapping("/credits/admin")
public class AdminCreditController {

    private final PoolQueryService pool;

    public AdminCreditController(PoolQueryService pool) {
        this.pool = pool;
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
}
