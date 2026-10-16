// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: the credits page's filter row: minimum and maximum amount
// (team decision: two number fields in place of the wireframe's
// "Amount: All" dropdown, mapping straight to minAmount/maxAmount), a
// from/to date range, and Clear Filters.
// Author review: Ryan Ang, pending pull request review.

import { NO_FILTERS, type HistoryFilterInputs } from '../history'

interface HistoryFiltersProps {
  value: HistoryFilterInputs
  onChange: (next: HistoryFilterInputs) => void
  error?: string
}

const inputClass =
  'w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900'

export function HistoryFilters({
  value,
  onChange,
  error,
}: HistoryFiltersProps) {
  const set =
    (field: keyof HistoryFilterInputs) =>
    (e: React.ChangeEvent<HTMLInputElement>) =>
      onChange({ ...value, [field]: e.target.value })

  const anySet = Object.values(value).some((v) => v !== '')

  return (
    <fieldset>
      <legend className="sr-only">Filter transactions</legend>
      <div className="grid grid-cols-2 gap-2 md:flex md:items-end md:gap-3">
        <label className="text-xs font-medium text-gray-600 md:w-32">
          Min amount
          <input
            type="number"
            inputMode="numeric"
            min={1}
            step={1}
            value={value.minAmount}
            onChange={set('minAmount')}
            className={`${inputClass} mt-1`}
          />
        </label>
        <label className="text-xs font-medium text-gray-600 md:w-32">
          Max amount
          <input
            type="number"
            inputMode="numeric"
            min={1}
            step={1}
            value={value.maxAmount}
            onChange={set('maxAmount')}
            className={`${inputClass} mt-1`}
          />
        </label>
        <label className="text-xs font-medium text-gray-600 md:w-44">
          From
          <input
            type="date"
            value={value.from}
            onChange={set('from')}
            className={`${inputClass} mt-1`}
          />
        </label>
        <label className="text-xs font-medium text-gray-600 md:w-44">
          To
          <input
            type="date"
            value={value.to}
            onChange={set('to')}
            className={`${inputClass} mt-1`}
          />
        </label>
        <button
          type="button"
          onClick={() => onChange(NO_FILTERS)}
          disabled={!anySet}
          className="col-span-2 justify-self-end py-2 text-sm font-medium text-gray-900 underline disabled:cursor-not-allowed disabled:text-gray-400 disabled:no-underline md:ml-auto"
        >
          Clear Filters
        </button>
      </div>
      {error && (
        <p role="alert" className="mt-2 text-sm text-red-700">
          {error}
        </p>
      )}
    </fieldset>
  )
}
