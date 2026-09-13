# PRAGATI — 15-Minute Judge Walkthrough

The app also has a built-in guided mode: open the **Judge walkthrough** page (admin) and it
replays this narrative step by step against live data. This document is the script for the
human demo.

**Setup:** open `http://localhost:8080`. Sign in as **Admin** (`admin@pragati.gov.in` /
`Admin@123`) — credentials are pre-filled.

---

## 1. Open with the problem (1 min)

> "The PM Internship Scheme connects lakhs of students with real experience. Today the
> matching is manual or a simple recommender that shows each student a top-5 list. We asked:
> what if the *whole system* thought about *everyone at once*?"

Show the **Command Center** overview — scenario, dataset size, latest run.

## 2. The engine: global vs sequential (3 min)

1. **Scenarios** → load **Micro Conflict Proof** (4 candidates, 2 seats).
2. Start a run (Balanced policy) — completes in ~10 ms.
3. Open the **Comparison** tab:
   - Global allocation: **94.2** average suitability, 2 students placed.
   - Sequential baseline on the *same* data: **85.2**, and one student gets a worse seat.
4. Say: "This is not a claim — it is measured every run on identical data. The sequential
   approach locks in two weaker fits; the global optimizer swaps both seats and provably
   improves the population. The solver even certifies the result is optimal."
5. Point at the **run detail → provenance**: dataset fingerprint, policy, model size,
   solver status *Optimal allocation*, runtime.

## 3. Explainability: "Why this?" (2 min)

Open **Why this allocation** on the Micro run. For each of the 2 assignments, show the
factor breakdown: skills, qualification, interest, location, preference — with weights and
points. Say: "A parent, a student or an auditor can see exactly why a seat went to a person.
Nothing is a black box."

## 4. The real field: Standard Showcase (2 min)

1. Load **Standard Showcase** (700 candidates, 49 opportunities, 342 seats). Run it.
2. Show the **Comparison** (global vs baseline), **Fairness** (by group), and
   **Geography** (by state — demand vs capacity vs unmet) tabs.
3. Open **Conflicts** — where competition is fiercest.
4. Download the **PDF decision report** (one button) — the artifact an authority could file.

## 5. Policy as a first-class citizen — what-if (2 min)

On the Standard run, open the **Policy Lab**:
1. Drag **Skills to ~60%** and **Location to ~15%** (keep the total at 100% — the badge
   enforces it) and hit **Run what-if simulation**. The panel updates itself when it
   completes — no refresh needed.
2. Read the before/after out loud: "Average suitability 79.1 → ~83.0, and **104 candidates
   would change seats**; ~79% of the allocation stays put." A visible, non-zero delta —
   measured, not claimed. Point at the trade-off the panel also shows: preference
   satisfaction drops (18.4% → 9.3%) — policies are real trade-offs, and the lab measures
   both sides.
3. Then flip **"Allocate every candidate"** and simulate again → the lab answers
   **INFEASIBLE** with the reason (700 candidates, 342 seats). Say: "The lab can
   tell you *before* you commit that a policy cannot work."
4. Say: "Policies are configurable and versioned. What-if is a sandbox — it never touches
   the published allocation until a human deliberately starts a real run."

## 6. Fairness & inclusion (1 min)

Load **Fairness Study** (550 candidates — 275 rural, 275 urban), run with the Equity policy,
and open the **Fairness** tab: allocation share by group, measured — not asserted.

## 7. Honesty: infeasibility (1 min)

Load **Infeasibility Demo** (60 candidates, 18 seats). Run with the *Full Coverage* policy
("every candidate must be allocated"). The system answers **INFEASIBLE — and explains why**
(60 eligible candidates, 18 seats). Say: "A system that can say 'no, and here is the
mathematical reason' is a system you can trust when it says 'yes'."

## 8. Operations: reallocation with diff (1 min)

Back on a completed Standard run, open **Reallocation**:
1. **Change capacity** — the picker shows every opportunity with its current seats and how
   many people actually hold them ("N filled in this run"); it preselects the busiest one.
   Set the new seat count to 1.
2. **Reallocate** — the diff shows exactly who **moved**, who was **added**, who was
   **removed**, who stayed — each with a plain-language reason. (If a change ever leaves
   everything unchanged, the panel explains that the optimum genuinely didn't move.)

## 9. The other two roles (1.5 min)

1. Sign in as **Candidate**: readiness dashboard (score + what to improve), the allocation
   result, explore 30+ opportunities, edit skills/preferences, upload a resume and review
   the extracted skills.
2. Sign in as **Provider**: publish an opportunity, set capacity, pause/close it, see
   demand vs seats and placements.

## 10. Close (30 s)

> "Deterministic, reproducible, explainable, measurable, and honest about what AI can and
> cannot do. The same dataset and policy always give the same answer — and the improvement
> over the obvious approach is measured, not promised."

---

### Scale check if asked

Load **Performance Benchmark** (1,000 candidates, 80 opportunities, 510 seats): the full
run — eligibility, CP-SAT solve, baseline, persistence — completes in well under a second
(~0.7 s on this machine).

### Common judge questions

- **"Is the AI an LLM?"** — No. Deterministic skill normalization and resume extraction;
  the optimizer is a mathematical solver. No model can override eligibility.
- **"What if the AI service is down?"** — The system runs without it; resume extraction is
  simply unavailable. Allocation is owned by the Java service.
- **"Are these real data?"** — No; every candidate and organisation is synthetic and the UI
  says so. The weights are configurable demonstration defaults, not official government
  policy.
- **"How do I reproduce a run?"** — The run stores the dataset fingerprint, policy and seed;
  reloading the same scenario reproduces identical results.
