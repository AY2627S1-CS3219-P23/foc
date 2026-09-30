// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5), 2026-09-30, issue #109 (PR #150).
// Scope: the six-box one-time-code field from the OTP overlay in
// docs/wireframes/signup.png. Six boxes rather than one six-character
// input was Leong Wei Zhi's call via options Q&A (wireframe fidelity, and
// the later OTP flows #92/#94 reuse it); it lives in features/user/ rather
// than shared/components/ because shared/ is for cross-domain primitives
// (see shared/components/README.md) and every flow that needs a code is
// user-domain — move it there if a non-user feature ever wants one.
// PR #150 review (@Sinnez1): the value is one entry per box rather than a
// string of the digits typed so far. Collapsing it to a string made
// clearing a middle box slide every later digit one box left, so a code
// could not be corrected in place and Verify stayed disabled at five
// digits.
// Author review: Leong Wei Zhi to review via the PR.

import { useRef } from 'react'

interface CodeInputProps {
  // one entry per box, '' for an empty one; its length is the box count,
  // so the boxes and the value can never disagree
  value: string[]
  onChange: (value: string[]) => void
  disabled?: boolean
  autoFocus?: boolean
  // names the group of boxes for screen readers and for tests
  label: string
}

const digitsOnly = (text: string) => text.replace(/\D/g, '')

export function CodeInput({
  value,
  onChange,
  disabled = false,
  autoFocus = false,
  label,
}: CodeInputProps) {
  const length = value.length
  const boxes = useRef<(HTMLInputElement | null)[]>([])

  const focus = (index: number) => {
    if (index >= 0 && index < length) boxes.current[index]?.focus()
  }

  // Positional: box n is value[n] whether or not its neighbours are
  // filled, so editing one box never disturbs another.
  const setAt = (index: number, digit: string) => {
    const next = [...value]
    next[index] = digit
    onChange(next)
  }

  // Lay several digits out from `index` — a paste, or a browser
  // autofilling the whole code into one box.
  const spreadFrom = (index: number, digits: string) => {
    const next = [...value]
    for (let i = 0; i < digits.length && index + i < length; i++) {
      next[index + i] = digits[i]
    }
    onChange(next)
    focus(Math.min(index + digits.length, length - 1))
  }

  const handleChange = (index: number, raw: string) => {
    const digits = digitsOnly(raw)
    if (digits.length > 1) {
      spreadFrom(index, digits)
      return
    }
    // '' here is the browser deleting the character in a filled box: the
    // box empties in place and the rest of the code stays where it is
    setAt(index, digits)
    if (digits) focus(index + 1)
  }

  const handleKeyDown = (
    index: number,
    event: React.KeyboardEvent<HTMLInputElement>,
  ) => {
    if (event.key === 'Backspace' && !value[index]) {
      // an empty box sends the backspace to the one before it, which is
      // what people expect when correcting a code
      event.preventDefault()
      setAt(index - 1, '')
      focus(index - 1)
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault()
      focus(index - 1)
    } else if (event.key === 'ArrowRight') {
      event.preventDefault()
      focus(index + 1)
    }
  }

  const handlePaste = (
    index: number,
    event: React.ClipboardEvent<HTMLInputElement>,
  ) => {
    const digits = digitsOnly(event.clipboardData.getData('text'))
    if (!digits) return
    event.preventDefault()
    spreadFrom(index, digits.slice(0, length - index))
  }

  return (
    <div role="group" aria-label={label} className="flex justify-center gap-2">
      {value.map((digit, index) => (
        <input
          key={index}
          ref={(element) => {
            boxes.current[index] = element
          }}
          type="text"
          inputMode="numeric"
          maxLength={1}
          // one-time-code belongs on a single box only, or autofill has
          // no unambiguous target
          autoComplete={index === 0 ? 'one-time-code' : 'off'}
          autoFocus={autoFocus && index === 0}
          disabled={disabled}
          value={digit}
          aria-label={`Digit ${index + 1} of ${length}`}
          // selecting on focus lets a digit be typed straight over a
          // filled box, which maxLength would otherwise block
          onFocus={(e) => e.currentTarget.select()}
          onChange={(e) => handleChange(index, e.target.value)}
          onKeyDown={(e) => handleKeyDown(index, e)}
          onPaste={(e) => handlePaste(index, e)}
          className="h-12 w-10 rounded-md border border-gray-200 text-center text-xl font-normal focus:border-gray-400 focus:outline-none disabled:opacity-50"
        />
      ))}
    </div>
  )
}
