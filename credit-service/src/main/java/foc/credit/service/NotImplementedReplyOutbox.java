/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: placeholder bean for ReplyOutbox so callers can be wired and
 * built before the real implementation exists; enqueue throws.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import org.springframework.stereotype.Service;

import foc.contracts.events.core.DomainEvent;

// Scaffold stub: replace with the real implementation, don't add to it
@Service
public class NotImplementedReplyOutbox implements ReplyOutbox {

    @Override
    public void enqueue(DomainEvent reply) {
        throw new UnsupportedOperationException("not implemented");
    }
}
