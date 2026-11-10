/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-04, Order F8.1.
 * Scope: the port accept uses to read pickup coordinates from
 * supplier-service for the courier proximity check (author's decision:
 * through supplier-service's existing public GET /suppliers).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

import java.util.Map;
import java.util.Set;

interface SupplierLocations {

    // The coordinates of the given suppliers. An id supplier-service does
    // not list, or lists without coordinates, is absent from the map.
    // Throws SupplierLookupException when supplier-service can't be asked.
    Map<Long, Coordinates> of(Set<Long> supplierIds);

    record Coordinates(double latitude, double longitude) {
    }
}
