// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: how a history row is shown (label, sign, description) and how
// the page's filter inputs become the API's query. The sign follows the
// row's change to the available balance (docs/credit-service.md's
// history-type table), with TRANSFER_OUT, which leaves through the
// reserved balance, shown as a loss, as the wireframe's signs read. The
// date conversion follows the contract's "For the web page" notes.
// Author review: Ryan Ang, pending pull request review.

import type {
  CreditHistoryEntry,
  CreditHistoryQuery,
  CreditHistoryType,
} from './types'

interface TypeDisplay {
  label: string
  gain: boolean
  describe(ref: string | null): string
}

const forRequest = (prefix: string, ref: string | null) =>
  ref ? `${prefix} request #${ref}` : prefix

const TYPES: Record<CreditHistoryType, TypeDisplay> = {
  PROVISION: {
    label: 'Initial allocation',
    gain: true,
    describe: () => 'Initial allocation: account created',
  },
  RESERVE: {
    label: 'Reserved',
    gain: false,
    describe: (ref) => forRequest('Reserved: created', ref),
  },
  RELEASE: {
    label: 'Released',
    gain: true,
    describe: (ref) => forRequest('Released: returned from', ref),
  },
  TRANSFER_OUT: {
    label: 'Paid',
    gain: false,
    describe: (ref) => forRequest('Paid: delivered', ref),
  },
  TRANSFER_IN: {
    label: 'Earned',
    gain: true,
    describe: (ref) => forRequest('Earned: delivered', ref),
  },
  EXPIRY: {
    label: 'Expired',
    gain: false,
    describe: () => 'Expired: moved to the common pool',
  },
  REDISTRIBUTION: {
    label: 'Redistributed',
    gain: true,
    describe: () => 'Redistributed: monthly share of the common pool',
  },
}

export function displayEntry(entry: CreditHistoryEntry) {
  const type = TYPES[entry.type]
  return {
    label: type.label,
    description: type.describe(entry.requestRef),
    signedAmount: `${type.gain ? '+' : '−'}${entry.amount}`,
    gain: type.gain,
  }
}

// in the browser's zone, as "16 Oct 2026, 10:00 pm" or the locale's form
const dateTime = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

export function formatOccurredAt(iso: string): string {
  return dateTime.format(new Date(iso))
}

// the filter inputs as typed: number fields and yyyy-mm-dd date fields
export interface HistoryFilterInputs {
  minAmount: string
  maxAmount: string
  from: string
  to: string
}

export const NO_FILTERS: HistoryFilterInputs = {
  minAmount: '',
  maxAmount: '',
  from: '',
  to: '',
}

export type ParsedFilters =
  { ok: true; query: CreditHistoryQuery } | { ok: false; error: string }

// the start of a local day as a UTC instant, or null when `date` is not
// a yyyy-mm-dd date; `dayOffset` 1 gives the start of the next day,
// which is the exclusive end of a range
function localDayStart(date: string, dayOffset = 0): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date)
  if (!match) return null
  const [year, month, day] = match.slice(1).map(Number)
  // setFullYear, not the Date constructor: the constructor reads years 0
  // to 99 as 1900 to 1999
  const start = new Date(0)
  start.setFullYear(year, month - 1, day)
  start.setHours(0, 0, 0, 0)
  // a day the month doesn't have (30 February) rolls over: refuse it
  if (start.getMonth() !== month - 1 || start.getDate() !== day) return null
  start.setDate(day + dayOffset)
  return start.toISOString()
}

function parseAmount(value: string): number | null | 'invalid' {
  if (value.trim() === '') return null
  const amount = Number(value)
  return Number.isInteger(amount) && amount >= 1 ? amount : 'invalid'
}

// checked here as well as by the server, so a bad range never leaves
// the page; the server's 400 is still shown if one slips through
export function parseFilters(inputs: HistoryFilterInputs): ParsedFilters {
  const minAmount = parseAmount(inputs.minAmount)
  const maxAmount = parseAmount(inputs.maxAmount)
  if (minAmount === 'invalid' || maxAmount === 'invalid') {
    return { ok: false, error: 'Amounts must be whole numbers of at least 1.' }
  }
  if (minAmount !== null && maxAmount !== null && minAmount > maxAmount) {
    return {
      ok: false,
      error: 'The minimum amount cannot be above the maximum.',
    }
  }
  const from = inputs.from ? localDayStart(inputs.from) : undefined
  // the last picked day is included, so the range ends where the next
  // one starts
  const to = inputs.to ? localDayStart(inputs.to, 1) : undefined
  if (from === null || to === null) {
    return { ok: false, error: 'Dates must be valid calendar dates.' }
  }
  // yyyy-mm-dd strings compare in date order
  if (inputs.from && inputs.to && inputs.from > inputs.to) {
    return { ok: false, error: 'The start date cannot be after the end date.' }
  }
  return {
    ok: true,
    query: {
      minAmount: minAmount ?? undefined,
      maxAmount: maxAmount ?? undefined,
      from,
      to,
    },
  }
}
