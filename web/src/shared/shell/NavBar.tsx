// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-20, issue #108.
// Scope: responsive nav bar — desktop links + mobile disclosure menu,
// with the always-visible credits and notification slots.
// Reviewed by: Leong Wei Zhi (via pull request).

import { useState } from 'react'
import { Link } from '@tanstack/react-router'

import { CreditsBadge } from './CreditsBadge'
import { NotificationBell } from './NotificationBell'

const navLinks = [{ to: '/', label: 'Dashboard' }] as const

const linkClass = 'rounded-md px-3 py-2 text-sm font-medium hover:bg-gray-100'
const activeProps = { className: 'bg-gray-100 text-gray-900' }

export function NavBar() {
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <header className="border-b border-gray-200 bg-white">
      <nav
        aria-label="Main"
        className="mx-auto flex max-w-6xl items-center gap-2 px-4 py-3"
      >
        <Link to="/" className="text-base font-semibold tracking-tight">
          Favours on Campus
        </Link>

        {/* Desktop links */}
        <div className="ml-6 hidden items-center gap-1 md:flex">
          {navLinks.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className={linkClass}
              activeProps={activeProps}
            >
              {link.label}
            </Link>
          ))}
        </div>

        {/* Credits + notifications: visible everywhere at every width */}
        <div className="ml-auto flex items-center gap-3">
          <CreditsBadge />
          <NotificationBell />

          {/* Mobile menu toggle */}
          <button
            type="button"
            className="rounded-md p-2 hover:bg-gray-100 md:hidden"
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            <svg
              className="h-5 w-5"
              viewBox="0 0 20 20"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              aria-hidden="true"
            >
              {menuOpen ? (
                <path d="M5 5l10 10M15 5L5 15" />
              ) : (
                <path d="M3 5h14M3 10h14M3 15h14" />
              )}
            </svg>
          </button>
        </div>
      </nav>

      {/* Mobile stacked links */}
      {menuOpen && (
        <div className="border-t border-gray-200 px-4 py-2 md:hidden">
          {navLinks.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className={`block ${linkClass}`}
              activeProps={activeProps}
              onClick={() => setMenuOpen(false)}
            >
              {link.label}
            </Link>
          ))}
        </div>
      )}
    </header>
  )
}
