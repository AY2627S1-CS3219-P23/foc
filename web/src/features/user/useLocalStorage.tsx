// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, PR #142 (JWT wiring into
// apiFetch). Scope: the return type is now the stored T | null instead
// of jose's JWTPayload, which the hook never actually produced; storage
// behaviour unchanged. The hook itself comes from PR #139.
// Reviewed by: Leong Wei Zhi (via pull request).

import { useState } from "react";

export const useLocalStorage = <T,>(
  keyName: string,
  defaultValue: T | null = null
): [T | null, (value: T | null) => void] => {
  const [storedValue, setStoredValue] = useState<T | null>(() => {
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

  const setValue = (newValue: T | null) => {
    try {
      window.localStorage.setItem(keyName, JSON.stringify(newValue));
    } catch (err) {
      console.log(err);
    }
    setStoredValue(newValue);
  };

  return [storedValue, setValue];
};
