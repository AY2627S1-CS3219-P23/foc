import React, { useState } from "react";
import axios from "axios";
import { router } from "../../routes/index";
import { useAuth } from "./useAuth";

export function Register() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const data = useAuth();

  const register = async (event: React.SyntheticEvent) => {
    event.preventDefault();
    try {
      const response = await axios.post("http://localhost:8081/api/auth/register", {
        username,
        password,
      });

      console.log("response", response);
      await data.login(response.data);
      setError("");
      setUsername("");
      setPassword("");
      router.navigate("/");
      console.log("Registration successful");
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
      <h2 style={styles.heading}>User Registration</h2>
      {error && <p style={styles.error}>{error}</p>}
      <form onSubmit={register} className="flex flex-col items-center">
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

