// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #257.
// Scope: the public profile (/users/:id) per
// web/docs/wireframes/public-profile.png — username, rating summary,
// paged Recent Reviews with rater names hydrated at read time (D32),
// and the Report User dialog. The page gates on user-service's
// public-profile route itself (not the usernames hook, which swallows
// 404s): a 404 is "user not found", anything else is retryable. The
// wireframe's joined date and deliveries-completed tile are omitted —
// no API carries either (public profile is username-only per F8.1.1);
// follow-up issue filed. The summary's average is a raw mean and null
// until the first rating, so the page rounds and never renders "null".
// 2026-10-31, Claude Code (Fable 5), PR #279 review (@Sinnez1): a
// non-canonical :id ('me', '09') is rejected before any fetch — the two
// services would read it differently (user-service binds a Long and
// owns /users/me; rating-service matches the exact string), letting a
// report dodge the self-report check — and a 400 from the gate reads as
// user-not-found, not retryable. The gate, summary and reviews each got
// a Try again; the reviews keep the last good page up (dimmed, range
// stamped from the answer itself, pager disabled while loading — the
// PastDistributions pattern); rater names stay blank until the lookup
// settles, and "Unknown user" is reserved for a confirmed 404; review
// text keeps its line breaks and wraps long unbroken strings; a whole
// average keeps its trailing zero (5.0).
// Author review: [pending]

import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router'

import { Banner } from '@/features/order/components/Banner'
import { formatDateTime } from '@/features/order/status'
import { useCurrentUserId } from '@/features/order/useCurrentUserId'
import { ratingApi } from '@/features/rating/api'
import { ReportUserModal } from '@/features/rating/components/ReportUserModal'
import { StarDisplay } from '@/features/rating/components/StarDisplay'
import type {
  RatingSummaryResponse,
  ReviewResponse,
} from '@/features/rating/types'
import type { PagedResponse } from '@/features/supplier/types'
import { useUsernameLookups } from '@/features/user/usernames'
import { ApiError, apiFetch, errorMessage } from '@/lib/api/http'
import { Pagination } from '@/shared/components/Pagination'

// the wireframe's Recent Reviews list is short; the service allows 1..100
const PAGE_SIZE = 5

// User ids are positive longs, and only their canonical decimal spelling
// may reach the services: user-service would canonicalize 'me' (its own
// alias) or '09' (Long-parsed to 9) while rating-service stores the raw
// string, so a non-canonical id renders another user's profile and files
// reports past the self-report check (PR #279 review).
const CANONICAL_USER_ID = /^[1-9]\d*$/

export function PublicProfile() {
  const { id } = useParams()
  // keying on the id remounts the view, so one profile's reviews, errors
  // and dialogs can never show under another's URL (active-delivery.tsx)
  return <PublicProfileView key={id} userId={id ?? ''} />
}

type Gate =
  | { kind: 'loading' }
  | { kind: 'ready'; username: string }
  | { kind: 'missing' }
  | { kind: 'error'; message: string }

function PublicProfileView({ userId }: { userId: string }) {
  const currentUserId = useCurrentUserId()
  // a non-canonical id is missing from the start, before any fetch; the
  // initializer is enough because the view is remounted per id (key={id})
  const [gate, setGate] = useState<Gate>(() =>
    CANONICAL_USER_ID.test(userId) ? { kind: 'loading' } : { kind: 'missing' },
  )
  // bumped by Try again to re-run the gate fetch (PR #279 review)
  const [attempt, setAttempt] = useState(0)
  const [reporting, setReporting] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    if (!CANONICAL_USER_ID.test(userId)) return
    let cancelled = false
    apiFetch<{ username: string }>(
      'user',
      `/users/${encodeURIComponent(userId)}`,
    )
      .then((profile) => {
        if (!cancelled) setGate({ kind: 'ready', username: profile.username })
      })
      .catch((err: unknown) => {
        if (cancelled) return
        // 404 is the server's word that the user is gone (or never was),
        // and a 400 that it can't name one — retrying helps neither;
        // transient failures read as retryable errors
        if (
          err instanceof ApiError &&
          (err.status === 404 || err.status === 400)
        ) {
          setGate({ kind: 'missing' })
        } else {
          setGate({
            kind: 'error',
            message: errorMessage(err, 'Could not load this profile.'),
          })
        }
      })
    return () => {
      cancelled = true
    }
  }, [userId, attempt])

  if (gate.kind === 'loading') {
    return <p className="text-sm text-gray-500">Loading profile…</p>
  }
  if (gate.kind === 'missing') {
    return (
      <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
        <p className="font-medium text-gray-900">User not found.</p>
        <p className="mt-1 text-sm text-gray-500">
          This account does not exist or has been deleted.
        </p>
      </div>
    )
  }
  if (gate.kind === 'error') {
    return (
      <p role="alert" className="text-sm text-red-700">
        {gate.message}{' '}
        <button
          type="button"
          onClick={() => {
            setGate({ kind: 'loading' })
            setAttempt((a) => a + 1)
          }}
          className="font-medium underline"
        >
          Try again
        </button>
      </p>
    )
  }

  const isSelf = currentUserId !== null && String(currentUserId) === userId

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      {notice && <Banner kind="success">{notice}</Banner>}

      <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <SummarySection userId={userId} username={gate.username} />
        <ReviewsSection userId={userId} />

        {!isSelf && (
          <div className="mt-6 border-t border-gray-100 pt-4 text-center">
            <button
              type="button"
              onClick={() => setReporting(true)}
              className="text-sm font-medium text-red-600 hover:text-red-700"
            >
              ⚑ Report User
            </button>
          </div>
        )}
      </div>

      {reporting && (
        <ReportUserModal
          reportedUserId={userId}
          reportedUsername={gate.username}
          onClose={() => setReporting(false)}
          onReported={() => {
            setReporting(false)
            // the service keeps every submission (no duplicate check),
            // so the copy promises review, not a one-time flag
            setNotice('Report submitted. An admin will review it.')
          }}
        />
      )}
    </div>
  )
}

function SummarySection({
  userId,
  username,
}: {
  userId: string
  username: string
}) {
  const [summary, setSummary] = useState<RatingSummaryResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  // bumped by Try again to re-run the fetch (PR #279 review)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let cancelled = false
    ratingApi
      .getSummary(userId)
      .then((s) => {
        if (cancelled) return
        setSummary(s)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!cancelled)
          setError(errorMessage(err, 'Could not load the rating summary.'))
      })
    return () => {
      cancelled = true
    }
  }, [userId, attempt])

  // toFixed keeps the trailing zero, so a whole average reads 5.0 — the
  // same one-decimal shape as 4.2 (PR #279 review)
  const average =
    summary !== null && summary.average !== null
      ? summary.average.toFixed(1)
      : null

  return (
    <div className="flex flex-col items-center text-center">
      <div
        aria-hidden="true"
        className="flex h-20 w-20 items-center justify-center rounded-full bg-gray-100 text-2xl font-semibold text-gray-400"
      >
        {username.charAt(0).toUpperCase()}
      </div>
      <h1 className="mt-3 text-2xl font-semibold text-gray-900">{username}</h1>

      {error !== null ? (
        <p role="alert" className="mt-3 text-sm text-red-700">
          {error}{' '}
          <button
            type="button"
            onClick={() => {
              setError(null)
              setAttempt((a) => a + 1)
            }}
            className="font-medium underline"
          >
            Try again
          </button>
        </p>
      ) : summary === null ? (
        <p className="mt-3 text-sm text-gray-500">Loading rating…</p>
      ) : summary.count === 0 ? (
        <div className="mt-3 flex items-center gap-2">
          <StarDisplay value={0} size="lg" />
          <span className="text-sm text-gray-500">No reviews yet</span>
        </div>
      ) : (
        <>
          <div className="mt-3 flex items-center gap-2">
            <StarDisplay value={summary.average ?? 0} size="lg" />
            <span className="font-semibold text-gray-900">{average}</span>
            <span className="text-sm text-gray-500">
              ({summary.count} {summary.count === 1 ? 'review' : 'reviews'})
            </span>
          </div>
          <div className="mt-4 rounded-md border border-gray-200 px-8 py-3">
            <p className="text-xl font-semibold text-gray-900">{average}</p>
            <p className="text-xs tracking-wide text-gray-500 uppercase">
              Avg rating received
            </p>
          </div>
        </>
      )}
    </div>
  )
}

// A result is stamped with the page and attempt it answers, so the last
// good page (and with it the pager) stays up through a failed or pending
// load — the PastDistributions pattern (PR #279 review).
interface ReviewsResult {
  page: number
  // which load of `page` this answers; a retry is a new attempt
  attempt: number
  reviews?: PagedResponse<ReviewResponse>
  error?: string
}

function ReviewsSection({ userId }: { userId: string }) {
  const [page, setPage] = useState(0)
  // bumped on every navigation/retry: after a failed load the pager
  // still shows the last good page, so re-picking `page` must refetch
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<ReviewsResult | null>(null)

  useEffect(() => {
    let cancelled = false
    ratingApi.listReviews(userId, page, PAGE_SIZE).then(
      (reviews) => {
        if (cancelled) return
        setResult({ page, attempt, reviews })
        // Defensive: if the page we asked for no longer exists, step back
        // to the last one that does (request-history.tsx).
        if (page > 0 && page >= reviews.totalPages) {
          setPage(Math.max(0, reviews.totalPages - 1))
        }
      },
      (err: unknown) => {
        if (!cancelled)
          setResult((prev) => ({
            page,
            attempt,
            reviews: prev?.reviews,
            error: errorMessage(err, 'Could not load the reviews.'),
          }))
      },
    )
    return () => {
      cancelled = true
    }
  }, [userId, page, attempt])

  const goTo = (next: number) => {
    setPage(next)
    setAttempt((a) => a + 1)
  }

  // a retry of the page already shown is loading too, until it answers
  const current =
    result !== null && result.page === page && result.attempt === attempt
  const loading = !current
  // the last good page stays up while the next one loads or fails
  const shown = result?.reviews
  const error = current ? result.error : undefined

  // memoized so the hydration effect keys on the reviews, not on a fresh
  // array identity every render (NotificationCenter's pattern)
  const raterIds = useMemo(
    () => (shown?.content ?? []).map((r) => r.raterId),
    [shown],
  )
  const lookups = useUsernameLookups(raterIds)

  // the range comes from the answer itself, never the requested page, so
  // a click never captions the old rows with the new page's numbers
  const from =
    shown === undefined || shown.totalElements === 0
      ? 0
      : shown.page * PAGE_SIZE + 1
  const to =
    shown === undefined ? 0 : shown.page * PAGE_SIZE + shown.content.length

  return (
    <section className="mt-8" aria-label="Recent reviews" aria-busy={loading}>
      <h2 className="text-lg font-semibold text-gray-900">Recent Reviews</h2>

      {error !== undefined && (
        <p
          role="alert"
          className="mt-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}{' '}
          <button
            type="button"
            onClick={() => setAttempt((a) => a + 1)}
            className="font-medium underline"
          >
            Try again
          </button>
        </p>
      )}
      {error === undefined &&
        loading &&
        (shown === undefined || shown.content.length === 0) && (
          <p className="mt-3 text-sm text-gray-500">Loading reviews…</p>
        )}
      {error === undefined &&
        !loading &&
        shown !== undefined &&
        shown.content.length === 0 && (
          <div className="mt-3 rounded-lg border border-gray-200 py-8 text-center">
            <p className="font-medium text-gray-900">No reviews yet.</p>
            <p className="mt-1 text-sm text-gray-500">
              Reviews from completed requests will show up here.
            </p>
          </div>
        )}
      {shown !== undefined && shown.content.length > 0 && (
        <>
          <ul className={`mt-3 space-y-3 ${loading ? 'opacity-60' : ''}`}>
            {shown.content.map((r, i) => {
              const rater = lookups.get(r.raterId)
              return (
                // the list endpoint carries no review id; createdAt+raterId
                // with the index breaks any same-instant tie
                <li
                  key={`${r.createdAt}-${r.raterId}-${i}`}
                  className="rounded-lg border border-gray-200 p-4"
                >
                  <div className="flex items-center justify-between gap-2">
                    <StarDisplay value={r.stars} />
                    <span className="text-xs text-gray-400">
                      {formatDateTime(r.createdAt)}
                    </span>
                  </div>
                  {r.text !== null ? (
                    <p className="mt-2 text-sm break-words whitespace-pre-line text-gray-700">
                      {r.text}
                    </p>
                  ) : (
                    <p className="mt-2 text-sm text-gray-400 italic">
                      No written review
                    </p>
                  )}
                  {/* blank (space keeps the line) until the lookup settles;
                      Unknown user only on a confirmed 404 — a transient
                      lookup failure must not read as a deleted account */}
                  <p className="mt-2 text-xs font-medium text-gray-500">
                    {rater?.username ?? (rater?.missing ? 'Unknown user' : ' ')}
                  </p>
                </li>
              )
            })}
          </ul>

          <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-sm text-gray-600">
            <span>
              Showing {from}-{to} of {shown.totalElements}{' '}
              {shown.totalElements === 1 ? 'review' : 'reviews'}
            </span>
            {shown.totalPages > 1 && (
              <Pagination
                page={shown.page + 1}
                totalPages={shown.totalPages}
                onPageChange={(p) => goTo(p - 1)}
                disabled={loading}
              />
            )}
          </div>
        </>
      )}
    </section>
  )
}
