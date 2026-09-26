// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22; revised 2026-09-26 (Claude Code, Sonnet 5).
// Scope: add/edit supplier form, per
// web/docs/wireframes/add-edit-supplier.png. Implements F1.1.1
// (create: name, location, categories, opening times, description)
// and F1.1.2 (update).
// 2026-09-26: removed the Campus Zone field — the team dropped the
// zone feature, and with no zones to populate the (required) select,
// the form could never pass HTML5 validation, blocking every edit.
// Reviewed by: [pending]

import { useState, type FormEvent, type KeyboardEvent } from 'react'

import { Modal } from '@/shared/components/Modal'
import type { Supplier, SupplierInput } from '../types'

interface SupplierFormModalProps {
  initial?: Supplier
  onCancel: () => void
  onSave: (input: SupplierInput) => void
  saving?: boolean
}

export function SupplierFormModal({
  initial,
  onCancel,
  onSave,
  saving,
}: SupplierFormModalProps) {
  const [name, setName] = useState(initial?.name ?? '')
  const [location, setLocation] = useState(initial?.location ?? '')
  const [categories, setCategories] = useState<string[]>(initial?.categories ?? [])
  const [categoryDraft, setCategoryDraft] = useState('')
  const [openingTime, setOpeningTime] = useState(initial?.openingTime ?? '09:00')
  const [closingTime, setClosingTime] = useState(initial?.closingTime ?? '18:00')
  const [description, setDescription] = useState(initial?.description ?? '')

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
      latitude: initial?.latitude ?? 0,
      longitude: initial?.longitude ?? 0,
    })
  }

  return (
    <Modal title={initial ? 'Edit Supplier' : 'Add Supplier'} onClose={onCancel}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <label className="block text-sm">
          <span className="font-medium text-gray-700">Supplier Name</span>
          <input
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 focus:border-gray-400 focus:outline-none"
          />
        </label>

        <label className="block text-sm">
          <span className="font-medium text-gray-700">Location</span>
          <input
            required
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            placeholder="Building, unit"
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
          />
        </label>

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
            <span className="font-medium text-gray-700">Opening Hours — Open</span>
            <input
              required
              type="time"
              value={openingTime}
              onChange={(e) => setOpeningTime(e.target.value)}
              className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 focus:border-gray-400 focus:outline-none"
            />
          </label>
          <label className="block text-sm">
            <span className="font-medium text-gray-700">Close</span>
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
