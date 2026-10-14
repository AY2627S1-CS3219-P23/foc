/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13, issue #172.
 * Scope: the page envelope of supplier-service's PageResponse, with the
 * same five fields, as docs/credit-service.md "Read API contract" asks.
 * A copy, not an import: services share no code but foc-contracts.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** One page of a list, in the shape the web app already reads from supplier-service. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> of(Page<S> source, Function<S, T> toResponse) {
        return new PageResponse<>(source.getContent().stream().map(toResponse).toList(), source.getNumber(),
            source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
