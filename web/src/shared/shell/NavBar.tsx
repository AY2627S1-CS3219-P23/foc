// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: top bar — brand, desktop nav links, and the always-visible
// credits + notification slots (per web/docs/wireframes/; mobile
// navigation is the bottom TabBar, not a menu here).
// 2026-09-29, Claude Code (Opus 5.5), issue #147: Admin Dashboard link
// after Profile, for ADMIN and OWNER only.
// Reviewed by: Leong Wei Zhi (via pull request).

import { Link, NavLink } from 'react-router'
import { CreditsBadge } from './CreditsBadge'
import { NotificationBell } from './NotificationBell'
import { adminNavItem, navItems } from './navigation'
import { router } from '../../routes/index'
import { isAdmin, useAuth } from '../../features/user/useAuth'

export function NavBar() {
  const data = useAuth()

  // the admin link joins the others only for ADMIN and OWNER; the /admin
  // route guard and user-service still check the role themselves
  const items = isAdmin(data.me) ? [...navItems, adminNavItem] : navItems

  const toLogin = () => {
    router.navigate('/login')
  }

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
          {items.map((item) => (
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

        {!data?.token ? ( // Conditionally render login and logout based on user state
          <button
            onClick={toLogin}
            className="text-inherit no-underline rounded-md border border-gray-5=800 bg-white px-3 py-1 text-sm font-medium whitespace-nowrap cursor-pointer"
          >
            Log In
          </button>
        ) : (
          <button
            className="text-inherit no-underline rounded-md border border-gray-5=800 bg-white px-3 py-1 text-sm font-medium whitespace-nowrap cursor-pointer"
            onClick={data.logout}
          >
            Logout
          </button>
        )}
      </nav>
    </header>
  )
}
