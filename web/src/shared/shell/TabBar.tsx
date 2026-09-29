// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: mobile bottom tab bar with the five nav destinations
// (per web/docs/wireframes/ — the mobile frames use a tab bar,
// not a hamburger menu).
// 2026-09-29, Claude Code (Opus 5.5), issue #147: a sixth "Admin" tab
// for ADMIN and OWNER only.
// Reviewed by: Leong Wei Zhi (via pull request).

import { NavLink } from 'react-router'

import { isAdmin, useAuth } from '../../features/user/useAuth'
import { adminNavItem, navItems } from './navigation'

export function TabBar() {
  const items = isAdmin(useAuth().me) ? [...navItems, adminNavItem] : navItems

  return (
    <nav
      aria-label="Main tabs"
      className="fixed inset-x-0 bottom-0 border-t border-gray-200 bg-white pb-[env(safe-area-inset-bottom)] md:hidden"
    >
      <div
        className={`grid ${items.length > 5 ? 'grid-cols-6' : 'grid-cols-5'}`}
      >
        {items.map((item) => (
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
            {item.shortLabel ?? item.label}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}
