import React, { useState } from "react";
import axios from "axios";
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
        const response = await axios.post("http://localhost:8081/api/auth/login", {
        username,
        password,
      });

      console.log("response", response);
      await data.login(response.data);
      setError("");
      setUsername("");
      setPassword("");
      router.navigate("/");
      console.log("login successful");
    } catch (error: unknown) {
      if (error instanceof Error) {
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