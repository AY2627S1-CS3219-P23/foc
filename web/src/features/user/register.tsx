// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issues #87/#89.
// Scope: registration now calls user-service's POST /auth/signup (replacing
// PR #139's user-auth server on port 8081) with the NUS email it
// requires, then sends you to log in (sign-up returns the account, not
// a token); shows the server's error message. The response (the new
// account) is not logged (PR #141 review). The page itself comes from PR #139.
// Reviewed by: Ryan Ang

import React, { useState } from "react";
import axios from "axios";
import { router } from "../../routes/index";
import { serviceBaseUrls } from "@/lib/api/config";

export function Register() {
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const register = async (event: React.SyntheticEvent) => {
    event.preventDefault();
    try {
      await axios.post(`${serviceBaseUrls.user}/auth/signup`, {
        email,
        username,
        password,
      });

      setError("");
      setEmail("");
      setUsername("");
      setPassword("");
      router.navigate("/login");
      console.log("Registration successful");
    } catch (error: unknown) {
      // user-service replies with problem+json; show its exact reason
      if (axios.isAxiosError(error) && error.response?.data?.detail) {
        setError(error.response.data.detail);
      } else if (error instanceof Error) {
        setError("Could not register. Try again.");
        console.error(error.message); 
      } else {
        console.error("An unexpected error occurred:", error);
      }
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

