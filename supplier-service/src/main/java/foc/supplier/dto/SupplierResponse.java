/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-26.
 * Scope: response DTO for the supplier list endpoint (issue #133),
 * shaped to match web/src/features/supplier/types.ts's existing
 * `Supplier` interface (already agreed via the frontend's own
 * F1.1.1-grounded design) rather than exposing the JPA entity
 * directly: `location` combines `building`/`locationDescription`,
 * `openingTime`/`closingTime` are formatted "HH:mm" strings, `id` is
 * stringified to match the frontend's `id: string`.
 * Reviewed by: [pending]
 */
package foc.supplier.dto;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SupplierResponse {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final String id;
    private final String name;
    private final String location;
    private final Double latitude;
    private final Double longitude;
    private final List<String> categories;
    private final String openingTime;
    private final String closingTime;
    private final String description;
    private final String imageUrl;

    public SupplierResponse(Long id, String name, String building, String locationDescription,
            Double latitude, Double longitude, List<String> categories,
            LocalTime startingTime, LocalTime closingTime, String description, String imageUrl) {
        this.id = String.valueOf(id);
        this.name = name;
        this.location = combineLocation(building, locationDescription);
        this.latitude = latitude;
        this.longitude = longitude;
        this.categories = categories;
        this.openingTime = startingTime == null ? null : startingTime.format(TIME_FORMAT);
        this.closingTime = closingTime == null ? null : closingTime.format(TIME_FORMAT);
        this.description = description;
        this.imageUrl = imageUrl;
    }

    private static String combineLocation(String building, String locationDescription) {
        if (building == null || building.isBlank()) {
            return locationDescription;
        }
        if (locationDescription == null || locationDescription.isBlank()) {
            return building;
        }
        return building + ", " + locationDescription;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public List<String> getCategories() {
        return categories;
    }

    public String getOpeningTime() {
        return openingTime;
    }

    public String getClosingTime() {
        return closingTime;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
