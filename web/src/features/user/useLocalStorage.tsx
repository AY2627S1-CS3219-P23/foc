import { useState } from "react";
import type { JWTPayload } from "jose";

export const useLocalStorage = <T,>(
  keyName: string,
  defaultValue = null as T
): [JWTPayload, (value: T) => void] => {
  const [storedValue, setStoredValue] = useState(() => {
    try {
      const value = window.localStorage.getItem(keyName);
      if (value) {
        return JSON.parse(value);
      } else {
        window.localStorage.setItem(
          keyName,
          JSON.stringify(defaultValue)
        );
        return defaultValue;
      }
    } catch {
      return defaultValue;
    }
  });

  const setValue = (newValue: T) => {
    try {
      window.localStorage.setItem(keyName, JSON.stringify(newValue));
    } catch (err) {
      console.log(err);
    }
    setStoredValue(newValue);
  };

  return [storedValue, setValue];
};