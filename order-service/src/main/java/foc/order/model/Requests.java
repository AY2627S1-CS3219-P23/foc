/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-11.
 * Scope: JPA entity for the "requests" table, following the
 * supplier-service Suppliers pattern. Columns, types and nullability
 * transcribed from the author's ER diagram (bigint -> Long, text ->
 * String, int -> Integer, timestamptz -> Instant, version -> @Version);
 * no columns added or changed by the tool.
 * 2026-10-16: createdAt defaults to Instant.now() (PR review fix: a
 * save without setCreatedAt failed the not-null check).
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "requests")
public class Requests {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // JWT sub of the creator
    @Column(nullable = false)
    private Long requesterId;

    // Null until accepted
    private Long courierId;

    // Pickup: Supplier service id (F1.1.1)
    @Column(nullable = false)
    private Long supplierId;

    // Snapshot at creation
    @Column(columnDefinition = "text")
    private String supplierName;

    // Free text (F1.1)
    @Column(nullable = false, columnDefinition = "text")
    private String dropoffLocation;

    // What to fetch (F1.1)
    @Column(nullable = false, columnDefinition = "text")
    private String item;

    // Integer >= 1 (F1.1.3)
    @Column(nullable = false)
    private Integer creditAmount;

    // CREATED/ACCEPTED/COLLECTED/COMPLETED/CANCELLED/EXPIRED (F0.1)
    @Column(nullable = false, columnDefinition = "text")
    private String status;

    // Must be in the future at creation (F1.1.2)
    @Column(nullable = false)
    private Instant deadline;

    private Instant acceptedAt;

    private Instant collectedAt;

    // Starts the 1 h auto-complete clock (F7.2)
    private Instant arrivedAt;

    // Defaults to construction time, so a save that never sets it still
    // passes the not-null check (as in user-service's User)
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // Optimistic lock
    @Version
    @Column(nullable = false)
    private Long version;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public Long getCourierId() {
        return courierId;
    }

    public void setCourierId(Long courierId) {
        this.courierId = courierId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getDropoffLocation() {
        return dropoffLocation;
    }

    public void setDropoffLocation(String dropoffLocation) {
        this.dropoffLocation = dropoffLocation;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public Integer getCreditAmount() {
        return creditAmount;
    }

    public void setCreditAmount(Integer creditAmount) {
        this.creditAmount = creditAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public void setDeadline(Instant deadline) {
        this.deadline = deadline;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Instant collectedAt) {
        this.collectedAt = collectedAt;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(Instant arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

}
