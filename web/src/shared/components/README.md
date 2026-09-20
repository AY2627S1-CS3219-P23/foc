<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-20, issue #108.
  Scope: convention note for shared UI primitives.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# `src/shared/components/` — common UI primitives

Buttons, form fields, modals, status chips, empty states, and other
cross-domain primitives live here (not per-feature copies).

Per `web/AGENTS.md`: whoever needs a shared component first builds it
here; cross-domain changes are flagged to the affected owner in the PR
rather than silently edited.
