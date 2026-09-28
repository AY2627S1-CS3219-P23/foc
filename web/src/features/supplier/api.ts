// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22; revised 2026-09-26 (Claude Code, Sonnet 5).
// Scope: Supplier Service REST calls, routed through the shared
// apiFetch wrapper (src/lib/api/http.ts) so auth headers and
// RFC 9457 error parsing stay in one place. Endpoint shapes match
// docs/supplier-service.md's Browse/search and Admin CRUD APIs;
// confirm exact paths once #101/#6/#7 land on the backend.
// 2026-09-26 (issue #133): GET /suppliers now paginates — `q` renamed
// to `search` to match the real backend param name; `zoneCode` dropped
// (team decision to drop the zone feature; the backend never gained a
// zone filter); `page`/`size` added; return type changed from
// `Supplier[]` to `PagedResponse<Supplier>`. `listZones` removed — the
// backend has no /zones endpoint, and calling it was breaking the
// suppliers page entirely (Promise.all rejects if either call fails).
// Added listCategories() (GET /suppliers/categories) — the filter
// dropdown needs every category that exists, not just those on the
// currently filtered/paginated result set.
// 2026-09-27: added `sort` (PR #134 Copilot review — the backend
// supported it via Pageable but this helper never serialized one) and
// optional lat/lng — team decision to support "sort by distance from
// the user's current location." When lat/lng are present, the backend
// orders by distance from that point instead of `sort`.
// Reviewed by: [pending]

import { apiFetch } from '@/lib/api/http'
import type { PagedResponse, Supplier, SupplierInput } from './types'

export interface SupplierSearchParams {
  search?: string
  category?: string
  page?: number
  size?: number
  sort?: string
  lat?: number
  lng?: number
}

function toQuery(params: SupplierSearchParams): string {
  const search = new URLSearchParams()
  if (params.search) search.set('search', params.search)
  if (params.category) search.set('category', params.category)
  if (params.page !== undefined) search.set('page', String(params.page))
  if (params.size !== undefined) search.set('size', String(params.size))
  if (params.sort) search.set('sort', params.sort)
  if (params.lat !== undefined) search.set('lat', String(params.lat))
  if (params.lng !== undefined) search.set('lng', String(params.lng))
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

export function listSuppliers(params: SupplierSearchParams = {}) {
  return apiFetch<PagedResponse<Supplier>>('supplier', `/suppliers${toQuery(params)}`)
}

export function getSupplier(id: string) {
  return apiFetch<Supplier>('supplier', `/suppliers/${id}`)
}

export function listCategories() {
  return apiFetch<string[]>('supplier', '/suppliers/categories')
}

export function createSupplier(input: SupplierInput) {
  return apiFetch<Supplier>('supplier', '/suppliers', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateSupplier(id: string, input: SupplierInput) {
  return apiFetch<Supplier>('supplier', `/suppliers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function deleteSupplier(id: string) {
  return apiFetch<void>('supplier', `/suppliers/${id}`, {
    method: 'DELETE',
  })
}
