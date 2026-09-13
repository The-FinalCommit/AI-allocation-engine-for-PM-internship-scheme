# PRAGATI — Testing

Automated tests cover the two places where correctness matters most and is hardest to
verify by clicking: **the math** (optimizer, determinism, fairness constraints) and
**the rules** (eligibility, suitability scoring, weight invariants).

## Test suites

### AI service — `ai-service/tests/` (21 tests, Python / pytest)

Run:
```bash
cd ai-service
.venv/bin/python -m pytest tests/ -q        # Linux/macOS
.venv\Scripts\python.exe -m pytest tests\ -q  # Windows
```

`test_optimizer.py` (9 tests)
- **Capacity is respected** — no opportunity ever exceeds its seat count.
- **At most one seat per candidate** — the fundamental allocation constraint.
- **Global swap improvement** — on an engineered 3-candidate / 3-seat case the CP-SAT
  result strictly dominates the greedy baseline (the same proof shown in the UI).
- **Infeasible under full coverage** — 3 candidates / 1 seat + "allocate everyone"
  must be declared INFEASIBLE, not silently mishandled.
- **Infeasible group floor / feasible group floor** — fairness floors are real
  constraints: infeasible when impossible, satisfied when possible.
- **Deterministic repeat** — two identical solves give byte-identical assignments.
- **Empty pairs** — degenerate input handled cleanly.

`test_gap_guidance.py` (7 tests)
- **Grounded answers only** — guidance names only skills present in the opportunity's
  stated requirements and the candidate's verified profile.
- **No invented guarantees** — never promises certificates, placements or statistics.
- **Ineligible answers name the blocking skill.**
- **Deterministic** — same input → same answer; route contract verified.

`test_nlp.py`
- **Canonical skill extraction** — resume text maps to the canonical skill taxonomy.
- **Synonym normalization** — "Python" / "python" / "py" style variants normalize.
- **No invented skills** — the NLP never adds a skill the text does not support.
- **Normalize determinism** — same input → same output.

### Backend — `backend/src/test/` (36 tests, JUnit 5)

Run:
```bash
cd backend
mvn test
```

`SuitabilityServiceTest` — pure-logic tests of the hard gate and the soft objective:
- Qualification below minimum ⇒ ineligible (with a human-readable reason).
- Over-qualification ⇒ still eligible.
- Missing **mandatory** skill ⇒ ineligible; missing *optional* skill ⇒ not blocked.
- PAUSED / CLOSED opportunity ⇒ ineligible; withdrawn candidate ⇒ ineligible.
- Scoring is **deterministic** (same inputs, identical scores).
- Overall score bounded to 0–100.
- Stronger skill match + aligned interest + rank-1 preference ⇒ strictly higher score.
- Qualification factor peaks at exact match and stays high when above.
- **Default weights always sum to 100** — the "PRAGATI default configurable weights"
  invariant.

Other suites:
- `AnalyticsServiceTest` (4) — recommendations are top-5 eligible-only ranked by score
  and never modify candidate data; skill gap splits covered / mandatory / preferred
  (eligible ⇒ zero mandatory gaps).
- `ReadinessServiceTest` (4) — readiness scores reviewed resumes as complete, reports
  pending/failed/missing resumes honestly instead of counting them.
- `ResumeUploadTest` (3) — readable DOCX becomes PENDING_REVIEW with evidence-backed
  suggestions; scanned PDFs fail honestly; unsupported file types are rejected up front.
- `ResumeIntelligenceServiceTest` (5) — fields carry confidence + evidence, nothing is
  fabricated from absent text, district is only suggested when known for the state.
- `ResumeFlowTest` (5) — a stale resume id after replacement is rejected; fields or
  skills the resume never suggested cannot be confirmed; a reviewed resume cannot be
  applied again; unknown profile fields are rejected.

## What is intentionally not unit-tested

- The Spring wiring / REST layer is exercised end-to-end in the live demo and the QA
  report (every endpoint is called against the running system).
- The UI is verified against the API contract (React renders the JSON shapes above).

## QA evidence

Live end-to-end verification of every scenario, role flow, and report is recorded in
[FINAL_QA_REPORT.md](FINAL_QA_REPORT.md).
