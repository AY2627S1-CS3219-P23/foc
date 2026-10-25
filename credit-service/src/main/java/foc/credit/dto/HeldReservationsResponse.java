/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-25, issue #236.
 * Scope: the body of GET /credits/admin/reservations/held, with the
 * fields of docs/credit-service.md "Admin routes". The shape is the
 * team's decision: an object around the list, as on
 * /credits/me/expiries, so fields can be added later.
 * PR #248 review (team decision): truncated.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.dto;

import java.time.Instant;
import java.util.List;

import foc.credit.service.ReservationQueryService.HeldReservations;

/**
 * Reservations still held, oldest first. {@code truncated} is true when
 * more were held than the cap lets {@code items} list.
 */
public record HeldReservationsResponse(List<Item> items, boolean truncated) {

    /** {@code heldSince} is when the reservation was made. */
    public record Item(String requestId, String requesterId, int amount, Instant heldSince) {
    }

    public static HeldReservationsResponse of(HeldReservations held) {
        return new HeldReservationsResponse(held.reservations().stream()
            .map(r -> new Item(r.getRequestRef(), r.getRequesterId(), r.getAmount(), r.getCreatedAt()))
            .toList(), held.truncated());
    }
}
