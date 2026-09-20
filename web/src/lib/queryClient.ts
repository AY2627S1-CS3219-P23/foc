// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-20, issue #108.
// Scope: the single shared TanStack Query client.
// Reviewed by: Leong Wei Zhi (via pull request).

import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient()
