/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: empty Spring Data repository for the scaffold; queries come
 * with the features that need them.
 * 2026-10-16, Claude Code (Opus 5.5), issue #173: the pool's row lock,
 * taken last in the D2 order by the expiry and redistribution runs.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import foc.credit.entity.CommonPool;

public interface CommonPoolRepository extends JpaRepository<CommonPool, Short> {

    // the last lock in the D2 order: after every account the operation touches
    default CommonPool lockPool() {
        return findForUpdate(CommonPool.ID).orElseThrow();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CommonPool p where p.id = :id")
    Optional<CommonPool> findForUpdate(@Param("id") short id);
}
