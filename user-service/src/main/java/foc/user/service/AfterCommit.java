/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5), date: 2026-10-17
Scope: PR #183 review (@Sinnez1): the reviewer showed that the per-user
       revocation stamp was written inside the caller's @Transactional,
       so it landed before the role change or removal committed and a
       login in that gap could still read the old role. This defers the
       write to after the commit, following the same sketch
       credit-service's CreditOperationsService.afterCommit already uses
       (the team's PR #175 review): register a synchronisation when one
       is active, run inline when there is no transaction. The mechanism
       (this over a @TransactionalEventListener event) was chosen by
       Leong Wei Zhi via neutral options Q&A; no design decision was
       made by AI.
Author review: Leong Wei Zhi to review via PR #183.
*/

package foc.user.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs best-effort side work once the surrounding transaction has
 * committed — or immediately when there is none, so a service called
 * outside a transaction (unit tests, or any non-transactional caller)
 * still does the work.
 *
 * <p>Only for work that must not be observable before the data it
 * describes: nothing here runs if the transaction rolls back, and a
 * failure cannot roll it back either.
 */
final class AfterCommit {

    private AfterCommit() {
    }

    // AI-generated (edited by Claude Code; Leong Wei Zhi review pending):
    // the afterCommit sketch credit-service already carries, from the
    // team's PR #175 review
    static void run(Runnable work) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    work.run();
                }
            });
        } else {
            work.run();
        }
    }
}
