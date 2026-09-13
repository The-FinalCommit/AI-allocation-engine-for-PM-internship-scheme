# PRAGATI — Final QA Report

**Date:** 2026-09-11 · **Refreshed 2026-09-13** — the showcase dataset was scaled to its
final size (Standard Showcase 700 / 49 / 342); §1–§5 were re-measured live against the
current build on this date.
**Environment:** Linux sandbox, JDK 17.0.20, Spring Boot 3.4.1,
Python 3.13 + OR-Tools CP-SAT 9.15.6755, H2 in-memory (demo profile).
**Method:** every item below was executed against the *running* system (HTTP calls to
`:8080` / `:8000` or `mvn test` / `pytest`). No result is theoretical.

---

## 1. Automated test suites

| Suite | Run | Result |
|---|---|---|
| Python (optimizer + NLP) | `ai-service/.venv/bin/python -m pytest tests/ -q` | **21 passed** in 0.5 s |
| Java (eligibility + suitability + weights) | `mvn test` | **36 passed, 0 failures** (BUILD SUCCESS) |
| Frontend type safety | `tsc --noEmit` | **0 errors** |
| Frontend production build | `vite build` | success — `index-BwSOG-zL.js` 958.41 kB (gzip 271.43 kB), CSS 57.39 kB |

## 2. Scenario matrix (all 7, live)

Each row: load → run → status → global vs baseline (average suitability) → end-to-end run time.

| Scenario | Size | Policy | Status | Global | Baseline | Run time |
|---|---|---|---|---|---|---|
| Standard Showcase | 700 c / 49 o / 342 s | balanced | Completed | 332 @ 79.1 | 320 @ 76.0 | 336 ms |
| **Micro Conflict Proof** | 4 c / 2 o / 2 s | balanced | Completed | **2 @ 94.2** | **2 @ 85.2** | 12 ms |
| High Competition | 550 c / 14 o / 34 s | balanced | Completed | 34 @ 84.3 | 34 @ 77.4 | 56 ms |
| Geographic Impact | 550 c / 24 o / 132 s | balanced | Completed | 128 @ 83.0 | 128 @ 77.1 | 115 ms |
| Fairness Study | 550 c / 18 o / 93 s | equity | Completed | 91 @ 79.7 | 91 @ 72.2 | 75 ms |
| **Infeasibility Demo** | 60 c / 12 o / 18 s | full_coverage | **Infeasible** | — | — | 12 ms |
| **Performance Benchmark** | 1000 c / 80 o / 510 s | balanced | Completed | 503 @ 81.1 | 483 @ 76.8 | **674 ms** |

Verified properties:
- **Determinism** — reload of the same scenario reproduces the same dataset fingerprint
  (e.g. Standard Showcase v4 fingerprint `5a60a8809af26d0d…`); re-runs produce identical
  allocations (provenance `solverSeed 0`, single worker).
- **Optimality certification** — Benchmark provenance: solver status *Optimal allocation*,
  7,029 variables, 1,032 constraints, objective 9,112,155, solver time 484 ms.
- **Honest infeasibility** — full-coverage on 60 candidates / 18 seats returns the
  `Infeasible` status with an explanatory message (no silent partial allocation).
- **Micro proof preserved** — the engineered 94.2 vs 85.2 gap is produced by the live
  engine on every run (nothing hardcoded).

## 3. Single-port delivery (UI + API on :8080)

| Check | Result |
|---|---|
| `GET /` | 200 `text/html` (SPA shell) |
| `GET /admin/runs` (deep link) | 200 `text/html` (SPA fallback) |
| `GET /assets/index-BwSOG-zL.js` | 200 `text/javascript`, 958,835 B |
| `GET /assets/index-CVgGab-O.css` | 200 `text/css` |
| `GET /logo.png`, `/favicon.png` | 200 |
| `GET /india-states.json` | 200 (347 kB simplified GeoJSON) |
| `GET /swagger-ui.html` | 200 (via redirect) |
| `GET /api/health` | `{"status":"UP","aiService":"UP"}` |

## 4. Role flows (live, three sign-ins)

**Candidate** (`candidate@pragati.gov.in` / `Candidate@123`)
- profile GET/PUT ✓ · skills PUT ✓ · interests PUT ✓ (saved) · preferences GET/PUT ✓
- readiness 0–100 with actionable gaps ✓ · dashboard (readiness + allocation + nextActions) ✓
- opportunities explorer: 49 opportunities with eligibility + reasons ✓
- allocation before a run: clean "no allocation yet" state ✓

**Provider** (`provider@pragati.gov.in` / `Provider@123`)
- create opportunity → PAUSED → CLOSED status transitions ✓
- capacity vs demand per opportunity ✓ · placements impact ✓

**Admin** (`admin@pragati.gov.in` / `Admin@123`)
- scenarios catalogue + load ✓ · start/cancel runs ✓ · run detail with staged progress ✓
- comparison ✓ · conflicts (pressure ratios) ✓ · movements ✓
- **why/assigned**: factor-level explanations (skills, qualification, interest, location,
  preference, learning, experience — each with fit, weight, contribution) ✓
- fairness by location type + state ✓ · geography (demand vs capacity vs unmet by state) ✓
- **PDF decision report**: 200 `application/pdf`, 158,860 B, downloaded through the
  authenticated blob path (no raw 401 screens) ✓
- what-if **simulation**: weights validated (must total 100%); skills/location policy
  (60/15) → **104 affected, stability 79.2%, suitability 79.1 → 83.0** (preference
  satisfaction 18.4 → 9.3 — the trade-off is measured, not hidden); "allocate every
  candidate" on 700/342 → honest **INFEASIBLE** verdict ✓
- **reallocation**: capacity 4→1 on the busiest conflicted opportunity (Financial Analyst
  Intern, 242 eligible) → child diff `{moved: 9, added: 0, removed: 3, unchanged: 320}`
  with per-candidate before/after + plain-language reasons ✓ (a no-op change on an
  unallocated opportunity correctly yields all-unchanged)
- **audit trail**: 11+ entries, latest-first (sign-in, allocation started/completed,
  reallocation, dataset loads) ✓
- judge walkthrough: 14 narrative steps against live data ✓
- technical diagnostics (both services, optimizer, dataset) ✓

## 5. Security & validation checks

- No token → 401 on all protected endpoints ✓ · wrong role → 403 ✓
- Candidate A cannot read Candidate B's profile (own-data scoping) ✓
- Provider can manage only its own opportunities ✓
- Login rate limit (8/min) returns 429 when exceeded ✓
- Simulation with weights ≠ 100% → 400 "Policy weights must total exactly 100%" ✓
- Reallocation on unknown opportunity → 400; on stale dataset → 409 with guidance ✓
- Security headers + CORS restricted to the demo origin ✓

## 6. Bugs found and fixed in this QA pass

| # | Bug | Root cause | Fix | Verified |
|---|---|---|---|---|
| 1 | **BENCHMARK scenario load hung forever** (JVM at 100 % CPU) | Unbounded `while` loop in the seeder's skill picker: requested more distinct skills than the union of the two sector pools can provide | Picker now shuffles the union once and takes at most `min(requested, available)` — structurally incapable of looping | BENCHMARK loads in **0.8 s**; run in 869 ms |
| 2 | **FAIRNESS scenario load failed (500)** — duplicate `user_id` | Multi-block scenarios attached the demo candidate profile in every block (unique constraint `uq_candidate_user`) | `demoAssigned` flag — demo profile is created at most once per scenario | FAIRNESS loads + runs (64 @ 75.2 vs 63 @ 72.1) |
| 3 | SPA root `/` returned 404 | No root mapping in the SPA fallback controller | Explicit `/` → `forward:/index.html` | `GET /` 200 |
| 4 | Hashed assets served as `text/html` | Fallback route pattern intercepted the `assets` path segment | `assets` excluded from deep/top SPA route patterns | Assets served `text/javascript` / `text/css` |
| 5 | "Why" browser could silently truncate (limit 40, no disclosure) | Hard 40-row fetch with no total shown | Fetch up to 100 + "Showing first N of M" disclosure | Built + deployed |
| 6 | **Policy Lab always showed "0 affected"** | The simulated allocation's entity fields (`affectedCount`, `stability`) were never populated — only the JSON blob — and score resolution (0.1 pts) meant many weight changes left the integer objective unchanged; candidates who *lost* a seat were not counted | Objective rebuilt at 0.01-pt resolution (10001 + score + preference) with exact lexicographic order; removed candidates now count as affected; entity fields populated on completion | Skills/location policy → **21–29 affected**, suitability 76.9 → 82.6–83.1 |
| 7 | **Simulation results panel never updated** ("No simulation yet" after Completed) | UI fetched the simulation list once, immediately after the async POST — before completion — and never polled | After the POST the UI polls every 1.2 s until a final state, showing "Simulating…" in the results panel meanwhile | Panel shows current → scenario metrics + affected/stability after completion |
| 8 | **Reallocation could return 0 moved** (demo change targeted an opportunity nobody held) | The "Change capacity" action pre-selected the first opportunity in the list, which often had 0 placements — a legitimate no-op that looked broken | Conflicts endpoint now reports `allocated` per opportunity; the picker shows "N filled in this run"; the default change targets the most-allocated opportunity; an all-unchanged diff now carries an explicit explanation | Most-placed opp → 1 seat: **5 moved / 9 removed / 131 unchanged**; no-op case explains itself |
| 9 | **PDF download showed raw 401 JSON** | Report opened as a plain link in a new tab — no Authorization header possible | Authenticated blob download (fetch + JWT + browser save); friendly errors instead of raw JSON | `GET …/report` with token → 200 `application/pdf` 158,860 B |
| 10 | **White screen on /candidate/profile** | `useMemo` called *after* a conditional early return (Rules of Hooks) — React threw "rendered more hooks than during the previous render" once the profile loaded | Hook moved above the early return; all other pages scanned for the same pattern (none found) | tsc clean; route serves + renders |
| 11 | **White screen on Movements & conflicts tab** | Frontend `ConflictRow` type declared `demandPressure/sector/state/capacity` — fields the API never returned; `demandPressure.toFixed` threw on real data | Type aligned to the real API (eligible/seats/allocated/unmetDemand/pressure); panel now shows Seats / Filled / Demand / Pressure | Tab renders on live runs |
| 12 | **Deep link to a missing run (/admin/runs/4) — blank page** | No dedicated not-found state | 404 from the API now renders a branded "This run doesn't exist" card with a back button | Verified |
| 13 | No render-time safety net for the whole app | — | Global React **ErrorBoundary** at the root: any future render error shows a clean recovery card (reload / go to dashboard), never a blank page | Wired into `main.tsx` |
| 14 | **Profile dropdowns used wrong enum values** (HIGH_SCHOOL/MASTERS/RECENT_GRADUATE) | UI constants drifted from the backend enums | Aligned to `HIGHER_SECONDARY / BACHELORS / POST_GRADUATE / DOCTORAL` and `STUDENT / GRADUATE / WORKING` | Selects match stored values; saves accepted |

## 7. Known limitations (deliberate, documented)

- H2 in-memory for the demo: data resets on restart by design (the seeder reproduces it).
- The what-if simulation endpoint returns a summary; the full per-candidate sandbox diff is
  not persisted (by design — it is a sandbox, not a run).
- Resume upload accepts PDF/DOCX; extraction is deterministic keyword/skill matching, not
  an LLM (stated honestly in the UI).
- Benchmark at 1,000×80 runs in < 1 s; no 100 k-scale validation was performed (out of
  scope for the demonstration).

## 8. Reproduce this report

```bash
# 1. toolchain: JDK 17 + Maven + Python 3.10+ venv with ai-service/requirements.txt
# 2. start both services (or: docker compose up --build / start-demo-windows.ps1)
cd ai-service && .venv/bin/python -m pytest tests/ -q
cd backend && mvn test
# 3. exercise the scenario matrix via the UI or:
curl -X POST localhost:8080/api/auth/login -d '{"email":"admin@pragati.gov.in","password":"Admin@123"}'
curl -X POST localhost:8080/api/admin/scenarios/STANDARD_SHOWCASE/load -H "Authorization: Bearer <token>"
curl -X POST localhost:8080/api/admin/runs -H "Authorization: Bearer <token>" -d '{"policyKey":"balanced"}'
```

**Verdict:** all acceptance-critical behaviours pass live. The system is deterministic,
explainable, honest about infeasibility, measurable against its baseline, and delivers the
entire product (UI + API + optimizer) on a single port.
