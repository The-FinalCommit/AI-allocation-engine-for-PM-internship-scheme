# PRAGATI — API Guide

Interactive, always-current documentation is served at
**`http://localhost:8080/swagger-ui.html`** (OpenAPI at `/v3/api-docs`). This guide is a
curated reference. All endpoints except `/api/auth/login` and `/api/health` require a
`Authorization: Bearer <token>` header.

## Auth

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/login` | public | Sign in, returns a JWT |
| GET | `/api/auth/me` | any signed-in | Current user + role |

Login body: `{"email":"admin@pragati.gov.in","password":"Admin@123"}`.

## System

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Liveness of backend + AI service |

## Candidate (all scoped to the signed-in candidate)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/candidates/me/profile` | View profile |
| PUT | `/api/candidates/me/profile` | Edit profile fields |
| PUT | `/api/candidates/me/skills` | Replace skills |
| PUT | `/api/candidates/me/interests` | Replace sector interests |
| GET / PUT | `/api/candidates/me/preferences` | Read / update ranked opportunity preferences |
| GET | `/api/candidates/me/readiness` | Readiness score + gaps to act on |
| POST | `/api/candidates/me/resume` | Upload resume (multipart) |
| GET | `/api/candidates/me/resume` | Resume status + extracted skills |
| POST | `/api/candidates/me/resume/skills/review` | Accept/reject extracted skills |
| GET | `/api/candidates/me/allocation` | Current allocation result (if any) |
| GET | `/api/candidates/me/allocation/history` | Past allocations across runs |
| GET | `/api/candidates/me/eligibility` | Which opportunities are eligible and why |
| GET | `/api/candidates/me/dashboard` | Readiness + allocation + next actions |

## Opportunity (public read, provider-managed)

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/api/opportunities` | any | Search/filter (sector, state, skills, pagination) |
| GET | `/api/opportunities/{id}` | any | Opportunity detail |
| GET | `/api/providers/me` | provider | Provider profile |
| GET | `/api/providers/me/opportunities` | provider | Own opportunities |
| GET | `/api/providers/me/opportunities/{id}` | provider | One opportunity |
| POST | `/api/providers/me/opportunities` | provider | Create opportunity |
| PUT | `/api/providers/me/opportunities/{id}` | provider | Update opportunity |
| PATCH | `/api/providers/me/opportunities/{id}/status` | provider | Activate / pause / close |
| GET | `/api/providers/me/capacity` | provider | Seats vs demand |
| GET | `/api/providers/me/impact` | provider | Placements per opportunity |

## Admin (all require ADMIN)

### Dataset & scenarios

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/overview` | Current scenario + system summary |
| GET | `/api/admin/scenarios` | Available scenario catalogue |
| POST | `/api/admin/scenarios/{key}/load` | Load a scenario dataset (deterministic) |
| GET | `/api/admin/data-quality` | Data quality checks |
| GET | `/api/admin/audit` | Audit trail (paginated) |
| GET | `/api/admin/technical` | Technical diagnostic summary |

Scenario keys: `STANDARD_SHOWCASE`, `MICRO_CONFLICT`, `HIGH_CONFLICT`, `GEOGRAPHIC`,
`FAIRNESS`, `INFEASIBLE`, `BENCHMARK`.

### Allocation runs

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/runs` | List runs |
| POST | `/api/admin/runs` | Start a run: `{"policyKey":"balanced", "weights":{...}, "fullCoverage":false, "fairnessFloorPct":10}` |
| POST | `/api/admin/runs/{id}/cancel` | Cancel an in-flight run |
| GET | `/api/admin/runs/{id}` | Run detail (status, totals, provenance) |
| GET | `/api/admin/runs/{id}/comparison` | Global vs baseline comparison |
| GET | `/api/admin/runs/{id}/conflicts` | Conflict/competition analysis |
| GET | `/api/admin/runs/{id}/movements` | Assignment movements |
| GET | `/api/admin/runs/{id}/why/assigned` | Why this, for every assigned candidate |
| GET | `/api/admin/runs/{id}/why/{candidateId}` | Why this, for one candidate |
| GET | `/api/admin/runs/{id}/fairness` | Fairness outcomes by group |
| GET | `/api/admin/runs/{id}/geography` | Geographic outcomes by state |
| GET | `/api/admin/runs/{id}/report` | Generate PDF decision report |

### Policy & simulation

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/policies` | Configurable weighting policies |
| POST | `/api/admin/simulations` | What-if simulation against a completed run |
| GET | `/api/admin/simulations` | List simulations |

Policy keys: `balanced`, `skills`, `preference`, `equity`, `full_coverage`.

Simulation body (all optional except `baseRunId`):
`{"baseRunId":1, "weights":{"skills":45,"location":25}, "fullCoverage":false, "fairnessFloorPct":10}`
— a sandboxed re-solve; it never touches the published run.

### Reallocation

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/admin/reallocations` | Reoptimize from a parent run under operational changes |
| GET | `/api/admin/reallocations` | List reallocations |
| GET | `/api/admin/reallocations/{id}/diff` | Parent/child diff: who moved, who gained, who lost, and why |

Body: `{"baseRunId":1, "changes":[{"type":"CAPACITY","opportunityId":7,"newCapacity":9},
{"type":"WITHDRAW_CANDIDATE","candidateId":42},
{"type":"WITHDRAW_OPPORTUNITY","opportunityId":11}]}`.
Change types: `CAPACITY` (needs `opportunityId` + `newCapacity`), `WITHDRAW_CANDIDATE`
(needs `candidateId`), `WITHDRAW_OPPORTUNITY` (needs `opportunityId`).

## The "why" contract

`/why/assigned` returns, for each assignment, the contributing factors — skill match,
mandatory-skill coverage, qualification fit, sector interest, location, preference rank and
experience — each with its weight and point contribution, summing to the total suitability.
This is the machine-readable basis for the human explanation shown in the UI.
