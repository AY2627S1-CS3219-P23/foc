// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22.
// Scope: Supplier domain types, matching docs/supplier-service.md's
// entities (Supplier, SupplierCategory, Zone) and the finalized D1
// FRs (F1.1.1: name, location, categories, opening times, description).
// Reviewed by: [pending]

export interface Zone {
  code: string
  name: string
}

export interface Supplier {
  id: string
  name: string
  location: string
  latitude: number
  longitude: number
  zoneCode: string
  categories: string[]
  openingTime: string // "HH:mm", 24h
  closingTime: string // "HH:mm", 24h
  description: string
  imageUrl?: string
}

// Payload shape for create/update — server assigns `id`.
export type SupplierInput = Omit<Supplier, 'id'>
