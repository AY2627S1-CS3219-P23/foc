// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-11-08, Credit NFR1.1.
// Scope: the pushed request events after which CreditBalanceProvider
// fetches the balance again; the list is a team decision. Its own
// file so the provider's module exports components and plain constants
// only (react-refresh).
// Author review: Ryan Ang, pending pull request review.

// The pushed request events after which someone's balance has changed:
// a reserve (submitted), a transfer (completed), a release (cancelled,
// expired) or a split (dispute-resolved). request.created is not one:
// it is published before the Credit Service reserves anything.
export const BALANCE_EVENTS: ReadonlySet<string> = new Set([
  'request.submitted',
  'request.completed',
  'request.cancelled',
  'request.expired',
  'request.dispute-resolved',
])
