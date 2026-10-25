/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: empty Spring Data repository for the scaffold; queries come
 * with the features that need them.
 * 2026-10-11, Claude Code (Opus 5.5), issue #169: a reservation's slices,
 * read back on release (D4).
 * 2026-10-25, Claude Code (Opus 5.5), issue #235: a reservation's slices
 * latest expiry first, for a resolved dispute's refund (team decision,
 * DSP-4).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import foc.credit.entity.CreditReservationSlice;

public interface CreditReservationSliceRepository extends JpaRepository<CreditReservationSlice, Long> {

    List<CreditReservationSlice> findByRequestRef(String requestRef);

    /**
     * A reservation's slices latest expiry first, the never-expiring
     * sign-up credits before any: the reverse of the spend order, so a
     * partial refund gives back the credits that last longest (DSP-4).
     */
    @Query("select s from CreditReservationSlice s where s.requestRef = :requestRef order by s.expiresAt desc nulls first, s.id desc")
    List<CreditReservationSlice> findLatestExpiryFirst(@Param("requestRef") String requestRef);
}
