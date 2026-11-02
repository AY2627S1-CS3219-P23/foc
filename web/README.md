<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
  Scope: this README, written alongside the SPA scaffold it documents.
  Stack, tooling, scope, and port decisions were made by the author
  (Leong Wei Zhi) via neutral-options Q&A; see ai/usage-log.md.
  Reviewed by: Leong Wei Zhi (via pull request).
  2026-11-02, Claude Code (Fable 5), issue #258: the Structure tree
  updated to the routes and feature folders actually on main, and the
  Environment section's pre-gateway "calls each backend service
  directly" line corrected — every call goes through the API gateway
  since the issue #151 cutover. Transcription only.
  Author review: Leong Wei Zhi, pending pull request review.
  2026-11-02, Claude Code (Fable 5), PR #285 review (@Sinnez1): the
  shared/auth line corrected — the folder holds only a README; the
  session/JWT code lives in features/user. Transcription only.
  Author review: Leong Wei Zhi, pending pull request review.
-->

# web/ — FoC Frontend

The single React SPA for Favours on Campus. See [`AGENTS.md`](AGENTS.md)
for the screen/domain conventions and wireframes.

**Stack:** React 19 · TypeScript 6 · Vite 8 · react-router v8 ·
Tailwind CSS v4 · Vitest 5 + Testing Library · ESLint 10 + Prettier.

The scaffold is deliberately minimal: routing is a plain route table
(no codegen), and there is **no data-fetching library** — only the
shared `apiFetch` wrapper. Each domain owner adds the tools they want
for their own pages.

## Prerequisites

Node 22+ (`.nvmrc` is set — `nvm use`).

## Commands

| Command                | What it does                        |
| ---------------------- | ----------------------------------- |
| `npm install`          | install dependencies                |
| `npm run dev`          | dev server on http://localhost:5173 |
| `npm run build`        | type-check + production build       |
| `npm run preview`      | serve the production build locally  |
| `npm run lint`         | ESLint                              |
| `npm run format`       | Prettier (write)                    |
| `npm run format:check` | Prettier (check only)               |
| `npm run test`         | Vitest, single run                  |
| `npm run test:watch`   | Vitest, watch mode                  |

## Structure

```
src/
├── routes/       # one file per page; index.tsx is the route table
│   ├── index.tsx           # mounts the shared shell; 404 handling
│   ├── home.tsx            # "/" landing page
│   ├── profile.tsx         # own profile + preferences
│   ├── public-profile.tsx  # /users/:id — ratings block, report user
│   ├── suppliers.tsx
│   ├── create-request.tsx
│   ├── my-requests.tsx
│   ├── open-requests.tsx
│   ├── active-delivery.tsx
│   ├── request-history.tsx # incl. the Rate Your Experience entry
│   ├── credits.tsx
│   ├── notifications.tsx
│   └── admin.tsx
├── shared/
│   ├── shell/        # app shell: top bar, mobile tab bar, credits +
│   │                 # notification slots, 404 view
│   ├── components/   # common UI primitives (cross-domain)
│   └── auth/         # placeholder README only — session/JWT code
│                     # lives in features/user/ (AuthProvider,
│                     # ProtectedRoute, jwt.ts)
├── features/     # per-domain code: user/, supplier/, order/, credit/,
│                 # notifications/, rating/ — see src/features/README.md
├── lib/          # API base URLs + shared fetch wrapper
└── test/         # Vitest setup + per-page/per-feature tests
```

Adding a page: create a file under `src/routes/` and add one child
entry to the route table in `src/routes/index.tsx`. Route files stay
thin and import from `features/<domain>/`; REST calls go through
`lib/api/http.ts` (`apiFetch`); shared pieces go in `shared/` — see
`src/features/README.md` and `web/AGENTS.md`.

## Environment

The SPA reaches every backend through the API gateway (issue #151,
`docs/gateway.md`): each `VITE_*_SERVICE_URL` points at the gateway's
`/api/<service>` prefix — see the "Web frontend" section of the root
`.env.example`. **Vite inlines them at build time**: for the
container, compose passes them as build args, so changing one requires
`docker compose build web`. For local dev, put overrides in
`web/.env.local` (git-ignored).

## Docker

```sh
docker compose up --build web
```

Serves the production build (nginx, SPA fallback) on
`http://localhost:${WEB_PORT:-5173}`. The dev server uses the same port,
so run one or the other.
