// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issues #87/#89.
// Scope: login now calls user-service's POST /auth/login (replacing
// PR #139's user-auth server on port 8081) with usernameOrEmail, and
// shows the server's error message; the response is no longer logged,
// as it now holds the access token (PR #141 review). Second PR #141
// review: calls go through apiFetch, so a missing VITE_USER_SERVICE_URL
// shows as such; AuthProvider does the one navigation after login; no
// success log. The page itself comes from PR #139.
// Reviewed by: Ryan Ang

import React, { useState } from "react";
import { ApiError, apiFetch } from "@/lib/api/http";
import { router } from "../../routes/index";
import { useAuth } from "./useAuth";

// POST /auth/login's body
type LoginResponse = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
};

// ApiError carries user-service's problem+json reason, and apiFetch's own
// Error names a missing VITE_USER_SERVICE_URL; a TypeError is the network
function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error && !(error instanceof TypeError)) return error.message;
  return fallback;
}

export function Login() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const data = useAuth();

  const toRegister = () => {
    router.navigate("/register");
  };


  const login = async (event: React.SyntheticEvent) => {
    event.preventDefault();
    try {
      const response = await apiFetch<LoginResponse>("user", "/auth/login", {
        method: "POST",
        body: JSON.stringify({ usernameOrEmail: username, password }),
      });

      setError("");
      setUsername("");
      setPassword("");
      // navigates to the home page
      await data.login(response);
    } catch (error: unknown) {
      setError(errorMessage(error, "Could not log in. Try again."));
    }
  };

  return (
    <div className="mt-[100px] text-center">
      <h2 style={styles.heading}>Login Page</h2>
      {error && <p style={styles.error}>{error}</p>}
      <form onSubmit={login} className="flex flex-col items-center">
        <label className ="mb-[10px] text-left">
          Username or email:
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className = "ml-[10px] w-[200px] p-[5px] bg-white border border-gray-300"
          />
        </label>
        <br />
        <label className ="mb-[10px] text-left">
          Password:
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className = "ml-[10px] w-[200px] p-[5px] bg-white border border-gray-300"
          />
        </label>
        <br />
        <button style={styles.button} type="submit">
          Login
        </button>
      </form>
      <p>
        Don't have an account?
        <button onClick={toRegister} className="text-[#007bff] no-underline cursor-pointer">
          Register
        </button>.
      </p>
    </div>
  );
};

const styles = {
  heading: {
    fontSize: "24px",
    color: "#333",
  },
  error: {
    color: "red",
  },
  input: {
    width: "200px",
    padding: "5px",
  },
  button: {
    width: "100px",
    padding: "10px",
    backgroundColor: "#007BFF",
    color: "white",
    border: "none",
    cursor: "pointer",
  }
};

/*
export const Login = () => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const data = useAuth();

  const handleLogin = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    // Here you would usually send a request to your backend to authenticate the user
    // For the sake of this example, we're using a mock authentication
    if (username === "user" && password === "password" && data?.login) {
      // Replace with actual authentication logic
      await data.login(username);
    } else {
      alert("Invalid username or password");
    }
  };

  return (
    <div>
      <form onSubmit={handleLogin}>
        <div>
          <label htmlFor='username'>Username:</label>
          <input
            id='username'
            type='text'
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </div>
        <div>
          <label htmlFor='password'>Password:</label>
          <input
            id='password'
            type='password'
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        <button type='submit'>Login</button>
      </form>
    </div>
  );
};*/