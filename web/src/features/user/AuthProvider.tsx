// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, PR #141 review.
// Scope: login navigates to the home page ("/") instead of "/home", which
// isn't a route, and is the only navigation after login. The component
// itself comes from PR #139.
// 2026-09-29, Claude Code (Fable 5), PR #142: the stored session's
// accessToken is now fed to apiFetch via setTokenSource, so every API
// call carries the Authorization header (getToken was hardcoded to
// null); the session is typed as the LoginResponse it actually is.
// 2026-09-29, Claude Code (Sonnet 5): exposes `role`, decoded from the
// token's claims (display-only — see jwt.ts), so pages can hide
// admin-only controls for non-admin callers instead of showing them
// and letting the server's role gate (supplier-service #106) reject
// the action after the fact.
// Reviewed by: Ryan Ang

import { useLayoutEffect, useMemo } from "react";
import { useNavigate, Outlet } from "react-router";
import { AuthContext } from "./useAuth";
import { useLocalStorage } from "./useLocalStorage";
import { setTokenSource } from "@/lib/api/http";
import { decodeJwtRole } from "./jwt";
import type { LoginResponse } from "./types";

export const AuthProvider = () => {
  const [token, setToken] = useLocalStorage<LoginResponse>("user");
  const navigate = useNavigate();

  // apiFetch's swappable token source; refreshed whenever the session
  // changes so requests carry the current token (or none after logout).
  // Layout effect, not useEffect: passive effects run children-first,
  // so a page's mount-time fetch would fire before this provider's
  // effect set the source; layout effects all run before any of them.
  useLayoutEffect(() => {
    setTokenSource(() => token?.accessToken ?? null);
  }, [token]);

  const role = useMemo(() => (token ? decodeJwtRole(token.accessToken) : null), [token]);

  const value = useMemo(() => {

    // call this function to set login values
    const login = async (session: LoginResponse) => {
      setToken(session);
      navigate("/");
    };

    // call this function to sign out logged in user
    const logout = () => {
      setToken(null);
      navigate("/", { replace: true });
    };

    return { token, role, login, logout };
  }, [token, role, navigate, setToken]);

  return (
    <AuthContext.Provider value={value}>
      <Outlet/>
    </AuthContext.Provider>
  );
};
