# PRAGATI — Architecture

## System overview

```
 Browser ── single port :8080 ──► React SPA (served as static files)
                                        │  /api/*  (JWT bearer)
                       ┌────────────────▼─────────────────┐
                       │  Spring Boot 3.4 · Java 17       │
                       │  ─ security: JWT + BCrypt, RBAC  │
                       │  ─ data: JPA + H2 (demo) / MySQL │
                       │  ─ domain services:              │
                       │     eligibility · suitability    │
                       │     allocation orchestration     │
                       │     comparison · fairness · geo  │
                       │     simulations · reallocation   │
                       │     audit · provenance · PDF     │
                       │     deterministic scenario seeder│
                       └────────────────┬─────────────────┘
                                        │  JSON, deterministic contract
                                        │  /optimize · /nlp/skills · /nlp/resume
                       ┌────────────────▼─────────────────┐
                       │  FastAPI · Python 3.10+          │
                       │  ─ deterministic NLP:            │
                       │     skill normalization & resume │
                       │     extraction (no LLM)          │
                       │  ─ OR-Tools CP-SAT optimizer:    │
                       │     global integer model +       │
                       │     deterministic greedy baseline│
                       └──────────────────────────────────┘
```

Two services, one principle: **Java owns the decision, Python owns the mathematics and the
understanding.** The AI service is a plain JSON microservice — it holds no business state and
the product runs (minus resume understanding) if it is offline. It exists as a separate
process because that is where OR-Tools and NLP tooling live; this is separation for real
value, not for architecture's sake.

## Why a global optimizer (and what the baseline is)

A naive system processes candidates one by one: each takes the best available seat and never
gives it back. A globally optimal allocation can require a candidate to *release* a seat so
the whole population does better.

PRAGATI models the allocation as an integer program:

- **Variables** — one binary per (candidate, opportunity) pair: assign / not assign.
- **Hard constraints** — eligibility (qualification, status), one seat per candidate,
  capacity per opportunity, fairness floors (e.g. minimum rural share), policy rules such as
  “every candidate must be allocated” when enabled.
- **Objective** — maximize total suitability, where suitability is a weighted factor model:
  mandatory skills, overall skill match, qualification fit, sector interest, location
  preference, stated preference rank, and experience. Weights come from the selected policy
  and are labelled *configurable demonstration defaults — not official government weights*.
- **Solver** — Google OR-Tools **CP-SAT**. When the solver certifies *OPTIMAL*, PRAGATI
  records that in the run's provenance (you can read “Optimal allocation” on the run detail).

In every run, a **deterministic sequential baseline** (candidates processed in fixed order,
each taking the best seat available) is computed on the *identical* dataset and policy. The
comparison page shows the measured difference — never a claim.

**The Micro Conflict Proof scenario** is an engineered 4-candidate / 2-seat case where the
baseline provably loses (94.2 vs 85.2 average suitability) — a one-slide demonstration that
global optimization is not a marketing term here.

## Determinism & reproducibility

- Every scenario is generated from a **fixed numeric seed** (seeded PRNG). Same seed → same
  dataset, byte for byte. Each loaded dataset records a SHA-256 fingerprint.
- The optimizer uses a fixed seed and single-threaded CP-SAT. Same dataset + policy →
  same allocation.
- Runs store their full **provenance**: dataset fingerprint, policy version, weights, solver
  status, model size (variables/constraints), objective value, and solver runtime. Two runs
  on the same dataset can be verified identical from the record alone.

## Eligibility vs suitability

Eligibility is a **hard rule** (qualification minimum, candidate status, opportunity status).
An ineligible candidate simply has no variable for that opportunity — the optimizer cannot
and does not override it. Suitability is the **soft objective** — how good the fit is among
eligible options. The AI service never touches eligibility and never allocates; it normalizes
skills and reads resumes so that the factor model works with messy human input.

## Data model (summary)

`User` (role: candidate/provider/admin) → `CandidateProfile` → `CandidateSkill` ·
`CandidateInterest` · `CandidatePreference` · `ResumeFile` (raw upload + extracted skills).
`Provider` → `Opportunity` → `OpportunitySkill` (mandatory/optional).
`DatasetSnapshot` (scenario, version, seed, fingerprint, counts) · `AllocationRun`
(policy, status, totals, provenance) → `Assignment` (factor breakdown JSON, score, rank) ·
`GroupMetric` · `GeoMetric` · `ConflictMetric` · `SimulationRun` (what-if) ·
`Reallocation` (parent/child diff) · `AuditEntry`.

See [DATA_MODEL.md](DATA_MODEL.md) for fields and invariants.

## Security

- JWT bearer tokens (HS256, demo secret) issued at login; BCrypt password hashes.
- Role-based access: candidate endpoints return only the caller's own data; provider
  endpoints manage only the provider's opportunities; admin endpoints require ADMIN.
- Login rate limiting (8/minute), CORS restricted to the demo origin, security headers.
- Everything in this build is a **synthetic demonstration** — there is no real PII and the
  JWT secret is deliberately not a production secret.

## Scaling behaviour (measured)

| Scenario | Candidates | Opportunities | Seats | Run time* |
|---|---|---|---|---|
| Micro Conflict Proof | 4 | 2 | 2 | 12 ms |
| High Competition | 550 | 14 | 34 | 56 ms |
| Standard Showcase | 700 | 49 | 342 | 336 ms |
| Performance Benchmark | 1,000 | 80 | 510 | 674 ms |

\* end-to-end run time on this development machine, including eligibility evaluation,
CP-SAT solve, baseline solve and persistence.

## Failure modes, honestly

- **Infeasible policy** — when a hard rule (e.g. “allocate everyone”) cannot be met, the
  solver says INFEASIBLE and the UI shows exactly why, instead of silently producing a bad
  allocation. The Infeasibility Demo scenario exists to prove this path.
- **AI service down** — health endpoint reports it; resume extraction is unavailable;
  allocation still works from profile data.
- **Timeouts** — the optimizer has a bounded time budget; if the budget is hit, the best
  proven result is recorded with its true status (never silently presented as optimal).
