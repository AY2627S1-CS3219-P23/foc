// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: passwordRules + PasswordChecklist moved verbatim out of
// register.tsx (PR #142) so the profile page's Change Password card can
// reuse them.
// Reviewed by: [pending]

// Mirrors user-service's AccountRules password policy (PASSWORD_PATTERN,
// PASSWORD_MIN/MAX). Display-only: the server remains the validator.
const passwordRules = [
  {
    label: 'Contains uppercase and lowercase',
    met: (p: string) => /[a-z]/.test(p) && /[A-Z]/.test(p),
  },
  {
    label: 'Contains numbers',
    met: (p: string) => /\d/.test(p),
  },
  {
    label: 'Length between 10 and 50 characters',
    met: (p: string) => p.length >= 10 && p.length <= 50,
  },
]

export function PasswordChecklist({ password }: { password: string }) {
  return (
    <ul className="mt-2 space-y-0.5 text-xs font-normal">
      {passwordRules.map(({ label, met }) => {
        const ok = met(password)
        return (
          <li key={label} className={ok ? 'text-green-600' : 'text-red-600'}>
            {ok ? '✓' : '✗'} {label}
          </li>
        )
      })}
    </ul>
  )
}
