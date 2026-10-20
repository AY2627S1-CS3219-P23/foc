// AI-assisted (CS3219 AI Usage Policy disclosure):
// Copied from register.tsx which was AI-assisted

import React, { useState } from 'react'
import { apiFetch, errorMessage } from '@/lib/api/http'
import { router } from '../../routes/index'
import { OwnerOtpVerificationModal } from './components/OwnerOtpVerificationModal'
import { PasswordChecklist } from './PasswordChecklist'
import type { SignupAccepted, OwnerSignupPending } from './types'

export function RegisterOwner() {
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [ownerKey, setOwnerKey] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // set once a code is on its way: the body that was sent, plus when that
  // code dies and when it may be resent. A snapshot rather than a read of
  // the live fields, so a resend repeats the identical request even if the
  // form is edited behind the dialog — anything else would be refused 409
  const [pending, setPending] = useState<OwnerSignupPending | null>(null)
  // dismissing the dialog hides it but keeps the pending sign-up, so the
  // code already emailed can still be entered — closing it used to strand
  // that code behind a cooldown and a pointless resend (PR #150 review)
  const [codeOpen, setCodeOpen] = useState(false)

  const register = async (event: React.SyntheticEvent) => {
    event.preventDefault()
    setSubmitting(true)
    try {
      const accepted = await apiFetch<SignupAccepted>(
        'user',
        '/auth/signup-owner',
        {
          method: 'POST',
          body: JSON.stringify({ email, username, password, ownerKey }),
        },
      )

      setError('')
      // no account exists yet, and nothing is cleared: the dialog takes
      // over from here, and its verify call is what creates the account
      setPending({
        email,
        username,
        password,
        ownerKey,
        expiresAt: Date.now() + accepted.expiresInSeconds * 1000,
        resendAt: Date.now() + accepted.resendInSeconds * 1000,
      })
      setCodeOpen(true)
    } catch (error: unknown) {
      setError(errorMessage(error, 'Could not register. Try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  // the account exists now, so finish where sign-up always finished
  const accountCreated = () => {
    setPending(null)
    setCodeOpen(false)
    setEmail('')
    setUsername('')
    setPassword('')
    setOwnerKey('')
    // the login page shows an "account created" notice (issue #147)
    router.navigate('/login', { state: { accountCreated: true } })
  }

  return (
    <div className="mx-auto mt-16 w-full max-w-sm rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 className="text-2xl font-semibold text-gray-900">
        Create Owner Account
      </h2>
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
        <label className="block text-sm font-medium text-gray-700">
          Setup token
          <input
            type="password"
            value={ownerKey}
            onChange={(e) => setOwnerKey(e.target.value)}
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none"
          />
        </label>
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
        >
          {submitting ? 'Sending code...' : 'Sign Up'}
        </button>
      </form>
      {pending && !codeOpen && (
        <p className="mt-4 text-center text-sm text-gray-600">
          We emailed a code to {pending.email}.{' '}
          <button
            type="button"
            onClick={() => setCodeOpen(true)}
            className="font-medium text-gray-900 underline cursor-pointer"
          >
            Enter your code
          </button>
        </p>
      )}
      <p className="mt-4 text-center text-sm text-gray-600">
        Already have an account?{' '}
        <button
          onClick={() => router.navigate('/login')}
          className="font-medium text-gray-900 underline cursor-pointer"
        >
          Log in
        </button>
      </p>
      <p className="mt-4 text-center text-sm text-gray-600">
        <button
          onClick={() => router.navigate('/register')}
          className="font-medium text-gray-900 underline cursor-pointer"
        >
          Register for users
        </button>
      </p>
      {/* outside the <form>: the dialog holds a form of its own, and forms
          may not nest */}
      {pending && codeOpen && (
        <OwnerOtpVerificationModal
          pending={pending}
          // hidden, not discarded: "Enter your code" brings it back
          onClose={() => setCodeOpen(false)}
          onVerified={accountCreated}
          onResent={setPending}
          onRestart={(message) => {
            // the pending sign-up is dead (expired, or too many wrong
            // codes): back to the still-filled form, one click from a
            // fresh code
            setPending(null)
            setCodeOpen(false)
            setError(message)
          }}
        />
      )}
    </div>
  )
}
