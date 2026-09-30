// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-30, PR #156 review.
// Scope: the supplier create/update/delete texts, moved here from
// routes/suppliers.tsx and SuppliersAdminSection.tsx (they were verbatim
// copies) so both say the same thing. The delete failure names the
// supplier, since the confirm closes before the alert shows.
// Author review: Ryan to review via the PR.

export function createdSupplier(name: string) {
  return `Successfully created Supplier "${name}".`
}

export function updatedSupplier(name: string) {
  return `Successfully updated Supplier "${name}".`
}

export function deletedSupplier(name: string) {
  return `Successfully deleted Supplier "${name}".`
}

// fallbacks for when the server gives no reason
export const SAVE_SUPPLIER_FAILED = 'Could not save supplier.'

export function deleteSupplierFailed(name: string) {
  return `Could not delete supplier "${name}".`
}
