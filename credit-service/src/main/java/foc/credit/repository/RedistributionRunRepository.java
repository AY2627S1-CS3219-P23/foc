/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: empty Spring Data repository for the scaffold; queries come
 * with the features that need them.
 * 2026-10-16, Claude Code (Opus 5.5), issue #173: the run row inserted,
 * never merged, so a second run for one month fails on the primary key.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import foc.credit.entity.RedistributionRun;

public interface RedistributionRunRepository extends JpaRepository<RedistributionRun, String> {

    /**
     * Records a month's run, never updates one: a second insert for the
     * same period fails on the primary key, so only one run per month
     * commits (F6.1.1). Use this, not {@code save}, which merges.
     */
    @Modifying
    @Query(value = """
        insert into redistribution_run (period, distributed, remainder, ran_at)
        values (:period, :distributed, :remainder, :ranAt)
        """, nativeQuery = true)
    void insert(@Param("period") String period, @Param("distributed") int distributed,
            @Param("remainder") int remainder, @Param("ranAt") Instant ranAt);
}
