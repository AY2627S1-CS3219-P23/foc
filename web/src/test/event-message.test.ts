// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5.1), 2026-11-04, PR #287 review (@Sinnez1).
// Scope: pins which rows cost a username lookup — only templates whose
// copy names the actor (accepted, collected, dropped, rating) — so an
// expired row's new courierId (notification D34), like completed's or a
// dispute's, hydrates nothing. Author review: Leong Wei Zhi, pending
// pull request review.

import { describe, expect, it } from 'vitest'

import { actorIdOf } from '@/features/notifications/eventMessage'

const request = {
  requestId: 'req-1',
  requesterId: 'usr-req-1001',
  courierId: 'usr-cou-2002',
}

describe('actorIdOf', () => {
  it('names the courier on the templates that show them', () => {
    for (const type of [
      'request.accepted',
      'request.collected',
      'request.dropped',
    ]) {
      expect(actorIdOf(type, request)).toBe('usr-cou-2002')
    }
  })

  it('names the rater on a rating', () => {
    expect(
      actorIdOf('rating.submitted', {
        raterId: 'usr-req-1001',
        rateeId: 'usr-cou-2002',
      }),
    ).toBe('usr-req-1001')
  })

  it('names nobody when the template never shows the actor', () => {
    for (const type of [
      'request.expired',
      'request.completed',
      'request.courier-arrived',
      'request.disputed',
      'request.dispute-resolved',
      'request.created',
    ]) {
      expect(actorIdOf(type, request)).toBeNull()
    }
  })

  it('names nobody for an unknown type', () => {
    expect(actorIdOf('request.unmapped', request)).toBeNull()
  })
})
