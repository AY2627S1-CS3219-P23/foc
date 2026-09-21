<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
  Scope: this README, written alongside the SPA scaffold it documents.
  Stack, tooling, scope, and port decisions were made by the author
  (Leong Wei Zhi) via neutral-options Q&A; see ai/usage-log.md.
  Reviewed by: Leong Wei Zhi (via pull request).
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
│   ├── index.tsx     # mounts the shared shell; 404 handling
│   └── home.tsx      # "/" placeholder page
├── shared/
│   ├── shell/        # app shell: top bar, mobile tab bar, credits +
│   │                 # notification slots, 404 view
│   ├── components/   # common UI primitives (cross-domain)
│   └── auth/         # session/JWT handling (not built yet)
├── features/     # per-domain code — one folder per teammate/service
├── lib/          # API base URLs + shared fetch wrapper
└── test/         # Vitest setup + example shell/router tests
```

Adding a page: create a file under `src/routes/` and add one child
entry to the route table in `src/routes/index.tsx`. Route files stay
thin and import from `features/<domain>/`; REST calls go through
`lib/api/http.ts` (`apiFetch`); shared pieces go in `shared/` — see
`src/features/README.md` and `web/AGENTS.md`.

## Environment

The SPA calls each backend service directly (no API gateway). Base URLs
come from `VITE_*_SERVICE_URL` env vars — see the "Web frontend" section
of the root `.env.example`. **Vite inlines them at build time**: for the
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
