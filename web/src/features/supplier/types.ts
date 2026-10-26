// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22; revised 2026-09-26 (Claude Code, Sonnet 5).
// Scope: Supplier domain types, matching docs/supplier-service.md's
// entities (Supplier, SupplierCategory) and the finalized D1 FRs
// (F1.1.1: name, location, categories, opening times, description).
// 2026-09-26 (issue #133): added PagedResponse to match the new
// GET /suppliers pagination envelope (author decision, kept small
// rather than serializing Spring Data's Page shape directly).
// 2026-09-26: removed the Zone type and Supplier.zoneCode — the team
// dropped the zone feature, and keeping a required `zoneCode` field
// with nothing to populate it was blocking the edit-supplier form
// (an empty, required <select> can never validate). Removed from
// SupplierFormModal/SupplierFilterBar/SupplierDetailPanel too.
// 2026-10-26, Claude Code (Sonnet 5.5), issue #225 (N8): added the bookmark
// calls (list, add, remove).
// Reviewed by: [pending]

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Supplier {
  id: string
  name: string
  location: string
  latitude: number
  longitude: number
  categories: string[]
  openingTime: string // "HH:mm", 24h
  closingTime: string // "HH:mm", 24h
  description: string
  imageUrl?: string
  // Set by GET /suppliers for the calling user (issue #225, D13).
  bookmarked?: boolean
}

// Payload shape for create/update — server assigns `id`.
export type SupplierInput = Omit<Supplier, 'id'>

// GET /bookmarks (issue #225): the caller's own bookmarks, newest first.
// `count` and `limit` are per user — the cap comes from the server so the
// frontend never hard-codes it.
// PUT /suppliers/{id}/bookmark's answer: `count` is the caller's bookmark
// total after the add, so the client takes it from here rather than counting.
export interface BookmarkResult {
  supplierId: string
  bookmarked: boolean
  bookmarkedAt: string
  count: number
}

export interface BookmarkList {
  count: number
  limit: number
  bookmarks: { supplierId: string; name: string; bookmarkedAt: string }[]
}
