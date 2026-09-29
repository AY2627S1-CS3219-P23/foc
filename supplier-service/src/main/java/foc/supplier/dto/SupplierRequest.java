/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-29.
 * Scope: request body for the admin create/update endpoints (issue
 * #104), shaped to match web/src/features/supplier/types.ts's
 * SupplierInput exactly (name, location, categories, openingTime,
 * closingTime, description, latitude, longitude, imageUrl) — the
 * frontend form (SupplierFormModal) already sends this shape, so no
 * frontend change is needed. `location` is one free-text field on the
 * form (no separate building/description inputs), stored into
 * Suppliers.locationDescription; openingTime/closingTime are "HH:mm"
 * strings from a native time input, parsed the same way
 * SupplierResponse formats them for output.
 * Required fields mirror the form's own `required` HTML5 attributes
 * (name, location, openingTime, closingTime); categories/description/
 * imageUrl are optional there too.
 * 2026-09-29 (author request): latitude/longitude were previously
 * optional and unvalidated — the form had no inputs for them and
 * silently sent 0/0 on create, so every new supplier broke "Nearest to
 * Me" sorting. Now required, range-validated server-side (defense in
 * depth; the form's own inputs enforce the same range client-side).
 * Reviewed by: [pending]
 */
package foc.supplier.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SupplierRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "location is required") String location,
        List<String> categories,
        @NotBlank(message = "openingTime is required") String openingTime,
        @NotBlank(message = "closingTime is required") String closingTime,
        String description,
        @NotNull(message = "latitude is required")
        @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
        Double latitude,
        @NotNull(message = "longitude is required")
        @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
        Double longitude,
        String imageUrl) {
}
