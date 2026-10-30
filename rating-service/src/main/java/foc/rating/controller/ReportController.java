/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #254.
 * Scope: the two routes from the issue; caller identity from
 * Authentication#getName, page/size checked here like
 * UserRatingsController. The GET's ADMIN/OWNER gate lives in
 * SecurityConfig, not here.
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

import foc.rating.dto.PageResponse;
import foc.rating.dto.ReportResponse;
import foc.rating.dto.SubmitReportRequest;
import foc.rating.service.ReportService;

/**
 * Reporting a user and the admin's report list (N1.4). The JWT subject
 * is the reporter; the list's ADMIN/OWNER gate is SecurityConfig's, so
 * a non-admin gets 403 before any parameter is parsed.
 */
@RestController
@RequestMapping("/reports")
class ReportController {

    static final int MAX_PAGE_SIZE = 100;

    private final ReportService reportService;

    ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ReportResponse create(Authentication caller, @Valid @RequestBody SubmitReportRequest body) {
        return ReportResponse.of(reportService.file(caller.getName(), body.reportedUserId(), body.reason(),
                body.details(), body.requestId()));
    }

    // page and size are plain integers checked here, not Spring's
    // Pageable (credit-service precedent, as in UserRatingsController)
    @GetMapping
    PageResponse<ReportResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new InvalidQueryParameterException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidQueryParameterException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageResponse.of(reportService.list(page, size), ReportResponse::of);
    }
}
