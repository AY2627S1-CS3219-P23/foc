/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: copied from credit-service's PageResponse (the platform's paged
 * list shape, as in the supplier listing) for the read endpoints; only
 * the package changed.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> of(Page<S> source, Function<S, T> toResponse) {
        return new PageResponse<>(source.getContent().stream().map(toResponse).toList(), source.getNumber(),
            source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
