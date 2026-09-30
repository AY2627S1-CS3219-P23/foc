// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5), 2026-09-30, issue #109 (PR #150).
// Scope: the six-box one-time-code field from the OTP overlay in
// docs/wireframes/signup.png. Six boxes rather than one six-character
// input was Leong Wei Zhi's call via options Q&A (wireframe fidelity, and
// the later OTP flows #92/#94 reuse it); it lives in features/user/ rather
// than shared/components/ because shared/ is for cross-domain primitives
// (see shared/components/README.md) and every flow that needs a code is
// user-domain — move it there if a non-user feature ever wants one.
// Controlled by a single string so the owning screen keeps the value.
// Author review: Leong Wei Zhi to review via the PR.

import { useRef } from 'react'

interface CodeInputProps {
  value: string
  onChange: (value: string) => void
  disabled?: boolean
  length?: number
  autoFocus?: boolean
  // names the group of boxes for screen readers and for tests
  label: string
}

const digitsOnly = (text: string) => text.replace(/\D/g, '')

export function CodeInput({
  value,
  onChange,
  disabled = false,
  length = 6,
  autoFocus = false,
  label,
}: CodeInputProps) {
  const boxes = useRef<(HTMLInputElement | null)[]>([])

  const focus = (index: number) => {
    if (index >= 0 && index < length) boxes.current[index]?.focus()
  }

  // Replace one position, keeping the value a plain string of digits.
  // Boxes fill left to right, so a gap can't be typed; if one is somehow
  // reached, the digits close up rather than leaving a hole in the value.
  const setDigit = (index: number, digit: string) => {
    const padded = value.padEnd(length, ' ').split('')
    padded[index] = digit || ' '
    onChange(padded.join('').replace(/ /g, '').slice(0, length))
  }

  const handleChange = (index: number, raw: string) => {
    // a browser autofilling the whole code into one box arrives as
    // several digits (maxLength only stops typing): spread it from here on
    const digits = digitsOnly(raw)
    if (digits.length > 1) {
      onChange((value.slice(0, index) + digits).slice(0, length))
      focus(Math.min(index + digits.length, length - 1))
      return
    }
    setDigit(index, digits)
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
      setDigit(index - 1, '')
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
    const next = (value.slice(0, index) + digits).slice(0, length)
    onChange(next)
    focus(Math.min(next.length, length - 1))
  }

  return (
    <div role="group" aria-label={label} className="flex justify-center gap-2">
      {Array.from({ length }, (_, index) => (
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
          value={value[index] ?? ''}
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
