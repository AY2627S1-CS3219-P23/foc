/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: empty Spring Data repository for the scaffold; queries come
 * with the features that need them.
 * 2026-10-11, Claude Code (Opus 5.5), issue #169: a user's lots in the
 * order a reservation spends them (D4); then drained lots left out,
 * now that the schema keeps them.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import foc.credit.entity.CreditLot;

public interface CreditLotRepository extends JpaRepository<CreditLot, Long> {

    /**
     * The owner's lots that still hold credits, soonest expiry first and
     * the never-expiring sign-up lot last (D4). Not locked: lots only
     * change under their owner's account lock.
     */
    @Query("select l from CreditLot l where l.ownerId = :ownerId and l.remaining > 0 order by l.expiresAt asc nulls last, l.id asc")
    List<CreditLot> findInSpendOrder(@Param("ownerId") String ownerId);
}
