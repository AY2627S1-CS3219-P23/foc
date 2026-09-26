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
}

// Payload shape for create/update — server assigns `id`.
export type SupplierInput = Omit<Supplier, 'id'>
