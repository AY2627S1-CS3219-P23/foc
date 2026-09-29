// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issues #87/#89.
// Scope: registration now calls user-service's POST /auth/signup (replacing
// PR #139's user-auth server on port 8081) with the NUS email it
// requires, then sends you to log in (sign-up returns the account, not
// a token); shows the server's error message. The response (the new
// account) is not logged (PR #141 review). Second PR #141 review: calls go
// through apiFetch, so a missing VITE_USER_SERVICE_URL shows as such; no
// success log. The page itself comes from PR #139.
// 2026-09-29, Claude Code (Fable 5): restyled to the sign-up wireframe
// (web/docs/wireframes/signup.png) — centered card, stacked labels, error
// alert box, live password checklist mirroring user-service's AccountRules
// (display-only; the server still validates) — logic otherwise unchanged.
// PR #142 Copilot review: role="alert" on the error message; checklist
// moved outside the password <label> (a ul is not phrasing content and
// was polluting the field's accessible name).
// 2026-09-29, Claude Code (Fable 5), issue #112: errorMessage and the
// password checklist moved out to shared homes (lib/api/http.ts and
// PasswordChecklist.tsx) for reuse by the profile page; logic unchanged.
// Reviewed by: Ryan Ang

import React, { useState } from "react";
import { apiFetch, errorMessage } from "@/lib/api/http";
import { router } from "../../routes/index";
import { PasswordChecklist } from "./PasswordChecklist";

export function Register() {
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const register = async (event: React.SyntheticEvent) => {
    event.preventDefault()
    try {
      await apiFetch('user', '/auth/signup', {
        method: 'POST',
        body: JSON.stringify({ email, username, password }),
      })

      setError('')
      setEmail('')
      setUsername('')
      setPassword('')
      // the login page shows an "account created" notice (issue #147)
      router.navigate('/login', { state: { accountCreated: true } })
    } catch (error: unknown) {
      setError(errorMessage(error, 'Could not register. Try again.'))
    }
  }

  return (
    <div className="mx-auto mt-16 w-full max-w-sm rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 className="text-2xl font-semibold text-gray-900">Create Account</h2>
      <p className="mt-1 text-sm text-gray-500">
        Join NUS peer-to-peer errand network.
      </p>
      {error && (
        <p
          role="alert"
          className="mt-4 rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
      )}
      <form onSubmit={register} className="mt-4 space-y-4">
        <label className="block text-sm font-medium text-gray-700">
          NUS Email Address
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="e1234567@u.nus.edu"
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
          />
        </label>
        <label className="block text-sm font-medium text-gray-700">
          Username
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none"
          />
        </label>
        <div>
          <label className="block text-sm font-medium text-gray-700">
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none"
            />
          </label>
          <PasswordChecklist password={password} />
        </div>
        <button
          type="submit"
          className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800"
        >
          Sign Up
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-gray-600">
        Already have an account?{' '}
        <button
          onClick={() => router.navigate('/login')}
          className="font-medium text-gray-900 underline cursor-pointer"
        >
          Log in
        </button>
      </p>
    </div>
  )
}
