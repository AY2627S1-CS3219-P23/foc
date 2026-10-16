// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: the one credit-service read the order pages need — the signed-in
// user's balance (GET /credits/me, credit-service's CreditController) —
// so the create-request form can warn about insufficient credits. Under
// VITE_MOCK_ORDER_API=true (dev server only) the balance comes from the
// order mock, which moves credits as orders are created/cancelled/settled.
// Author review: [pending]

import { apiFetch } from '@/lib/api/http'
import type { CreditBalance } from '@/features/order/types'

export function getBalance(): Promise<CreditBalance> {
  if (import.meta.env.DEV && import.meta.env.VITE_MOCK_ORDER_API === 'true') {
    return import('@/features/order/orderApiMock').then((m) =>
      m.mockOrderApi.getBalance(),
    )
  }
  return apiFetch<CreditBalance>('credit', '/credits/me')
}
