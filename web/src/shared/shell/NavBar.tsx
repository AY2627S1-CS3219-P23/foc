// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: top bar — brand, desktop nav links, and the always-visible
// credits + notification slots (per web/docs/wireframes/; mobile
// navigation is the bottom TabBar, not a menu here).
// Reviewed by: Leong Wei Zhi (via pull request).

import { Link, NavLink } from 'react-router'

import { CreditsBadge } from './CreditsBadge'
import { NotificationBell } from './NotificationBell'
import { navItems } from './navigation'

export function NavBar() {
  return (
    <header className="border-b border-gray-200 bg-white">
      <nav
        aria-label="Main"
        className="mx-auto flex max-w-6xl items-center gap-2 px-4 py-3"
      >
        <Link to="/" className="text-base font-semibold tracking-tight">
          FoC (Favours on Campus)
        </Link>

        {/* Desktop links; on mobile the TabBar carries these destinations */}
        <div className="ml-6 hidden items-center gap-1 md:flex">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `rounded-md px-3 py-2 text-sm font-medium hover:bg-gray-100 ${
                  isActive ? 'bg-gray-100 text-gray-900' : 'text-gray-600'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </div>

        {/* Credits + notifications: visible everywhere at every width */}
        <div className="ml-auto flex items-center gap-3">
          <CreditsBadge />
          <NotificationBell />
        </div>
      </nav>
    </header>
  )
}
