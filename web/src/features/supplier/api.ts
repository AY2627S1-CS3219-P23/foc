// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22.
// Scope: Supplier Service REST calls, routed through the shared
// apiFetch wrapper (src/lib/api/http.ts) so auth headers and
// RFC 9457 error parsing stay in one place. Endpoint shapes match
// docs/supplier-service.md's Browse/search and Admin CRUD APIs;
// confirm exact paths once #101/#6/#7 land on the backend.
// Reviewed by: [pending]

import { apiFetch } from '@/lib/api/http'
import type { Supplier, SupplierInput, Zone } from './types'

export interface SupplierSearchParams {
  q?: string
  category?: string
  zoneCode?: string
}

function toQuery(params: SupplierSearchParams): string {
  const search = new URLSearchParams()
  if (params.q) search.set('q', params.q)
  if (params.category) search.set('category', params.category)
  if (params.zoneCode) search.set('zoneCode', params.zoneCode)
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

export function listSuppliers(params: SupplierSearchParams = {}) {
  return apiFetch<Supplier[]>('supplier', `/suppliers${toQuery(params)}`)
}

export function getSupplier(id: string) {
  return apiFetch<Supplier>('supplier', `/suppliers/${id}`)
}

export function listZones() {
  return apiFetch<Zone[]>('supplier', '/zones')
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
