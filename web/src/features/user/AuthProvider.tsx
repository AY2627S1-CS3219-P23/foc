// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, PR #141 review.
// Scope: login navigates to the home page ("/") instead of "/home", which
// isn't a route, and is the only navigation after login. The component
// itself comes from PR #139.
// Author review: Ryan to review via the PR.

import { useMemo } from "react";
import { useNavigate, Outlet } from "react-router";
import { AuthContext } from "./useAuth";
import { useLocalStorage } from "./useLocalStorage";
import type { JWTPayload } from "jose";

export const AuthProvider = () => {
  const [token, setToken] = useLocalStorage("user");
  const navigate = useNavigate();

  const value = useMemo(() => {

    // call this function to set login values
    const login = async (token: JWTPayload) => {
      setToken(token);
      navigate("/");
    };

    // call this function to sign out logged in user
    const logout = () => {
      setToken(null);
      navigate("/", { replace: true });
    };

    return { token, login, logout };
  }, [token, navigate, setToken]);

  return (
    <AuthContext.Provider value={value}>
      <Outlet/>
    </AuthContext.Provider>
  );
};