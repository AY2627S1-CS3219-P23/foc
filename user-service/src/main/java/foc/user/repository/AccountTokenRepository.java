/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-27
Scope: repository created for issue #93's day-31 purge — the only query so
       far removes reset/recovery tokens owned by purged accounts (bulk
       deletes in the purge transaction chosen by Leong Wei Zhi via options
       Q&A, over DB-level ON DELETE CASCADE). The recovery and password-reset
       flows that read these rows arrive with #94.
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import foc.user.entity.AccountToken;

@Repository
public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    // removes tokens owned by accounts whose recovery window has passed, so
    // the purge's users delete doesn't trip the user_id FK (bulk delete
    // statements can't join, hence the subquery)
    @Modifying(clearAutomatically = true)
    @Query("delete from AccountToken t where t.user in (select u from User u where u.deletedAt < :cutoff)")
    int deleteByUserDeletedBefore(@Param("cutoff") Instant cutoff);
}
