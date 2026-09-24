/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-23; revised 2026-09-24.
 * Scope: added @CsvBindByName to every CSV-seeded field. Once any one
 * field carries the annotation, opencsv's HeaderColumnNameMappingStrategy
 * switches from auto-matching every field by name to binding only
 * annotated fields — an initial partial annotation (locationDescription
 * only) left all other fields, including the NOT NULL "name" column,
 * unbound and caused seeding to fail; all fields are now annotated
 * explicitly (author's existing field names and CSV headers kept as-is;
 * no schema/design change).
 * 2026-09-24: startingTime/closingTime changed from String to
 * java.time.LocalTime (Postgres TIME) per author decision — the CSV
 * values (e.g. "0900hrs") are a time-of-day with no date component.
 * Parsing handled via opencsv's @CsvDate with the custom pattern
 * "HHmm'hrs'" (opencsv supports java.time temporal types for @CsvDate,
 * not just java.util.Date).
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.supplier.model;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import jakarta.persistence.*;

import java.time.LocalTime;

@Entity
@Table(name = "Suppliers")
public class Suppliers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @CsvBindByName(column = "Name")
    private String name;

    @CsvBindByName(column = "Type")
    private String type;

    @CsvBindByName(column = "Building")
    private String building;

    @CsvBindByName(column = "Floor")
    private String floor;

    @CsvBindByName(column = "Location Description")
    private String locationDescription;

    @CsvBindByName(column = "Latitude")
    private Double latitude;

    @CsvBindByName(column = "Longitude")
    private Double longitude;

    @CsvBindByName(column = "StartingTime")
    @CsvDate("HHmm'hrs'")
    private LocalTime startingTime;

    @CsvBindByName(column = "ClosingTime")
    @CsvDate("HHmm'hrs'")
    private LocalTime closingTime;

    @Column(nullable = true)
    @CsvBindByName(column = "ImageURL")
    private String imageURL;

    private String supplierDescription;

    private String status;

    // Getters and Setters

    private Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public String getLocationDescription() {
        return locationDescription;
    }

    public void setLocationDescription(String locationDescription) {
        this.locationDescription = locationDescription;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public LocalTime getStartingTime() {
        return startingTime;
    }

    public void setStartingTime(LocalTime startingTime) {
        this.startingTime = startingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public void setClosingTime(LocalTime closingTime) {
        this.closingTime = closingTime;
    }

    public String getImageURL() {
        return imageURL;
    }

    public void setImageURL(String imageURL) {
        this.imageURL = imageURL;
    }

    public String getSupplierDescription() {
        return supplierDescription;
    }

    public void setSupplierDescription(String supplierDescription) {
        this.supplierDescription = supplierDescription;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
