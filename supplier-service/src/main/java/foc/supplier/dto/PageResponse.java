/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-26.
 * Scope: minimal pagination envelope for issue #133, used instead of
 * serializing Spring Data's Page directly (which emits a verbose,
 * Pageable-shaped body) — author decision to keep the wire format
 * small and stable for the frontend to consume.
 * Reviewed by: [pending]
 */
package foc.supplier.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public PageResponse(List<T> content, int page, int size, long totalElements, int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public static <T> PageResponse<T> of(Page<?> source, List<T> content) {
        return new PageResponse<>(content, source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages());
    }

    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
