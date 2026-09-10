# CS3219 — Software Design and Architecture (AY2627 Sem 1)

## Favours on Campus (FoC)

**Favours on Campus (FoC)** is a peer-to-peer campus errand platform where
students can request items to be collected from stores or facilities on
campus, and other students can fulfil (and deliver) those requests. The
platform runs on a closed credit economy — credits cannot be bought,
withdrawn, or exchanged for money, and only circulate within the platform.

---

## Team Members

| Name | Service In-Charge | Nice-to-have |
| ----- | ----- | ----- |
| Kwey Xiu Xi | User Service | Cloud Deployment and DevOps |
| Alastair Tan Choon Wei | Supplier Service | — |
| Aung Ko Khant | Order Service | Centralized Logging |
| Ryan Ang Jun Wen | Credit Service | Order History |
| Leong Wei Zhi | Notification Service | Rating System |

---

## Repository Structure

This repository follows a **one-service-per-folder** structure: each
microservice (`user-service/`, `supplier-service/`, `order-service/`,
`credit-service/`, `notification-service/`) lives in its own top-level
folder. The frontend lives in `web/`.

```text
.
├── web/
├── user-service/
├── supplier-service/
├── order-service/
├── credit-service/
├── notification-service/
├── <n2h-service>/
└── README.md
```

- Any **nice-to-have (N2H)** feature that warrants its own service should
  be added as an **additional folder** at the same level, following the
  same per-service structure.
- Files for agentic coding tools (e.g. agent configs, prompts, skills)
  may be added as needed, but must still **respect the
  one-service-per-folder skeleton** for core implementation.

---

## AI Use Summary

This section is the consolidated AI-use disclosure required by the CS3219
AI Usage Policy (project document, Appendix 2). The detailed log with
prompts and timestamps lives in [`ai/usage-log.md`](ai/usage-log.md).

**Tools:** Claude Code (Fable 5)

**Prohibited phases avoided:** requirements elicitation and
prioritization; architecture and design decisions. Requirements, service
boundaries, allocation, and the tech stack were decided by the team; AI
tools were used only afterwards.

**Used for:** transcribing the team-written D1 backlog into labelled
GitHub issues; repository documentation (README, AGENTS.md); project
scaffolding (service folder skeletons).

**Verification:** all AI-assisted output is reviewed by the team through
pull requests before merging.
