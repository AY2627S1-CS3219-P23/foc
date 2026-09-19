<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-19.
  Scope: README written while creating the shared contracts library for
  issue #62 per team decision D15 (docs/notification-service.md).
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# foc-contracts

Shared **cross-service contract constants** — the one deliberate
exception (decision D15, 2026-09-19) to the "services never share code"
convention in [`AGENTS.md`](../AGENTS.md).

Only things that are a *contract between services* belong here, e.g.
the broker exchange names both producer and consumer must agree on
(`EventContracts.ORDER_EVENTS_EXCHANGE`). Service-private names
(queue names, table names, internal config) stay in their service.
The library has **no framework dependencies** — plain constants only —
so depending on it never drags Spring or broker types into a service.

## Use

Install to the local Maven repository, then depend on it:

```sh
./mvnw install
```

```xml
<dependency>
    <groupId>foc</groupId>
    <artifactId>foc-contracts</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Docker builds don't need the install step — each consuming service's
`Dockerfile` builds this module first (its compose build context is the
repo root).
