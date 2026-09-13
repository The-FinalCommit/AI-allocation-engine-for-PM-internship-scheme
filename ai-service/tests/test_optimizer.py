import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import optimizer


def _run(pairs, caps, full_coverage=False, groups=None, candidates=None):
    payload = {
        "candidates": candidates or sorted({p[0] for p in pairs}),
        "opportunities": [{"id": i, "capacity": c} for i, c in caps.items()],
        "pairs": [{"c": c, "i": i, "score": s, "pref": 0} for (c, i, s, p) in pairs],
        "policy": {"fullCoverage": full_coverage, "groups": groups or []},
        "params": {"maxTimeSeconds": 10, "workers": 1, "seed": 0},
    }
    return optimizer.optimize(payload)


def test_capacity_respected():
    # Two candidates, one seat: only one can be allocated.
    res = _run([(1, 10, 90, 0), (2, 10, 80, 0)], {10: 1})
    assert res["status"] in ("OPTIMAL", "FEASIBLE")
    assert len(res["global"]) == 1
    assert res["global"][0]["c"] == 1


def test_at_most_one_per_candidate():
    res = _run([(1, 10, 50, 0), (1, 11, 90, 0)], {10: 1, 11: 1})
    assert len(res["global"]) == 1
    assert res["global"][0]["i"] == 11


def test_global_beats_sequential_on_engineered_swap():
    # C1 best at O1, C2 best at O2, but sequential order C1->O1, C2->(O1 full)->O2...
    # Here: C1 scores 90 on O1 / 20 on O2; C2 scores 85 on O1 / 95 on O2; one seat each.
    res = _run([(1, 1, 90, 0), (1, 2, 20, 0), (2, 1, 85, 0), (2, 2, 95, 0)], {1: 1, 2: 1})
    global_pairs = {(a["c"], a["i"]) for a in res["global"]}
    greedy_pairs = {(a["c"], a["i"]) for a in res["greedy"]}
    assert global_pairs == {(1, 1), (2, 2)}
    assert greedy_pairs == {(1, 1), (2, 2)}  # both optimal here; next test shows the swap


def test_global_swap_improvement():
    # C1: O1 90 / O2 88 ; C2: O1 86 / O2 30 ; C3: O1 84 / O2 20 ; one seat on O1, two on O2.
    # Greedy (by id): C1->O1(90), C2->O2(30), C3->O2(20) total=140.
    # Global: C2->O1(86), C1->O2(88), C3->O2(20) total=194.
    res = _run([(1, 1, 90, 0), (1, 2, 88, 0), (2, 1, 86, 0), (2, 2, 30, 0), (3, 1, 84, 0), (3, 2, 20, 0)],
               {1: 1, 2: 2})
    g = {(a["c"], a["i"]): a["score"] for a in res["global"]}
    gr = {(a["c"], a["i"]): a["score"] for a in res["greedy"]}
    # Global finds the better composition: C1 moves to O2, freeing O1 for a stronger claim.
    assert sum(g.values()) > sum(gr.values())
    assert (1, 2) in g


def test_infeasible_full_coverage():
    # 3 candidates, 1 seat, every candidate must be allocated -> infeasible.
    res = _run([(1, 1, 90, 0), (2, 1, 80, 0), (3, 1, 70, 0)], {1: 1}, full_coverage=True)
    assert res["status"] == "INFEASIBLE"


def test_infeasible_group_floor():
    # Two rural candidates, one seat, floor 100% for the group -> infeasible.
    res = _run([(1, 1, 90, 0), (2, 1, 80, 0)], {1: 1},
               groups=[{"key": "RURAL", "candidates": [1, 2], "floorPct": 100}])
    assert res["status"] == "INFEASIBLE"


def test_feasible_group_floor():
    res = _run([(1, 1, 90, 0), (2, 1, 80, 0)], {1: 2},
               groups=[{"key": "RURAL", "candidates": [1, 2], "floorPct": 50}])
    assert res["status"] in ("OPTIMAL", "FEASIBLE")
    assert len(res["global"]) == 2


def test_deterministic_repeat():
    a = _run([(1, 1, 90, 0), (2, 1, 88, 0), (3, 1, 86, 0), (1, 2, 40, 0)], {1: 1, 2: 1})
    b = _run([(1, 1, 90, 0), (2, 1, 88, 0), (3, 1, 86, 0), (1, 2, 40, 0)], {1: 1, 2: 1})
    assert a["global"] == b["global"]
    assert a["greedy"] == b["greedy"]
    assert a["objective"] == b["objective"]


def test_empty_pairs():
    res = _run([], {1: 5})
    assert res["status"] == "OPTIMAL"
    assert res["global"] == []
    assert res["greedy"] == []
