// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #257.
// Scope: the public profile (/users/:id) per
// web/docs/wireframes/public-profile.png — username, rating summary,
// paged Recent Reviews with rater names hydrated at read time (D32),
// and the Report User dialog. The page gates on user-service's
// public-profile route itself (not the usernames hook, which swallows
// 404s): a 404 is "user not found", anything else is retryable. The
// summary's average is a raw mean and null until the first rating, so
// the page rounds and never renders "null".
// Author review: [pending]
// 2026-10-31 (Claude Code, Fable 5), issue #280: the wireframe's two
// remaining tiles — "Joined {month year}" (the profile's joinedAt) and
// "{n} deliveries completed" (order-service's completed count, fetched
// only once the gate succeeds; an em dash if it fails) — in a 3-tile
// row that now also holds the Avg-rating tile, shown even before the
// first review. Author review: pending.

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
import { useUsernames } from '@/features/user/usernames'
import { ApiError, apiFetch, errorMessage } from '@/lib/api/http'
import { Pagination } from '@/shared/components/Pagination'

// the wireframe's Recent Reviews list is short; the service allows 1..100
const PAGE_SIZE = 5

export function PublicProfile() {
  const { id } = useParams()
  // keying on the id remounts the view, so one profile's reviews, errors
  // and dialogs can never show under another's URL (active-delivery.tsx)
  return <PublicProfileView key={id} userId={id ?? ''} />
}

type Gate =
  | { kind: 'loading' }
  | { kind: 'ready'; username: string; joinedAt: string }
  | { kind: 'missing' }
  | { kind: 'error'; message: string }

// "October 2026" — the wireframe's "Joined {month year}" precision
const joinedFormat = new Intl.DateTimeFormat('en-SG', {
  month: 'long',
  year: 'numeric',
})

function PublicProfileView({ userId }: { userId: string }) {
  const currentUserId = useCurrentUserId()
  const [gate, setGate] = useState<Gate>({ kind: 'loading' })
  const [reporting, setReporting] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    apiFetch<{ username: string; joinedAt: string }>(
      'user',
      `/users/${encodeURIComponent(userId)}`,
    )
      .then((profile) => {
        if (!cancelled)
          setGate({
            kind: 'ready',
            username: profile.username,
            joinedAt: profile.joinedAt,
          })
      })
      .catch((err: unknown) => {
        if (cancelled) return
        // 404 is the server's word that the user is gone (or never was);
        // a bad id's 400 and transient failures read as retryable errors
        if (err instanceof ApiError && err.status === 404) {
          setGate({ kind: 'missing' })
        } else {
          setGate({
            kind: 'error',
            message: errorMessage(
              err,
              'Could not load this profile. Try again.',
            ),
          })
        }
      })
    return () => {
      cancelled = true
    }
  }, [userId])

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
    return <Banner kind="error">{gate.message}</Banner>
  }

  const isSelf = currentUserId !== null && String(currentUserId) === userId

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      {notice && <Banner kind="success">{notice}</Banner>}

      <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <SummarySection
          userId={userId}
          username={gate.username}
          joinedAt={gate.joinedAt}
        />
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
  joinedAt,
}: {
  userId: string
  username: string
  joinedAt: string
}) {
  const [summary, setSummary] = useState<RatingSummaryResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  // null while loading; an em dash if the count can't be fetched — a
  // missing tile value must not take the rest of the profile with it
  const [completed, setCompleted] = useState<number | null>(null)
  const [completedFailed, setCompletedFailed] = useState(false)

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
  }, [userId])

  // the section only renders once the gate succeeded, so a missing user
  // never triggers this call (the 404 page stays fetch-free)
  useEffect(() => {
    let cancelled = false
    apiFetch<{ count: number }>(
      'order',
      `/requests/completed-count?courierId=${encodeURIComponent(userId)}`,
    )
      .then((response) => {
        if (!cancelled) setCompleted(response.count)
      })
      .catch(() => {
        if (!cancelled) setCompletedFailed(true)
      })
    return () => {
      cancelled = true
    }
  }, [userId])

  const average =
    summary !== null && summary.average !== null
      ? Number(summary.average.toFixed(1))
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

      {error ? (
        <div className="mt-3 w-full">
          <Banner kind="error">{error}</Banner>
        </div>
      ) : summary === null ? (
        <p className="mt-3 text-sm text-gray-500">Loading rating…</p>
      ) : summary.count === 0 ? (
        <div className="mt-3 flex items-center gap-2">
          <StarDisplay value={0} size="lg" />
          <span className="text-sm text-gray-500">No reviews yet</span>
        </div>
      ) : (
        <div className="mt-3 flex items-center gap-2">
          <StarDisplay value={summary.average ?? 0} size="lg" />
          <span className="font-semibold text-gray-900">{average}</span>
          <span className="text-sm text-gray-500">
            ({summary.count} {summary.count === 1 ? 'review' : 'reviews'})
          </span>
        </div>
      )}

      {/* the wireframe's three tiles (#280) — always shown, so a new
          user's profile still answers when they joined; three across,
          as the credit BalanceCards */}
      <div className="mt-4 grid w-full grid-cols-3 gap-2 md:gap-4">
        <ProfileTile
          label="Avg rating received"
          value={average !== null ? String(average) : '—'}
        />
        <ProfileTile
          label="Joined"
          value={joinedFormat.format(new Date(joinedAt))}
        />
        <ProfileTile
          label="Deliveries completed"
          value={
            completed !== null ? String(completed) : completedFailed ? '—' : '…'
          }
        />
      </div>
    </div>
  )
}

// One stat tile: value over label, as the #257 Avg-rating tile drew it.
function ProfileTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-gray-200 px-2 py-3">
      <p className="text-xl font-semibold text-gray-900">{value}</p>
      <p className="text-xs tracking-wide text-gray-500 uppercase">{label}</p>
    </div>
  )
}

function ReviewsSection({ userId }: { userId: string }) {
  const [page, setPage] = useState(0)
  const [reviews, setReviews] = useState<ReviewResponse[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    ratingApi
      .listReviews(userId, page, PAGE_SIZE)
      .then((result) => {
        if (cancelled) return
        setReviews(result.content)
        setTotal(result.totalElements)
        setTotalPages(result.totalPages)
        setError(null)
        // Defensive: if the page we asked for no longer exists, step back
        // to the last one that does (request-history.tsx).
        if (page > 0 && page >= result.totalPages) {
          setPage(Math.max(0, result.totalPages - 1))
        }
      })
      .catch((err: unknown) => {
        if (!cancelled)
          setError(errorMessage(err, 'Could not load the reviews. Try again.'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [userId, page])

  // memoized so the hydration effect keys on the reviews, not on a fresh
  // array identity every render (NotificationCenter's pattern)
  const raterIds = useMemo(() => reviews.map((r) => r.raterId), [reviews])
  const names = useUsernames(raterIds)

  const from = total === 0 ? 0 : page * PAGE_SIZE + 1
  const to = page * PAGE_SIZE + reviews.length

  return (
    <section className="mt-8" aria-label="Recent reviews">
      <h2 className="text-lg font-semibold text-gray-900">Recent Reviews</h2>

      {loading ? (
        <p className="mt-3 text-sm text-gray-500">Loading reviews…</p>
      ) : error ? (
        <div className="mt-3">
          <Banner kind="error">{error}</Banner>
        </div>
      ) : reviews.length === 0 ? (
        <div className="mt-3 rounded-lg border border-gray-200 py-8 text-center">
          <p className="font-medium text-gray-900">No reviews yet.</p>
          <p className="mt-1 text-sm text-gray-500">
            Reviews from completed requests will show up here.
          </p>
        </div>
      ) : (
        <>
          <ul className="mt-3 space-y-3">
            {reviews.map((r, i) => (
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
                  <p className="mt-2 text-sm text-gray-700">{r.text}</p>
                ) : (
                  <p className="mt-2 text-sm text-gray-400 italic">
                    No written review
                  </p>
                )}
                <p className="mt-2 text-xs font-medium text-gray-500">
                  {names.get(r.raterId) ?? 'Unknown user'}
                </p>
              </li>
            ))}
          </ul>

          <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-sm text-gray-600">
            <span>
              Showing {from}-{to} of {total}{' '}
              {total === 1 ? 'review' : 'reviews'}
            </span>
            {totalPages > 1 && (
              <Pagination
                page={page + 1}
                totalPages={totalPages}
                onPageChange={(p) => setPage(p - 1)}
              />
            )}
          </div>
        </>
      )}
    </section>
  )
}
