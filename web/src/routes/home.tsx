import { Outlet } from "react-router-dom";
import {router} from "./index";

export function Home() {
  const isLoginTrue = localStorage.getItem("user");
  
  const toLogin = () => {
    router.navigate("/login");
  };

  const toRegister = () => {
    router.navigate("/register");
  };

  const userNotLogin = () => (
    <div>
      <h2 className="text-xl font-semibold text-gray-900">
        Welcome!</h2>
      <p className="mt-2 text-sm text-gray-600">
        It seems like you are not logged in. If you have an account, please{" "}
        <button onClick={toLogin} className="text-[#007bff] no-underline cursor-pointer">
          Log In
        </button>
        . Don't have an account yet?{" "}
        <button onClick={toRegister} className="text-[#007bff] no-underline cursor-pointer">
          Register
        </button>
      </p>
    </div>
  );

  const userLoggedIn = () => (
    <div>
      <h2 className="text-xl font-semibold text-gray-900">Welcome Back!</h2>
      <Outlet />
    </div>
  );

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      {isLoginTrue == "undefined" || isLoginTrue == "null" ? (
        <>{userNotLogin()}</>
      ) : (
        <>{userLoggedIn()}</>
      )}
    </div>
  );
};

export default Home;