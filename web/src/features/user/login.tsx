// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issues #87/#89.
// Scope: login now calls user-service's POST /auth/login (replacing
// PR #139's user-auth server on port 8081) with usernameOrEmail, and
// shows the server's error message; the response is no longer logged,
// as it now holds the access token (PR #141 review). The page itself
// comes from PR #139.
// Reviewed by: Ryan Ang

import React, { useState } from "react";
import axios from "axios";
import { serviceBaseUrls } from "@/lib/api/config";
import { router } from "../../routes/index";
import { useAuth } from "./useAuth";

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
      const response = await axios.post(`${serviceBaseUrls.user}/auth/login`, {
        usernameOrEmail: username,
        password,
      });

      await data.login(response.data);
      setError("");
      setUsername("");
      setPassword("");
      router.navigate("/");
      console.log("login successful");
    } catch (error: unknown) {
      // user-service replies with problem+json; show its reason
      if (axios.isAxiosError(error) && error.response?.data?.detail) {
        setError(error.response.data.detail);
      } else if (error instanceof Error) {
        setError("Could not log in. Try again.");
        console.error(error.message); 
      } else {
        console.error("An unexpected error occurred:", error);
      }
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