// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-17, issue #174.
// Scope: formatting shared by the admin dashboard's Common Pool box and
// its past-distributions table: times in Singapore time (the zone the
// redistribution runs in, D12), a run's month by name, and a credit
// count with its plural.
// Author review: Ryan Ang, pending pull request review.

const sgt = new Intl.DateTimeFormat('en-SG', {
  timeZone: 'Asia/Singapore',
  dateStyle: 'medium',
  timeStyle: 'short',
})

const monthName = new Intl.DateTimeFormat('en-SG', {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})

// an instant as "1 Nov 2026, 12:00 am SGT", whatever the browser's zone
export function formatSgt(iso: string): string {
  return `${sgt.format(new Date(iso))} SGT`
}

// "2026-10" as "October 2026"
export function periodLabel(period: string): string {
  const [year, month] = period.split('-').map(Number)
  return monthName.format(new Date(Date.UTC(year, month - 1, 1)))
}

export const credits = (n: number) => `${n} ${n === 1 ? 'credit' : 'credits'}`
