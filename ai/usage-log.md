# AI Usage Log

Shared log of AI tool usage, required by the CS3219 AI Usage Policy
(project document, Appendix 2). Every AI-assisted change must be logged
here with a timestamp, the prompts used, and the usage scenario.

Entry template:

```markdown
## YYYY-MM-DD — <member name>
- **Tool:** <e.g., Claude Code (Fable 5), GitHub Copilot>
- **Mode:** generate | refactor | debug | explain | docs
- **Scope:** <files/feature affected>
- **Prompt(s):** <exact prompt or summary + key responses>
- **Author review:** <how the output was validated/edited/tested>
```

---

## 2026-09-14 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** docs
- **Scope:** `docs/architecture.md` — high-level service architecture
  diagram (Mermaid) plus an exported SVG/PNG for the D1 document.
- **Prompt(s):** Asked to draw the high-level architecture diagram
  connecting the dependencies between services, based on the team's D1
  functional/non-functional requirements and following the CS3219 L4
  diagram guidelines (title, legend, labelled lines, explicit elements).
- **Author review:** The architecture itself (microservices, service
  boundaries, REST + async event workflow, JWT auth) was decided by the
  team beforehand (AGENTS.md, D1 document); the tool transcribed those
  decisions into a diagram. Open decisions (broker technology, database
  engines, client update transport) were kept as explicit "TBD" markers
  for the team — the tool made no design choices. Reviewed via pull
  request.

## 2026-09-10 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** docs, boilerplate
- **Scope:** GitHub issues for the product backlog (from the team's D1
  requirements document), labels, README team-allocation table,
  `notification-service/` skeleton folder, `AGENTS.md`, this log file.
- **Prompt(s):** Asked to create GitHub issues from the team-written D1
  requirements with priority/sprint/service labels; update README with the
  team's allocation; document the team's chosen tech stack and the course
  guidelines in AGENTS.md.
- **Author review:** Requirements, priorities, allocation, and tech stack
  were decided by the team beforehand (D1 document/presentation); the tool
  transcribed and formatted them. Output reviewed via pull requests.
