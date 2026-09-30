// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22; revised 2026-09-26 (Claude Code, Sonnet 5).
// Scope: add/edit supplier form, per
// web/docs/wireframes/add-edit-supplier.png. Implements F1.1.1
// (create: name, location, categories, opening times, description)
// and F1.1.2 (update).
// 2026-09-26: removed the Campus Zone field — the team dropped the
// zone feature, and with no zones to populate the (required) select,
// the form could never pass HTML5 validation, blocking every edit.
// 2026-09-27: added a red asterisk to each required field's label
// (Name, Location, Opening/Close) so the `required` HTML5 validation
// already in place is visible before a user hits Save, not just as a
// browser-native error after the fact.
// 2026-09-29, Claude Code (Sonnet 5), issue #104: added Latitude/
// Longitude inputs — previously the form silently sent 0/0 for a new
// supplier (initial?.latitude ?? 0) with no way to enter real
// coordinates, so every newly-created supplier was pinned to (0, 0)
// and sorted wrongly (or not at all) by "Nearest to Me". Validated as
// required numbers in range (type="number", min/max/step="any", the
// same native-HTML5-validation approach already used for the other
// required fields) — backend also validates the same range
// (SupplierRequest).
// PR #104 review (Copilot): the form has no imageUrl field, so every
// edit sent it as undefined and SupplierService.applyRequest blindly
// overwrote the existing image with null — round-tripping
// initial?.imageUrl on save so editing a supplier no longer erases its
// seed/existing image. No UI for clearing an image exists, so there's
// no case where omission should mean "remove it."
// 2026-09-29 (author request): replaced that round-tripped value with
// an actual editable Image URL field — a plain text input (paste a
// link), matching the data model exactly (Suppliers.imageURL is
// already just a URL string; even the seed data points at
// GitHub-hosted images, not uploaded files, so this needs no backend
// change). Optional, like Description; clearing it now intentionally
// removes the image, which the round-trip approach couldn't do.
// 2026-09-30, Claude Code (Opus 5.5): a click outside the form no longer
// closes it (Modal closeOnBackdrop={false}); only Cancel or ✕ do, so a
// half-filled supplier isn't lost to a stray click. PR #156 review: an
// optional `error` shown inside the form, so a failed save's reason is
// visible (the form covers the page behind it).
// Reviewed by: [pending]

import { useState, type FormEvent, type KeyboardEvent } from 'react'

import { Modal } from '@/shared/components/Modal'
import type { Supplier, SupplierInput } from '../types'

interface SupplierFormModalProps {
  initial?: Supplier
  onCancel: () => void
  onSave: (input: SupplierInput) => void
  saving?: boolean
  // why the last save failed; the form stays open to fix and retry
  error?: string | null
}

export function SupplierFormModal({
  initial,
  onCancel,
  onSave,
  saving,
  error,
}: SupplierFormModalProps) {
  const [name, setName] = useState(initial?.name ?? '')
  const [location, setLocation] = useState(initial?.location ?? '')
  const [categories, setCategories] = useState<string[]>(initial?.categories ?? [])
  const [categoryDraft, setCategoryDraft] = useState('')
  const [openingTime, setOpeningTime] = useState(initial?.openingTime ?? '09:00')
  const [closingTime, setClosingTime] = useState(initial?.closingTime ?? '18:00')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [latitude, setLatitude] = useState(initial?.latitude !== undefined ? String(initial.latitude) : '')
  const [longitude, setLongitude] = useState(initial?.longitude !== undefined ? String(initial.longitude) : '')
  const [imageUrl, setImageUrl] = useState(initial?.imageUrl ?? '')

  function addCategory() {
    const value = categoryDraft.trim()
    if (value && !categories.includes(value)) {
      setCategories([...categories, value])
    }
    setCategoryDraft('')
  }

  function handleCategoryKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault()
      addCategory()
    }
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    onSave({
      name,
      location,
      categories,
      openingTime,
      closingTime,
      description,
      latitude: Number(latitude),
      longitude: Number(longitude),
      imageUrl: imageUrl.trim(),
    })
  }

  return (
    <Modal
      title={initial ? 'Edit Supplier' : 'Add Supplier'}
      onClose={onCancel}
      closeOnBackdrop={false}
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <label className="block text-sm">
          <span className="font-medium text-gray-700">
            Supplier Name <span className="text-red-500">*</span>
          </span>
          <input
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 focus:border-gray-400 focus:outline-none"
          />
        </label>

        <label className="block text-sm">
          <span className="font-medium text-gray-700">
            Location <span className="text-red-500">*</span>
          </span>
          <input
            required
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            placeholder="Building, unit"
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
          />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm">
            <span className="font-medium text-gray-700">
              Latitude <span className="text-red-500">*</span>
            </span>
            <input
              required
              type="number"
              step="any"
              min={-90}
              max={90}
              value={latitude}
              onChange={(e) => setLatitude(e.target.value)}
              placeholder="1.2966"
              className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
            />
          </label>
          <label className="block text-sm">
            <span className="font-medium text-gray-700">
              Longitude <span className="text-red-500">*</span>
            </span>
            <input
              required
              type="number"
              step="any"
              min={-180}
              max={180}
              value={longitude}
              onChange={(e) => setLongitude(e.target.value)}
              placeholder="103.7764"
              className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
            />
          </label>
        </div>

        <div className="text-sm">
          <span className="font-medium text-gray-700">Categories</span>
          <div className="mt-1 flex flex-wrap items-center gap-1.5 rounded-md border border-gray-200 px-2 py-1.5">
            {categories.map((c) => (
              <span
                key={c}
                className="flex items-center gap-1 rounded-md bg-gray-100 px-2 py-0.5 text-xs text-gray-700"
              >
                {c}
                <button
                  type="button"
                  onClick={() => setCategories(categories.filter((x) => x !== c))}
                  aria-label={`Remove ${c}`}
                  className="text-gray-400 hover:text-gray-600"
                >
                  ✕
                </button>
              </span>
            ))}
            <input
              value={categoryDraft}
              onChange={(e) => setCategoryDraft(e.target.value)}
              onKeyDown={handleCategoryKeyDown}
              onBlur={addCategory}
              placeholder="Type to add more..."
              className="min-w-[8rem] flex-1 border-none p-0.5 text-sm placeholder:text-gray-400 focus:outline-none"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm">
            <span className="font-medium text-gray-700">
              Opening Hours — Open <span className="text-red-500">*</span>
            </span>
            <input
              required
              type="time"
              value={openingTime}
              onChange={(e) => setOpeningTime(e.target.value)}
              className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 focus:border-gray-400 focus:outline-none"
            />
          </label>
          <label className="block text-sm">
            <span className="font-medium text-gray-700">
              Close <span className="text-red-500">*</span>
            </span>
            <input
              required
              type="time"
              value={closingTime}
              onChange={(e) => setClosingTime(e.target.value)}
              className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 focus:border-gray-400 focus:outline-none"
            />
          </label>
        </div>

        <label className="block text-sm">
          <span className="font-medium text-gray-700">Description</span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Describe the supplier, services, or delivery details..."
            rows={3}
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
          />
        </label>

        <label className="block text-sm">
          <span className="font-medium text-gray-700">Image URL</span>
          <input
            type="url"
            value={imageUrl}
            onChange={(e) => setImageUrl(e.target.value)}
            placeholder="https://..."
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
          />
        </label>

        {error && (
          <p
            role="alert"
            className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {error}
          </p>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={saving}
            className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
          >
            {saving ? 'Saving...' : 'Save Supplier'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
