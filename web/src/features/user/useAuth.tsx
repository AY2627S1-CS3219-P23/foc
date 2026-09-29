// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, PR #142 (JWT wiring into
// apiFetch). Scope: token is the stored LoginResponse session (or null
// when logged out) instead of jose's JWTPayload, which login() was
// typed as but never actually received. The hook itself comes from
// PR #139.
// 2026-09-29, Claude Code (Sonnet 5): added `role`, decoded from the
// token by AuthProvider — lets pages hide admin-only controls (e.g.
// routes/suppliers.tsx's IS_ADMIN) for the caller's actual role.
// Reviewed by: Leong Wei Zhi (via pull request).

import { createContext, useContext } from "react";
import type { LoginResponse, UserRole } from "./types";

export interface AuthData {
  token: LoginResponse | null;
  role: UserRole | null;
  login(session: LoginResponse): Promise<void>;
  logout(): void;
}

export const AuthContext = createContext<AuthData | null>(null);

export const useAuth = () => {
  const context = useContext(AuthContext);

  if (context === null) {
    throw new Error('useAuth must be used within an AuthProvider');
  }

  return context;
};
