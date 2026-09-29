// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, PR #142 (JWT wiring into
// apiFetch). Scope: token is the stored LoginResponse session (or null
// when logged out) instead of jose's JWTPayload, which login() was
// typed as but never actually received. The hook itself comes from
// PR #139.
// Reviewed by: Leong Wei Zhi (via pull request).

import { createContext, useContext } from "react";
import type { LoginResponse } from "./types";

export interface AuthData {
  token: LoginResponse | null;
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
