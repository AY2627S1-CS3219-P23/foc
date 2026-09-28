import { useEffect, useState } from "react";
import { useNavigate, Outlet } from "react-router";
import { useAuth } from "./useAuth";

// Only blocks on client side, still need server side protection for API calls
export const ProtectedRoute = () => {
  const navigate = useNavigate();
  const data = useAuth();
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!data?.token) {
      // user is not authenticated
      navigate("/login");
    } else {
      // user is authenticated
      setIsLoading(false);
    }
  });
  
  if (isLoading) {
    return <div>Loading...</div>;
  }
  return <Outlet/>;
};