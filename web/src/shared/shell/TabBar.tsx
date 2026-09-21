// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: mobile bottom tab bar with the five nav destinations
// (per web/docs/wireframes/ — the mobile frames use a tab bar,
// not a hamburger menu).
// Reviewed by: Leong Wei Zhi (via pull request).

import { NavLink } from 'react-router'

import { navItems } from './navigation'

export function TabBar() {
  return (
    <nav
      aria-label="Main tabs"
      className="fixed inset-x-0 bottom-0 border-t border-gray-200 bg-white pb-[env(safe-area-inset-bottom)] md:hidden"
    >
      <div className="grid grid-cols-5">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              `flex flex-col items-center gap-1 py-2 text-[11px] font-medium ${
                isActive ? 'text-gray-900' : 'text-gray-500'
              }`
            }
          >
            <svg
              className="h-5 w-5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              {item.iconPaths.map((d) => (
                <path key={d} d={d} />
              ))}
            </svg>
            {item.label}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}
