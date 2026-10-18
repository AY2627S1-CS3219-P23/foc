// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: the Created → Accepted → Collected → Delivered → Completed
// tracker from my-requests.png — numbered steps on desktop, a segmented
// bar on mobile.
// Author review: [pending]

import { progressOf, progressSteps } from '../status'
import type { OrderStatus } from '../types'

export function OrderProgress({ status }: { status: OrderStatus }) {
  const reached = progressOf(status)
  return (
    <>
      <ol
        aria-label="Progress"
        className="hidden items-center gap-3 text-xs sm:flex"
      >
        {progressSteps.map((label, i) => {
          const done = i < reached
          const current = i === reached - 1
          return (
            <li
              key={label}
              aria-current={current ? 'step' : undefined}
              className={`flex items-center gap-1.5 ${
                current
                  ? 'font-semibold text-gray-900'
                  : done
                    ? 'text-gray-700'
                    : 'text-gray-400'
              }`}
            >
              <span
                className={`flex h-5 w-5 items-center justify-center rounded-full text-[11px] text-white ${
                  done ? 'bg-gray-700' : 'bg-gray-300'
                }`}
              >
                {i + 1}
              </span>
              {label}
              {i < progressSteps.length - 1 && (
                <span
                  className="ml-1.5 h-px w-6 bg-gray-300"
                  aria-hidden="true"
                />
              )}
            </li>
          )
        })}
      </ol>
      <div
        className="flex gap-1 sm:hidden"
        role="img"
        aria-label={`Step ${reached} of ${progressSteps.length}: ${progressSteps[reached - 1]}`}
      >
        {progressSteps.map((label, i) => (
          <span
            key={label}
            className={`h-1.5 flex-1 rounded-full ${i < reached ? 'bg-gray-700' : 'bg-gray-200'}`}
          />
        ))}
      </div>
    </>
  )
}
