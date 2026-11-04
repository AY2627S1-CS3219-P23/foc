// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-11-04, Order F8.1.
// Scope: marks open requests whose pickup is more than 1 km from the
// pickup of any delivery the courier holds, so Open Requests can show
// the wireframe's "Too far (>1 km)" state before they click (author's
// decision). Advisory only: order-service's accept check is the rule,
// and anything this can't work out (suppliers not loaded, a pickup
// without coordinates) is left unmarked for the server to decide.
// Author review: Ko-Khan, pending pull request review.

import { useCallback, useEffect, useState } from 'react'

import { listSuppliers } from '@/features/supplier/api'
import { haversineDistanceMeters } from '@/features/supplier/distance'
import type { RequestSummary } from './types'

// F8.1: a courier's held pickups may be at most this far apart
export const MAX_PICKUPS_APART_METERS = 1000

const PAGE_SIZE = 100

interface Point {
  latitude: number
  longitude: number
}

// Every supplier's coordinates by id, or null until (or unless) loaded.
async function loadCoordinates(): Promise<Map<number, Point>> {
  const points = new Map<number, Point>()
  let page = 0
  let totalPages = 1
  while (page < totalPages) {
    const result = await listSuppliers({ page, size: PAGE_SIZE })
    for (const s of result.content) {
      if (Number.isFinite(s.latitude) && Number.isFinite(s.longitude))
        points.set(Number(s.id), {
          latitude: s.latitude,
          longitude: s.longitude,
        })
    }
    totalPages = result.totalPages
    page++
  }
  return points
}

// Loaded once, and only while the courier holds a delivery: with none,
// nothing is ever too far. A failed load marks nothing.
export function usePickupProximity(
  held: RequestSummary[],
): (request: RequestSummary) => boolean {
  const holding = held.length > 0
  const [points, setPoints] = useState<Map<number, Point> | null>(null)

  useEffect(() => {
    if (!holding || points !== null) return
    let cancelled = false
    loadCoordinates().then(
      (loaded) => {
        if (!cancelled) setPoints(loaded)
      },
      () => {},
    )
    return () => {
      cancelled = true
    }
  }, [holding, points])

  return useCallback(
    (request: RequestSummary) => {
      if (points === null || request.supplierId == null) return false
      const pickup = points.get(request.supplierId)
      if (!pickup) return false
      return held.some((h) => {
        const other =
          h.supplierId == null ? undefined : points.get(h.supplierId)
        return (
          other !== undefined &&
          haversineDistanceMeters(
            pickup.latitude,
            pickup.longitude,
            other.latitude,
            other.longitude,
          ) > MAX_PICKUPS_APART_METERS
        )
      })
    },
    [points, held],
  )
}
