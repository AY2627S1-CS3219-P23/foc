// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issues #87/#89.
// Scope: registration now calls user-service's POST /auth/signup (replacing
// PR #139's user-auth server on port 8081) with the NUS email it
// requires, then sends you to log in (sign-up returns the account, not
// a token); shows the server's error message. The response (the new
// account) is not logged (PR #141 review). Second PR #141 review: calls go
// through apiFetch, so a missing VITE_USER_SERVICE_URL shows as such; no
// success log. The page itself comes from PR #139.
// Reviewed by: Ryan Ang

import React, { useState } from "react";
import { ApiError, apiFetch } from "@/lib/api/http";
import { router } from "../../routes/index";

// ApiError carries user-service's problem+json reason, and apiFetch's own
// Error names a missing VITE_USER_SERVICE_URL; a TypeError is the network
function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error && !(error instanceof TypeError)) return error.message;
  return fallback;
}

export function Register() {
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const register = async (event: React.SyntheticEvent) => {
    event.preventDefault();
    try {
      await apiFetch("user", "/auth/signup", {
        method: "POST",
        body: JSON.stringify({ email, username, password }),
      });

      setError("");
      setEmail("");
      setUsername("");
      setPassword("");
      router.navigate("/login");
    } catch (error: unknown) {
      setError(errorMessage(error, "Could not register. Try again."));
    }
  };

  return (
    <div className="mt-[100px] text-center">
      <h2 style={styles.heading}>User Registration</h2>
      {error && <p style={styles.error}>{error}</p>}
      <form onSubmit={register} className="flex flex-col items-center">
        <label className ="mb-[10px] text-left">
          NUS email:
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="e1234567@u.nus.edu"
            className = "ml-[10px] w-[200px] p-[5px] bg-white border border-gray-300"
          />
        </label>
        <br />
        <label className ="mb-[10px] text-left">
          Username:
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
          Register
        </button>
      </form>
      <p>
        Already have an account?{" "}
        <button onClick={() => router.navigate("/login")} className="text-[#007bff] no-underline cursor-pointer">
          Login
        </button>
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

