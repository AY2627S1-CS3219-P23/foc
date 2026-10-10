/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: placeholder bean for CreditOperations so callers can be wired
 * and built before the real implementation exists; every method throws.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import org.springframework.stereotype.Service;

import foc.credit.entity.CreditAccount;

// Scaffold stub: replace with the real implementation, don't add to it
@Service
public class NotImplementedCreditOperations implements CreditOperations {

    @Override
    public CreditAccount getOrCreate(String userId) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ReserveResult reserve(String requestRef, String requesterId, int amount) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public SettleResult transfer(String requestRef, String courierId) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public SettleResult release(String requestRef, String requesterId) {
        throw new UnsupportedOperationException("not implemented");
    }
}
