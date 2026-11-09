// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5.1), 2026-11-04, PR #287 review (@Sinnez1).
// Scope: pins which rows cost a username lookup — only templates whose
// copy names the actor (accepted, collected, dropped, rating) — so an
// expired row's new courierId (notification D34), like completed's or a
// dispute's, hydrates nothing. Author review: Leong Wei Zhi, pending
// pull request review.
// 2026-11-08, Claude Code (Opus 5.5), Credit NFR1.1 (PR #313): the
// credit-change copy per viewer — the requester's and the courier's
// side, a 0 dispute share, and the side-less copy — and that a credit
// row hydrates no name. Author review: Ryan Ang, pending pull request
// review.
import { describe, expect, it } from 'vitest'

import {
  actorIdOf,
  isCreditEvent,
  notificationMessage,
} from '@/features/notifications/eventMessage'
import type { NotificationDto } from '@/features/notifications/types'

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

describe('credit-change notifications', () => {
  const row = (
    eventType: string,
    payload: Record<string, unknown>,
  ): NotificationDto => ({
    id: 1,
    eventType,
    payload: { ...request, requestId: '12', ...payload },
    occurredAt: '2026-11-08T00:00:00Z',
    read: false,
    createdAt: '2026-11-08T00:00:00Z',
  })
  const title = (dto: NotificationDto, viewer: string | null) =>
    notificationMessage(dto, new Map(), viewer).title

  it('tells the requester they paid and the courier they earned', () => {
    const transferred = row('credit.transferred', { amount: 3 })

    expect(title(transferred, 'usr-req-1001')).toBe(
      '−3 Credits paid for Request #12',
    )
    expect(title(transferred, 'usr-cou-2002')).toBe(
      '+3 Credits earned for Request #12',
    )
  })

  it('tells the requester about a refund', () => {
    expect(title(row('credit.released', { amount: 1 }), 'usr-req-1001')).toBe(
      '+1 Credit refunded for Request #12',
    )
  })

  it('gives each side of a dispute its own share, 0 included', () => {
    const settled = row('credit.dispute-settled', {
      courierAmount: 0,
      requesterAmount: 2,
    })

    expect(title(settled, 'usr-req-1001')).toBe(
      '+2 Credits from the dispute on Request #12',
    )
    expect(title(settled, 'usr-cou-2002')).toBe(
      'No credits from the dispute on Request #12',
    )
  })

  it('falls back to side-less copy when the viewer is unknown', () => {
    expect(title(row('credit.transferred', { amount: 3 }), null)).toBe(
      '3 Credits transferred for Request #12',
    )
    expect(
      title(
        row('credit.dispute-settled', { courierAmount: 1, requesterAmount: 2 }),
        null,
      ),
    ).toBe('Credits settled for the dispute on Request #12')
  })

  it('degrades when the amount is missing', () => {
    expect(title(row('credit.released', {}), 'usr-req-1001')).toBe(
      'Credits refunded for Request #12',
    )
  })

  it('knows the three credit-change types and nothing else', () => {
    for (const type of [
      'credit.transferred',
      'credit.released',
      'credit.dispute-settled',
    ]) {
      expect(isCreditEvent(type)).toBe(true)
      // the copy never names a courier, so no username is looked up
      expect(actorIdOf(type, request)).toBeNull()
    }
    for (const type of ['credit.reserved', 'request.completed', 'toString']) {
      expect(isCreditEvent(type)).toBe(false)
    }
  })
})
