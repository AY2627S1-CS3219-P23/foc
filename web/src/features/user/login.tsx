// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issues #87/#89.
// Scope: login now calls user-service's POST /auth/login (replacing
// PR #139's user-auth server on port 8081) with usernameOrEmail, and
// shows the server's error message; the response is no longer logged,
// as it now holds the access token (PR #141 review). Second PR #141
// review: calls go through apiFetch, so a missing VITE_USER_SERVICE_URL
// shows as such; AuthProvider does the one navigation after login; no
// success log. The page itself comes from PR #139.
// 2026-09-29, Claude Code (Fable 5): restyled to the login wireframe
// (web/docs/wireframes/login.png) — centered card, stacked labels,
// error alert box — using the app's Tailwind conventions; logic unchanged.
// PR #142 Copilot review: role="alert" on the error message so screen
// readers announce failed logins.
// 2026-09-29, Claude Code (Opus 5.5), issue #147: errorMessage now comes
// from lib/api/http; Prettier formatting.
// Reviewed by: Ryan Ang

import React, { useState } from 'react'
import { apiFetch, errorMessage } from '@/lib/api/http'
import { router } from '../../routes/index'
import { useAuth } from './useAuth'
import type { LoginResponse } from './types'

export function Login() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const data = useAuth()

  const toRegister = () => {
    router.navigate('/register')
  }

  const login = async (event: React.SyntheticEvent) => {
    event.preventDefault()
    try {
      const response = await apiFetch<LoginResponse>('user', '/auth/login', {
        method: 'POST',
        body: JSON.stringify({ usernameOrEmail: username, password }),
      })

      setError('')
      setUsername('')
      setPassword('')
      // navigates to the home page
      await data.login(response)
    } catch (error: unknown) {
      setError(errorMessage(error, 'Could not log in. Try again.'))
    }
  }

  return (
    <div className="mx-auto mt-16 w-full max-w-sm rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 className="text-2xl font-semibold text-gray-900">Log In</h2>
      <p className="mt-1 text-sm text-gray-500">
        Access your student errand portal.
      </p>
      {error && (
        <p
          role="alert"
          className="mt-4 rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
      )}
      <form onSubmit={login} className="mt-4 space-y-4">
        <label className="block text-sm font-medium text-gray-700">
          Username or Email
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Enter NUS email or username"
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
          />
        </label>
        <label className="block text-sm font-medium text-gray-700">
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none"
          />
        </label>
        <button
          type="submit"
          className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800"
        >
          Log In
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-gray-600">
        Don't have an account?{' '}
        <button
          onClick={toRegister}
          className="font-medium text-gray-900 underline cursor-pointer"
        >
          Sign up
        </button>
      </p>
    </div>
  )
}
