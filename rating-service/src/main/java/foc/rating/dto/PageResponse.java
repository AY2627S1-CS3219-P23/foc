/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #253.
 * Scope: copied from order-service's PageResponse (the platform's paged
 * list shape, as in the supplier listing) for the review history; only
 * the package changed. Copied, not shared, by the author's decision.
 * Author review: [pending]
 */
package foc.rating.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> of(Page<S> source, Function<S, T> toResponse) {
        return new PageResponse<>(source.getContent().stream().map(toResponse).toList(), source.getNumber(),
            source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
