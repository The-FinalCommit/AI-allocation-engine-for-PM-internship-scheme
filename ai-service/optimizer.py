"""
PRAGATI global allocation engine.

Deterministic global optimization (Google OR-Tools CP-SAT) plus a
deterministic sequential (candidate-by-candidate) baseline. Both consume
the exact same dataset, eligibility set and suitability scores supplied by
the backend — nothing is invented or faked here.
"""

import math
import time
from collections import defaultdict

from ortools.sat.python import cp_model


def _index_pairs(pairs):
    by_c, by_i = defaultdict(list), defaultdict(list)
    for (c, i, score, pref) in pairs:
        by_c[c].append((i, score, pref))
        by_i[i].append(c)
    return by_c, by_i


def greedy_baseline(candidates, capacities, pairs):
    """
    Deterministic sequential baseline: candidates are processed in
    ascending id order; each takes the highest-scoring eligible
    opportunity with remaining capacity (ties broken by preference, then
    lower opportunity id).
    """
    by_c = defaultdict(list)
    for (c, i, score, pref) in pairs:
        by_c[c].append((score, pref, i))
    remaining = dict(capacities)
    chosen = []
    for c in sorted(by_c.keys()):
        best = None
        for (score, pref, i) in sorted(by_c[c], key=lambda t: (-t[0], -t[1], t[2])):
            if remaining.get(i, 0) > 0:
                best = (score, pref, i)
                break
        if best is not None:
            remaining[best[2]] -= 1
            chosen.append((c, best[2], best[0]))
    return chosen


def optimize(payload):
    candidates = payload["candidates"]
    capacities = {o["id"]: o["capacity"] for o in payload["opportunities"]}
    pairs = [(p["c"], p["i"], int(p["score"]), int(p.get("pref", 0))) for p in payload["pairs"]]
    policy = payload.get("policy") or {}
    params = payload.get("params") or {}
    max_time = int(params.get("maxTimeSeconds", 60))
    workers = int(params.get("workers", 1))
    seed = int(params.get("seed", 0))

    model = cp_model.CpModel()
    xs = {}
    for (c, i, score, pref) in pairs:
        xs[(c, i)] = model.NewBoolVar(f"x_{c}_{i}")

    by_c, by_i = _index_pairs(pairs)

    # Hard: at most one opportunity per candidate.
    for c, entries in by_c.items():
        model.Add(sum(xs[(c, i)] for (i, _, _) in entries) <= 1)

    # Hard: capacity per opportunity.
    for i, cand_ids in by_i.items():
        model.Add(sum(xs[(c, i)] for c in cand_ids) <= capacities.get(i, 0))

    # Per-candidate allocation indicator (used by full coverage and floors).
    y_c = {}
    for c, entries in by_c.items():
        y = model.NewBoolVar(f"y_{c}")
        model.Add(sum(xs[(c, i)] for (i, _, _) in entries) >= 1).OnlyEnforceIf(y)
        model.Add(sum(xs[(c, i)] for (i, _, _) in entries) <= 0).OnlyEnforceIf(y.Not())
        y_c[c] = y

    # Hard policy: every candidate must be allocated.
    if policy.get("fullCoverage"):
        for c in candidates:
            total = sum(xs[(c, i)] for (i, _, _) in by_c.get(c, []))
            model.Add(total == 1)

    # Hard policy: group fairness floors.
    for g in policy.get("groups") or []:
        floor = int(g.get("floorPct", 0))
        if floor <= 0:
            continue
        members = [y_c[c] for c in g.get("candidates", []) if c in y_c]
        need = math.ceil(len(members) * floor / 100.0)
        model.Add(sum(members) >= need)

    # Objective: maximize allocated count first, then total suitability
    # (score resolution: 0.01 suitability points), then stated-preference matches.
    # The 10001 bonus strictly dominates any single-pair suitability value
    # (max 10000), so the lexicographic order is exact.
    model.Maximize(sum((10001 + score + pref) * xs[(c, i)] for (c, i, score, pref) in pairs))

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = max_time
    solver.parameters.num_workers = workers
    solver.parameters.random_seed = seed
    solver.parameters.log_search_progress = False

    t0 = time.time()
    status = solver.Solve(model)
    elapsed_ms = int((time.time() - t0) * 1000)

    if status == cp_model.OPTIMAL:
        st = "OPTIMAL"
    elif status == cp_model.FEASIBLE:
        st = "FEASIBLE"
    else:
        st = "INFEASIBLE"

    global_assignment = []
    if st != "INFEASIBLE":
        for (c, i, score, pref) in pairs:
            if solver.Value(xs[(c, i)]) == 1:
                global_assignment.append({"c": c, "i": i, "score": score})
    greedy = greedy_baseline(candidates, capacities, pairs)
    greedy_out = [{"c": c, "i": i, "score": s} for (c, i, s) in greedy]

    return {
        "status": st,
        "objective": int(solver.ObjectiveValue()) if st != "INFEASIBLE" else 0,
        "runtimeMs": elapsed_ms,
        "model": {
            "pairs": len(pairs),
            "variables": len(xs),
            "constraints": len(by_c) + len(by_i) + (len(candidates) if policy.get("fullCoverage") else 0),
            "solverRuntimeMs": elapsed_ms,
        },
        "global": global_assignment,
        "greedy": greedy_out,
    }
