#!/usr/bin/env python3
"""Live measurement sweep for doc accuracy (SIH final pass).
Phase A: measure the active STANDARD run (comparison/fairness/geography/conflicts/sims/reallocation).
Phase B: load+run each other scenario and record counts.
Phase C: reload STANDARD, run it fresh — leaves the demo in a clean state."""
import json, time, urllib.request, urllib.error

BASE = "http://127.0.0.1:8080"

def req(method, path, body=None, tok=None, timeout=180):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if tok:
        r.add_header("Authorization", "Bearer " + tok)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.load(e)
        except Exception:
            return e.code, {}

def main():
    _, login = req("POST", "/api/auth/login", {"email": "admin@pragati.gov.in", "password": "Admin@123"})
    tok = login["token"]
    out = {}

    def overview_counts():
        ov = req("GET", "/api/admin/overview", tok=tok)[1]
        sc = ov.get("scenario") or {}
        return {
            "scenario": sc.get("key"), "candidates": sc.get("candidateCount"),
            "opportunities": sc.get("opportunityCount"), "seats": sc.get("seatCount"),
            "readiness": ov.get("readiness"), "allocation": ov.get("allocation"),
            "aiMode": ov.get("aiMode"),
        }

    def load(key):
        s, d = req("POST", f"/api/admin/scenarios/{key}/load", tok=tok)
        return s, d

    def start_run(policy=None, full_coverage=None):
        body = {}
        if policy: body["policyKey"] = policy
        if full_coverage is not None: body["fullCoverage"] = full_coverage
        _, run = req("POST", "/api/admin/runs", body or None, tok=tok)
        return run["id"]

    def terminal(st):
        st = (st or "").lower()
        return any(k in st for k in ("completed", "infeasible", "failed", "cancel"))

    def save():
        with open("/tmp/measured.json", "w") as f:
            json.dump(out, f, indent=2, default=str)

    def wait_run(rid, tries=600):
        for _ in range(tries):
            _, d = req("GET", f"/api/admin/runs/{rid}", tok=tok)
            if terminal(d.get("run", {}).get("status")):
                return d
            time.sleep(0.4)
        raise SystemExit(f"run {rid} did not finish")

    def run_scenario(key, policy=None, full_coverage=None, with_geo=False):
        s, snap = load(key)
        if s != 200:
            return {"load_error": (s, snap)}
        rid = start_run(policy, full_coverage)
        d = wait_run(rid)
        run = d["run"]
        entry = {
            "snapshot": snap, "runId": rid, "status": run.get("status"),
            "solverStatus": run.get("solverStatus"), "totalRuntimeMs": run.get("totalRuntimeMs"),
            "infeasibleMessage": d.get("infeasibleMessage"),
        }
        s, comp = req("GET", f"/api/admin/runs/{rid}/comparison", tok=tok)
        if s == 200: entry["comparison"] = comp
        s, fair = req("GET", f"/api/admin/runs/{rid}/fairness", tok=tok)
        if s == 200:
            entry["fairness"] = {"ruralUrban": fair.get("ruralUrban"), "disparity": fair.get("disparity"),
                                 "stateRows": len(fair.get("states") or [])}
        if with_geo:
            s, geo = req("GET", f"/api/admin/runs/{rid}/geography", tok=tok)
            if s == 200:
                states = geo.get("states") if isinstance(geo, dict) else geo
                entry["geoStateRows"] = len(states or [])
                entry["geoSample"] = (states or [])[:3]
        s, conf = req("GET", f"/api/admin/runs/{rid}/conflicts", tok=tok)
        if s == 200: entry["conflictsTop"] = (conf or [])[:4]
        return entry

    # ---------- Phase A: the active STANDARD run ----------
    _, runs = req("GET", "/api/admin/runs?page=0&size=20", tok=tok)
    content = runs.get("content") or []
    std_run = next((r for r in content if r.get("scenario") == "Standard Showcase"
                    and "completed" in (r.get("status") or "").lower()), None)
    if not std_run:
        # no completed standard run (e.g. after a scenario swap) — load + run it now
        load("STANDARD_SHOWCASE")
        rid0 = start_run("balanced")
        wait_run(rid0)
        _, runs = req("GET", "/api/admin/runs?page=0&size=20", tok=tok)
        content = runs.get("content") or []
        std_run = next((r for r in content if r.get("scenario") == "Standard Showcase"
                        and terminal(r.get("status"))), None)
        if not std_run:
            raise SystemExit("no completed STANDARD run found")
    rid = std_run["id"]
    out["STANDARD_OVERVIEW"] = overview_counts()
    out["STANDARD_RUN"] = {"id": rid, "number": std_run.get("number"),
                           "solverStatus": std_run.get("solverStatus"),
                           "totalRuntimeMs": std_run.get("totalRuntimeMs")}
    s, comp = req("GET", f"/api/admin/runs/{rid}/comparison", tok=tok)
    out["STANDARD_COMPARISON"] = comp if s == 200 else (s, comp)
    s, fair = req("GET", f"/api/admin/runs/{rid}/fairness", tok=tok)
    if s == 200:
        out["STANDARD_FAIRNESS"] = {"ruralUrban": fair.get("ruralUrban"),
                                    "disparity": fair.get("disparity"),
                                    "stateRows": len(fair.get("states") or [])}
    s, geo = req("GET", f"/api/admin/runs/{rid}/geography", tok=tok)
    if s == 200:
        states = geo.get("states") if isinstance(geo, dict) else geo
        out["STANDARD_GEO_STATE_ROWS"] = len(states or [])
        out["STANDARD_GEO_SAMPLE"] = (states or [])[:3]
    s, conf = req("GET", f"/api/admin/runs/{rid}/conflicts", tok=tok)
    out["STANDARD_CONFLICTS_TOP"] = (conf or [])[:4] if s == 200 else (s, conf)

    def wait_sim(sid):
        row = None
        for _ in range(300):
            _, hist = req("GET", "/api/admin/simulations?page=0&size=5", tok=tok)
            row = next((r for r in (hist.get("content") or []) if r["id"] == sid), None)
            st = (row.get("status") or "").lower() if row else ""
            if st and any(k in st for k in ("completed", "infeasible", "failed", "cancel")):
                return row
            time.sleep(0.4)
        return row

    s, sim = req("POST", "/api/admin/simulations",
                 {"baseRunId": rid,
                  "weights": {"skills": 60.0, "interest": 10.0, "location": 15.0,
                              "qualification": 5.0, "experience": 5.0,
                              "learning": 3.0, "preference": 2.0}}, tok=tok)
    out["SIM_SKILLS"] = wait_sim(sim["id"]) if s == 202 else (s, sim)

    s, sim = req("POST", "/api/admin/simulations", {"baseRunId": rid, "fullCoverage": True}, tok=tok)
    out["SIM_FULL_COVERAGE"] = wait_sim(sim["id"]) if s == 202 else (s, sim)

    top = out["STANDARD_CONFLICTS_TOP"]
    if isinstance(top, list) and top:
        opp = top[0]
        s, ra = req("POST", "/api/admin/realizations".replace("realizations", "reallocations"),
                    {"baseRunId": rid,
                     "changes": [{"type": "CAPACITY", "opportunityId": opp["opportunityId"],
                                  "newCapacity": 1}]}, tok=tok)
        if s == 202:
            d = wait_run(ra["id"])
            s, diff = req("GET", f"/api/admin/realizations".replace("realizations", "reallocations") + f"/{ra['id']}/diff", tok=tok)
            out["REALLOCATION"] = {"opportunity": opp.get("title"), "eligible": opp.get("eligibleCount"),
                                   "seatsBefore": opp.get("seatCount"), "seatsAfter": 1,
                                   "runStatus": d["run"].get("status"),
                                   "diff": diff if s == 200 else (s, diff)}
        else:
            out["REALLOCATION"] = (s, ra)
    else:
        out["REALLOCATION"] = {"error": "no conflicts"}

    # ---------- Phase B: other scenarios ----------
    save()
    for key, policy in (("MICRO_CONFLICT", "balanced"), ("HIGH_CONFLICT", "balanced"),
                        ("GEOGRAPHIC", "balanced"), ("FAIRNESS", "equity"),
                        ("INFEASIBLE", "full_coverage"), ("BENCHMARK", "balanced")):
        out[key] = run_scenario(key, policy)
        save()

    # ---------- Phase C: restore STANDARD, fresh run ----------
    out["FINAL_STANDARD"] = run_scenario("STANDARD_SHOWCASE", "balanced", with_geo=True)
    out["FINAL_OVERVIEW"] = overview_counts()

    save()
    print("MEASURE_OK")

if __name__ == "__main__":
    main()
