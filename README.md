<div align="center">

<img src="assets/logo_p0.png" alt="PRAGATI logo" width="220"/>

# PRAGATI — AI Smart Allocation for the PM Internship Scheme

**Smart India Hackathon 2026 · Problem Statement SIH25033**
**Team: The Final Commit**

[![Java](https://img.shields.io/badge/Java-17-b07219?logo=openjdk&logoColor=white)](backend)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white)](backend)
[![Python](https://img.shields.io/badge/Python-3.10%2B-3776AB?logo=python&logoColor=white)](ai-service)
[![FastAPI](https://img.shields.io/badge/FastAPI-OR--Tools%20CP--SAT-009688?logo=fastapi&logoColor=white)](ai-service)
[![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)](frontend)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-3178C6?logo=typescript&logoColor=white)](frontend)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](docker-compose.yml)
[![License](https://img.shields.io/badge/License-MIT-informational)](#license)

</div>

---

PRAGATI allocates internship opportunities **across the entire candidate population** — not a
top‑5 recommender. One run takes every eligible candidate, every opportunity, and every seat, and
produces a single globally optimal, fully explained, reproducible allocation.

> **Demo environment.** Every candidate, organisation, and opportunity in this build is
> **synthetic and system‑generated**, and this is clearly disclosed in the UI. The allocation
> engine is deterministic: the same dataset + policy + seed always yields the identical
> allocation.

---

## Table of contents

- [What makes PRAGATI different](#what-makes-pragati-different)
- [Architecture at a glance](#architecture-at-a-glance)
- [Roles & demo accounts](#roles--demo-accounts)
- [Scenarios](#scenarios-self-contained-demonstration-datasets)
- [Run it](#run-it)
- [Repository layout](#repository-layout)
- [Testing](#testing)
- [Key documentation](#key-documentation)
- [What is deliberately out of scope](#what-is-deliberately-out-of-scope)
- [Team](#team)
- [License](#license)

---

## What makes PRAGATI different

| Capability | How PRAGATI does it |
|---|---|
| **Global, not sequential** | An integer constraint model (OR‑Tools CP‑SAT) optimises the *whole* population at once — a candidate can give up a seat to unlock a better outcome for someone else. |
| **Provable** | A deterministic sequential baseline runs on the *identical* dataset in every run, so the improvement is measured, never claimed. (Micro scenario: 94.2 vs 85.2 average suitability.) |
| **Explainable** | Every assignment stores its factor‑level breakdown — skills, qualification, interest, location, preference, learning, experience — with weights and contributions. "Why this allocation" answers per candidate. |
| **Fair by design** | Eligibility is a hard rule; fairness floors (e.g. minimum share for rural candidates) are hard constraints; outcomes are *measured* by group and state. |
| **Policy as a first-class citizen** | Configurable, versioned weighting policies; sandboxed what‑if simulations that never touch the published result; honest INFEASIBLE verdicts when a policy cannot be met. |
| **Operational** | Reallocation with parent/child diff ("who moved and why"), run cancellation, dataset versioning with superseded‑run marking, complete audit trail, PDF decision report. |
| **Honest about AI** | The AI service understands resumes and normalises skills. It **never allocates and never overrides eligibility** — the system runs fully without it. |

## Architecture at a glance

```
        Browser (React SPA, served by Spring on :8080)
                    │  /api/* (JWT)
        ┌───────────▼───────────┐
        │   Spring Boot 3 (17)  │  security · JPA/H2 (demo) · audit · PDF · reports
        └───────────┬───────────┘
                    │  /optimize · /nlp/skills (JSON, deterministic contract)
        ┌───────────▼───────────┐
        │  FastAPI AI service   │  skill normalization · CP-SAT optimizer · baseline
        │        (:8000)        │  OR-Tools CP-SAT, single worker, fixed seed
        └────────────────────────┘
```

- **Java** owns the data, eligibility, suitability scoring, persistence, security, and decisions.
- **Python** owns understanding (resumes/skills) and the mathematical optimization. It is a
  separate service because that is where it adds real value — and the Java service degrades
  gracefully if it is offline.
- **H2** (in‑memory) for the demo, **MySQL 8** profile for production (`SPRING_PROFILES_ACTIVE=prod`).

Full write‑up: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Roles & demo accounts

| Role | Email | Password | What they see |
|---|---|---|---|
| Administrator | `admin@pragati.gov.in` | `Admin@123` | Command center, scenarios, runs, what‑if policy lab, reallocation, audit, judge walkthrough, system health |
| Organisation partner | `provider@pragati.gov.in` | `Provider@123` | Publish/manage opportunities, capacity vs demand, impact per opportunity |
| Candidate | `candidate@pragati.gov.in` | `Candidate@123` | Readiness dashboard, allocation result, explore opportunities, edit profile/skills/preferences, upload resume |

Credentials are pre‑filled on the sign‑in screen.

## Scenarios (self-contained demonstration datasets)

| Scenario | Field | Purpose |
|---|---|---|
| Standard Showcase | 700 candidates · 49 opportunities · 342 seats | Realistic mixed field |
| Micro Conflict Proof | 4 candidates · 2 seats | Engineered proof that global beats sequential (94.2 vs 85.2) |
| High Competition | 550 candidates · 14 hot opportunities · 34 seats | Demand far above capacity |
| Geographic Impact | 550 candidates across 16 states · 24 opportunities · 132 seats | Capacity concentrated, demand spread |
| Fairness Study | 550 candidates (275 rural / 275 urban) · 18 opportunities · 93 seats | Measured fairness outcomes by group |
| Infeasibility Demo | 60 candidates · 18 seats | "Every candidate allocated" is impossible — the system says so and explains why |
| Performance Benchmark | 1000 candidates · 80 opportunities · 510 seats | Scale check (sub‑second) |

## Run it

### Option A — Docker (single command)

```bash
docker compose up --build
# open http://localhost:8080
```

### Option B — Windows one-click

```powershell
.\start-demo-windows.ps1
# open http://localhost:8080
```

Requires **Java 17** and **Python 3.10+**. If `backend/target/pragati-backend.jar` is present the
launcher starts it directly; otherwise it builds the jar with **Maven** (3.9+). The AI‑service
Python environment and the frontend build are created on first run only when missing.

### Option C — Manual (Linux / macOS / Windows)

Prerequisites: **Java 17 (JDK)**, **Python 3.10+**, optionally **Node 18+** (only to rebuild the UI).

```bash
# 1. AI + optimization service (port 8000)
cd ai-service
python -m venv .venv
.venv/bin/pip install -r requirements.txt        # Windows: .venv\Scripts\pip
.venv/bin/uvicorn main:app --host 127.0.0.1 --port 8000

# 2. Backend + UI (port 8080) — frontend must be built once into backend/frontend-dist
cd frontend && npm install && npm run build && cp -r dist/* ../backend/frontend-dist
cd ../backend
mvn -q spring-boot:run
# open http://localhost:8080
```

Interactive API docs: **http://localhost:8080/swagger-ui.html**
Service health: **http://localhost:8080/api/health** (reports both services)

## Repository layout

```
pragati/
├── start-demo-windows.ps1     # one-click Windows starter
├── docker-compose.yml         # one-command Docker demo
├── backend/                   # Spring Boot (Java 17) — API, data, security, UI hosting
│   ├── Dockerfile
│   ├── frontend-dist/         # built React app (served on :8080)
│   └── src/main/java/in/pragati/
│       ├── config/  security/  common/
│       ├── domain/  repo/      # entities + JPA repositories
│       ├── seed/               # deterministic scenario seeder (7 scenarios)
│       ├── service/            # allocation, comparison, fairness, reallocation, PDF…
│       ├── ai/                 # typed client for the AI service
│       └── web/                # REST controllers (Admin / Candidate / Provider / Opportunity)
├── ai-service/                 # FastAPI + OR-Tools CP-SAT + deterministic NLP
│   ├── main.py  optimizer.py  nlp.py
│   └── tests/                  # pytest (optimizer proofs + NLP determinism)
├── frontend/                   # React 18 + TypeScript + Vite + Tailwind
├── tools/                      # e2e and performance measurement scripts
└── docs/                       # architecture, API, judge guide, data model, QA report
```

## Testing

```bash
# AI service — optimizer proofs + NLP determinism
cd ai-service && .venv/bin/pytest

# Backend — unit + integration tests
cd backend && mvn test

# End-to-end / performance
python tools/e2e.py
python tools/measure.py
```

See [docs/TESTING.md](docs/TESTING.md) for the full test matrix and
[docs/FINAL_QA_REPORT.md](docs/FINAL_QA_REPORT.md) for end‑to‑end quality evidence.

## Key documentation

- [docs/JUDGE_GUIDE.md](docs/JUDGE_GUIDE.md) — the 15‑minute demo script
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — how the system is built
- [docs/API_GUIDE.md](docs/API_GUIDE.md) — endpoint reference (also Swagger UI)
- [docs/DATA_MODEL.md](docs/DATA_MODEL.md) — entities, invariants, integrity
- [docs/TESTING.md](docs/TESTING.md) — automated tests and how to run them
- [docs/FINAL_QA_REPORT.md](docs/FINAL_QA_REPORT.md) — end‑to‑end quality evidence

## What is deliberately out of scope

- Real government integration, PII, production identity (SSO), payments, notifications.
- This is a **demonstration product**: data is synthetic, the JWT secret is demo‑only, and the
  policies are *illustrative configurable defaults — not official government weights*.

## Team

**The Final Commit** — Smart India Hackathon 2026

| # | Name | GitHub |
|---|---|---|
| 1 | Kartik Jindal | [@BlazeO8](https://github.com/BlazeO8) |
| 2 | Vinamra Gupta | [@VinamraGupta01](https://github.com/VinamraGupta01) |
| 3 | Ansh Nanda | [@anshn120](https://github.com/anshn120) |
| 4 | Avni Saxena | [@s75avni](https://github.com/s75avni) |
| 5 | Deepanshu | [@Deepanshu080](https://github.com/Deepanshu080) |
| 6 | Ishika Tyagi | [@ishikatyagi-tech](https://github.com/ishikatyagi-tech) |

## License

This project is submitted for Smart India Hackathon 2026 under Problem Statement **SIH25033**.
Unless a `LICENSE` file is added to the repository, all rights are reserved by the authors listed
above. If you intend to open‑source this project, add an [MIT](https://choosealicense.com/licenses/mit/)
or similar license file and update this section accordingly.

---

<div align="center">
Built with care by <strong>The Final Commit</strong> for SIH 2026 · Problem SIH25033
</div>
