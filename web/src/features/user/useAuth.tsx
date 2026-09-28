import { createContext, useContext } from "react";
import type { JWTPayload } from "jose";

export interface AuthData {
  token: JWTPayload;
  login(token: JWTPayload): Promise<void>;
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