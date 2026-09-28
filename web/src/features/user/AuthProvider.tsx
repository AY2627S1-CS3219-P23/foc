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
      navigate("/home");
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