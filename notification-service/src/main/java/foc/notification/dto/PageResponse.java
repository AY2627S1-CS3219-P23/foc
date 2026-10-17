/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: the page envelope of credit-service's PageResponse, with the
 * same five fields, so the web app reads one list shape everywhere.
 * A copy, not an import: services share no code but foc-contracts.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** One page of a list, in the shape the web app already reads from supplier- and credit-service. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> of(Page<T> source) {
		return new PageResponse<>(source.getContent(), source.getNumber(),
				source.getSize(), source.getTotalElements(), source.getTotalPages());
	}
}
