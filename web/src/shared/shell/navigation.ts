// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: the shell's five nav destinations (per web/docs/wireframes/),
// shared by the desktop nav bar and the mobile tab bar.
// Reviewed by: Leong Wei Zhi (via pull request).

// SVG path data (24×24 viewBox, stroked) for the mobile tab icons.
export interface NavItem {
  to: string
  label: string
  iconPaths: readonly string[]
}

export const navItems: readonly NavItem[] = [
  {
    to: '/',
    label: 'Home',
    iconPaths: ['M3 10.5 12 3l9 7.5', 'M5 9v12h14V9'],
  },
  {
    to: '/suppliers',
    label: 'Suppliers',
    iconPaths: ['M6 7h12l1 14H5L6 7Z', 'M9 10V7a3 3 0 0 1 6 0v3'],
  },
  {
    to: '/my-requests',
    label: 'My Requests',
    iconPaths: [
      'M8.5 6h12M8.5 12h12M8.5 18h12',
      'M3.5 6h.01M3.5 12h.01M3.5 18h.01',
    ],
  },
  {
    to: '/requests',
    label: 'Requests',
    iconPaths: ['M9 4h6v3H9z', 'M15 5h4v16H5V5h4', 'M9 12h6M9 16h4'],
  },
  {
    to: '/profile',
    label: 'Profile',
    iconPaths: [
      'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
      'M4.5 21a7.5 7.5 0 0 1 15 0',
    ],
  },
]
