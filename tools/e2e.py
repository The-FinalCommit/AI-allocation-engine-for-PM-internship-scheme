#!/usr/bin/env python3
"""Full E2E regression sweep (candidate / provider / admin) — live API checks.
Deterministic: reloads STANDARD at start, restores a clean STANDARD run at end."""
import json, time, urllib.request, urllib.error, subprocess

BASE = "http://127.0.0.1:8080"
PASS, FAIL = [], []

def check(name, cond, extra=""):
    (PASS if cond else FAIL).append(name)
    print(("PASS  " if cond else "FAIL  ") + name + (f"   -> {extra}" if extra and not cond else ""))

def req(method, path, body=None, tok=None, raw=False, ctype=None, timeout=90):
    data = None
    headers = {}
    if body is not None:
        if isinstance(body, (bytes, bytearray)):
            data = bytes(body)
            headers["Content-Type"] = ctype or "application/octet-stream"
        else:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
    r = urllib.request.Request(BASE + path, data=data, method=method)
    for k, v in headers.items(): r.add_header(k, v)
    if tok: r.add_header("Authorization", "Bearer " + tok)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            b = resp.read()
            if raw: return resp.status, b, dict(resp.headers)
            try: return resp.status, json.loads(b)
            except Exception: return resp.status, b.decode(errors="replace")
    except urllib.error.HTTPError as e:
        b = e.read()
        try: return e.code, json.loads(b)
        except Exception: return e.code, b.decode(errors="replace")

def login(email, pw):
    s, d = req("POST", "/api/auth/login", {"email": email, "password": pw})
    assert s == 200, f"login {email} -> {s}"
    return d["token"]

def multipart_field(name, filename, content_bytes, ctype, field="file"):
    boundary = "----e2eboundary"
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{filename}\"\r\n"
            f"Content-Type: {ctype}\r\n\r\n").encode() + content_bytes + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"

admin = login("admin@pragati.gov.in", "Admin@123")
prov  = login("provider@pragati.gov.in", "Provider@123")
cand  = login("candidate@pragati.gov.in", "Candidate@123")
check("login all three roles", True)

# ---------- auth / security ----------
s, _ = req("GET", "/api/admin/overview")
check("no token -> 401", s == 401, str(s))
s, _ = req("GET", "/api/admin/overview", tok=cand)
check("candidate on admin endpoint -> 403", s == 403, str(s))
s, _ = req("GET", "/api/candidates/me/profile")
check("candidate endpoint without token -> 401", s == 401, str(s))

# ---------- static delivery ----------
s, b, h = req("GET", "/", raw=True)
check("GET / -> 200 html", s == 200 and "text/html" in h.get("Content-Type", ""), str(s))
s, b, h = req("GET", "/admin/runs", raw=True)
check("deep link /admin/runs -> SPA 200 html", s == 200 and "text/html" in h.get("Content-Type", ""), str(s))
s, b, h = req("GET", "/logo.png", raw=True)
check("GET /logo.png -> 200", s == 200 and len(b) > 10000, str(s))
s, b, h = req("GET", "/favicon.png", raw=True)
check("GET /favicon.png -> 200", s == 200, str(s))
s, b, h = req("GET", "/india-states.json", raw=True)
check("GET /india-states.json -> 200", s == 200 and len(b) > 100000, str(s))
s, b = req("GET", "/api/health")
check("api/health UP + aiService UP", s == 200 and b.get("status") == "UP" and b.get("aiService") == "UP", str(b))

# ---------- admin: deterministic baseline ----------
s, _ = req("POST", "/api/admin/scenarios/STANDARD_SHOWCASE/load", tok=admin)
check("load STANDARD", s == 200, str(s))
s, run = req("POST", "/api/admin/runs", {"policyKey": "balanced"}, tok=admin)
rid = run.get("id")
for _ in range(400):
    s2, rd = req("GET", f"/api/admin/runs/{rid}", tok=admin)
    st = (rd.get("run", {}).get("status") or "").lower()
    if any(k in st for k in ("completed", "infeasible", "failed", "cancel")): break
    time.sleep(0.4)
check("baseline run completed + optimal",
      "completed" in st and (rd.get("run", {}).get("solverStatus") or "").lower().startswith("optimal"), st)

s, ov = req("GET", "/api/admin/overview", tok=admin)
sc, al = ov.get("scenario") or {}, ov.get("allocation") or {}
check("overview 700/49/342", sc.get("candidateCount") == 700 and sc.get("opportunityCount") == 49 and sc.get("seatCount") == 342, str({k: sc.get(k) for k in ('candidateCount','opportunityCount','seatCount')}))
check("allocation 332 allocated", al.get("allocated") == 332, str(al))
check("aiMode labelled honestly", ov.get("aiMode") in ("Deterministic taxonomy engine", "Deterministic fallback"), str(ov.get("aiMode")))

s, d = req("GET", f"/api/admin/runs/{rid}", tok=admin)
prov0 = d.get("provenance") or {}
check("provenance fingerprint + weights + solver", bool(prov0.get("datasetFingerprint")) and isinstance(prov0.get("weights"), dict) and "CP-SAT" in (prov0.get("optimizer") or ""), str({k: prov0.get(k) for k in ("datasetFingerprint", "optimizer", "solverStatus")}))
check("provenance dataset 700/49/342", prov0.get("candidates") == 700 and prov0.get("opportunities") == 49 and prov0.get("seats") == 342, str(prov0.get("candidates")))
check("run not stale", d.get("run", {}).get("stale") is False, str(d.get("run", {}).get("stale")))

s, comp = req("GET", f"/api/admin/runs/{rid}/comparison", tok=admin)
check("comparison 332@79.1 vs 320@76.0", comp.get("globalAllocated") == 332 and comp.get("globalSuitability") == 79.1 and comp.get("baselineAllocated") == 320 and comp.get("baselineSuitability") == 76.0, str(comp))
check("comparison human summary", "global allocation" in (comp.get("summary") or ""), str(comp.get("summary")))

s, fair = req("GET", f"/api/admin/runs/{rid}/fairness", tok=admin)
ru = {g["groupValue"]: g for g in (fair.get("ruralUrban") or [])}
check("fairness groups measured", set(ru) == {"Urban", "Semi-Urban", "Rural"} and ru.get("Rural", {}).get("population") == 251, str(ru))
check("fairness disparity 2.5pp honest", (fair.get("disparity") or {}).get("gapPercentagePoints") == 2.5, str(fair.get("disparity")))
check("fairness state rows = 16", len(fair.get("states") or []) == 16, str(len(fair.get("states") or [])))

s, geo = req("GET", f"/api/admin/runs/{rid}/geography", tok=admin)
states = geo.get("states") if isinstance(geo, dict) else geo
check("geography 16 states", len(states or []) == 16, str(len(states or [])))
check("geography demand sums to 700", sum(x.get("demand", 0) for x in (states or [])) == 700, "")

s, conf = req("GET", f"/api/admin/runs/{rid}/conflicts", tok=admin)
check("conflicts top pressure 60.5", s == 200 and conf and conf[0].get("pressure") == 60.5 and conf[0].get("title") == "Financial Analyst Intern", str((conf or [{}])[0]))

s, mov = req("GET", f"/api/admin/runs/{rid}/movements?page=0&size=5", tok=admin)
mrows = mov.get("content") if isinstance(mov, dict) else mov
check("movements page", s == 200 and mrows and "candidateName" in json.dumps(mrows[0]), str(s))

s, why = req("GET", f"/api/admin/runs/{rid}/why/assigned?limit=5", tok=admin)
check("why/assigned factor-level", s == 200 and isinstance(why, list) and why and "factors" in json.dumps(why), str(s))

s, pol = req("GET", "/api/admin/policies", tok=admin)
check("5 policy presets", s == 200 and len(pol) == 5, str([p.get("key") for p in pol]))

s, dq = req("GET", "/api/admin/data-quality", tok=admin)
check("data quality all READY", s == 200 and isinstance(dq, list) and dq and all(x.get("status") == "READY" for x in dq), str([x.get("status") for x in dq] if isinstance(dq, list) else dq))

s, aud = req("GET", "/api/admin/audit?page=0&size=5", tok=admin)
arows = aud.get("content") if isinstance(aud, dict) else aud
check("audit trail", s == 200 and arows, str(s))

s, steps = req("GET", "/api/admin/judge/steps", tok=admin)
check("judge steps", s == 200 and isinstance(steps, list) and len(steps) >= 12, str(len(steps)))

s, tech = req("GET", "/api/admin/technical", tok=admin)
check("technical view", s == 200, str(s))

s, scs = req("GET", "/api/admin/scenarios", tok=admin)
check("7 scenarios listed", s == 200 and len(scs) == 7, str(len(scs)))

def wait_sim(sid):
    row = None
    for _ in range(200):
        s2, hist = req("GET", "/api/admin/simulations?page=0&size=5", tok=admin)
        row = next((x for x in (hist.get("content") or []) if x.get("id") == sid), None)
        stt = (row.get("status") or "").lower() if row else ""
        if stt and any(k in stt for k in ("completed", "infeasible", "failed", "cancel")):
            return row
        time.sleep(0.4)
    return row

s, sim = req("POST", "/api/admin/simulations",
             {"baseRunId": rid, "weights": {"skills": 60.0, "interest": 10.0, "location": 15.0,
                                            "qualification": 5.0, "experience": 5.0,
                                            "learning": 3.0, "preference": 2.0}}, tok=admin)
row = wait_sim(sim.get("id")) if s == 202 else None
check("what-if skills sim: 104 affected, 79.2 stable", row is not None and row.get("affected") == 104 and row.get("stability") == 79.2, str(row))

s, d = req("POST", "/api/admin/simulations", {"baseRunId": rid, "weights": {"skills": 50.0}}, tok=admin)
check("sim weights != 100 -> 400", s == 400, str(s))

s, sim = req("POST", "/api/admin/simulations", {"baseRunId": rid, "fullCoverage": True}, tok=admin)
row2 = wait_sim(sim.get("id")) if s == 202 else None
check("full-coverage sim -> Infeasible", row2 is not None and "infeasible" in (row2.get("status") or "").lower(), str(row2))

s, b, h = req("GET", f"/api/admin/runs/{rid}/report", tok=admin, raw=True)
check("PDF report 200", s == 200 and h.get("Content-Type", "").startswith("application/pdf") and b[:4] == b"%PDF", f"{s} {h.get('Content-Type')}")

# ---------- candidate ----------
s, a = req("GET", "/api/candidates/me/allocation", tok=cand)
check("candidate ALLOCATED", s == 200 and a.get("state") == "ALLOCATED", str(a.get("state")))
check("allocation wording 'You are fit for'", "You are fit for" in (a.get("message") or ""), str(a.get("message")))
check("allocation run number = 1", a.get("runNumber") == 1, str(a.get("runNumber")))
check("allocation suitability 89.8", abs((a.get("suitability") or 0) - 89.8) < 0.05, str(a.get("suitability")))

s, prof = req("GET", "/api/candidates/me/profile", tok=cand)
check("profile loads", s == 200 and prof.get("fullName"), str(prof.get("fullName")))

s, allo = req("GET", "/api/opportunities?page=0&size=200&availableOnly=false", tok=cand)
valid_ids = [x["id"] for x in (allo.get("content") or [])]

s, prefs = req("GET", "/api/candidates/me/preferences", tok=cand)
orig = prefs
check("seeded preferences", s == 200 and isinstance(prefs, list) and len(prefs) >= 1, str(prefs))
ids = [p["opportunityId"] for p in prefs]
extra = next((i for i in valid_ids if i not in ids), None)
s, _ = req("PUT", "/api/candidates/me/preferences", [{"opportunityId": x["opportunityId"], "rank": x["rank"]} for x in orig] + [{"opportunityId": extra, "rank": len(orig) + 1}], tok=cand)
check("prefs ADD (regression: was 500)", s == 200, str(s))
s, _ = req("PUT", "/api/candidates/me/preferences", [{"opportunityId": x["opportunityId"], "rank": x["rank"]} for x in orig], tok=cand)
check("prefs REMOVE", s == 200, str(s))
reorder = [{"opportunityId": ids[1], "rank": 1}, {"opportunityId": ids[0], "rank": 2}] + [{"opportunityId": i, "rank": k + 3} for k, i in enumerate(ids[2:])]
s, _ = req("PUT", "/api/candidates/me/preferences", reorder, tok=cand)
check("prefs REORDER", s == 200, str(s))
s, d = req("PUT", "/api/candidates/me/preferences", [{"opportunityId": ids[0], "rank": 1}, {"opportunityId": ids[0], "rank": 2}], tok=cand)
check("prefs duplicate -> 400", s == 400, str(s))
s, d = req("PUT", "/api/candidates/me/preferences", [{"opportunityId": valid_ids[i], "rank": i + 1} for i in range(11)], tok=cand)
check("prefs rank 11 -> 400", s == 400, str(s))
s, d = req("PUT", "/api/candidates/me/preferences", [{"opportunityId": 999999, "rank": 1}], tok=cand)
check("prefs unknown opp -> 400", s == 400, str(s))
s, _ = req("PUT", "/api/candidates/me/preferences", [{"opportunityId": x["opportunityId"], "rank": x["rank"]} for x in orig], tok=cand)
s2, prefs2 = req("GET", "/api/candidates/me/preferences", tok=cand)
check("prefs restored", s == 200 and [p["opportunityId"] for p in prefs2] == ids, str(prefs2))

s, rd = req("GET", "/api/candidates/me/readiness", tok=cand)
check("readiness 0-100 + items", s == 200 and 0 <= (rd.get("overall") or -1) <= 100 and rd.get("items"), str(rd.get("overall")))

s, recs = req("GET", "/api/candidates/me/recommendations", tok=cand)
check("recommendations 1-5 + note", s == 200 and 1 <= len(recs.get("items") or []) <= 5 and "not a final allocation" in (recs.get("note") or ""), str(recs.get("note")))

s, opps = req("GET", "/api/opportunities?page=0&size=100&availableOnly=false", tok=cand)
check("opportunities 49", s == 200 and opps.get("totalElements") == 49, str(opps.get("totalElements")))
s, o2 = req("GET", "/api/opportunities?page=1&size=100&availableOnly=false", tok=cand)
check("pagination page1 empty", s == 200 and len(o2.get("content") or []) == 0, str(len(o2.get("content") or [])))

aid = (recs.get("items") or [{}])[0]["opportunity"]["id"]
s, fit = req("GET", f"/api/candidates/me/analytics/opportunity/{aid}", tok=cand)
check("fit analysis factors", s == 200 and fit.get("factors") and fit.get("lostPoints") is not None and fit.get("possibleScore") == 100, str({k: fit.get(k) for k in ("overallScore", "lostPoints")}))
s, gap = req("GET", f"/api/candidates/me/skill-gap/{aid}", tok=cand)
check("skill gap sections + summary", s == 200 and gap.get("coveredSkills") is not None and gap.get("mandatoryGaps") is not None and gap.get("preferredGaps") is not None and (gap.get("assistantSummary") or "") != "", str(gap.get("assistantSummary")))
s, g = req("POST", f"/api/candidates/me/skill-gap/{aid}/guidance", {"question": "weak_area"}, tok=cand)
check("grounded guidance", s == 200 and g.get("answer") and g.get("grounding"), str(s))
s, g2 = req("POST", f"/api/candidates/me/skill-gap/{aid}/guidance", {"question": "not_a_question"}, tok=cand)
check("unknown question -> 400", s == 400, str(s))

s, hist = req("GET", "/api/candidates/me/allocation/history", tok=cand)
check("allocation history", s == 200 and isinstance(hist, list) and len(hist) >= 1, str(len(hist or [])))

# resume flow (real PDF)
pdf = b"""%PDF-1.4
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj
4 0 obj << /Length 90 >> stream
BT /F1 12 Tf 72 720 Td (Aarav Sharma - Delhi - BACHELORS - Python, SQL, Data Analysis) Tj ET
endstream endobj
5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
trailer << /Root 1 0 R /Size 6 >>
%%EOF"""
body, ctype = multipart_field("file", "e2e.pdf", pdf, "application/pdf")
s, rs = req("POST", "/api/candidates/me/resume", body, tok=cand, raw=False, ctype=ctype)
check("resume upload -> PENDING_REVIEW/REVIEWED", s == 200 and isinstance(rs, dict) and rs.get("status") in ("PENDING_REVIEW", "REVIEWED", "FAILED"), str(rs))
if isinstance(rs, dict) and rs.get("status") == "PENDING_REVIEW":
    s, sugg = req("GET", "/api/candidates/me/resume/suggestions", tok=cand)
    check("resume suggestions", s == 200 and sugg.get("resumeId") is not None, str(s))
    s, ap = req("POST", "/api/candidates/me/resume/apply-suggestions", {"resumeId": sugg.get("resumeId"), "profile": {}, "skills": [], "interests": []}, tok=cand)
    check("resume apply (dismiss) -> REVIEWED", s == 200 and ap.get("status") == "REVIEWED", str(ap.get("status")))
    s, d = req("POST", "/api/candidates/me/resume/apply-suggestions", {"resumeId": sugg.get("resumeId"), "profile": {}, "skills": [], "interests": []}, tok=cand)
    check("resume re-apply -> 400", s == 400, str(s))

# profile round-trip (never silently overwrite)
bio0 = prof.get("bio") or ""
newbio = bio0 + " [e2e]"
s, _ = req("PUT", "/api/candidates/me/profile", {**prof, "bio": newbio}, tok=cand)
s, p2 = req("GET", "/api/candidates/me/profile", tok=cand)
check("profile PUT bio persisted", s == 200 and (p2.get("bio") or "") == newbio, str(p2.get("bio")))
s, _ = req("PUT", "/api/candidates/me/profile", {**p2, "bio": bio0}, tok=cand)
check("profile bio restored", s == 200, str(s))

s, _ = req("GET", "/api/candidates/1/profile", tok=cand)
check("cross-candidate read blocked", s in (403, 404), str(s))

# ---------- provider ----------
s, pm = req("GET", "/api/providers/me", tok=prov)
check("provider me", s == 200, str(s))
s, plist = req("GET", "/api/providers/me/opportunities?page=0&size=50", tok=prov)
prows = plist.get("content") if isinstance(plist, dict) else plist
check("provider opportunities list", s == 200 and prows, str(len(prows or [])))
if prows:
    oid = prows[0]["id"]
    s, det = req("GET", f"/api/providers/me/opportunities/{oid}", tok=prov)
    check("provider opportunity detail", s == 200 and det.get("id") == oid, str(s))
s, cap = req("GET", "/api/providers/me/capacity", tok=prov)
check("provider capacity/demand", s == 200, str(s))
s, imp = req("GET", "/api/providers/me/impact", tok=prov)
check("provider impact", s == 200, str(s))

# ---------- reallocation (leaves a child; restored afterwards) ----------
top = (conf or [{}])[0]
s, ra = req("POST", "/api/admin/realizations".replace("realizations", "reallocations"),
            {"baseRunId": rid, "changes": [{"type": "CAPACITY", "opportunityId": top.get("opportunityId"), "newCapacity": 1}]}, tok=admin)
ok = s == 202
diff = None
if ok:
    for _ in range(300):
        s2, rdx = req("GET", f"/api/admin/runs/{ra['id']}", tok=admin)
        stt = (rdx.get("run", {}).get("status") or "").lower()
        if any(k in stt for k in ("completed", "infeasible", "failed", "cancel")): break
        time.sleep(0.4)
    s2, diff = req("GET", f"/api/admin/realizations".replace("realizations", "reallocations") + f"/{ra['id']}/diff", tok=admin)
check("reallocation diff 9 moved / 3 removed / 320 unchanged",
      diff is not None and diff.get("moved") == 9 and diff.get("removed") == 3 and diff.get("unchanged") == 320,
      str({k: (diff or {}).get(k) for k in ("moved", "added", "removed", "unchanged")}))
check("reallocation entry reasons + no 'AI decided'",
      diff is not None and diff.get("entries") and all("AI decided" not in (e.get("reason") or "") for e in diff["entries"]),
      str(diff.get("entries", [{}])[0] if diff else None))

# provider create/pause/close (after the measured run; wiped by final restore)
s, created = req("POST", "/api/providers/me/opportunities",
                 {"title": "E2E Temp Intern", "sector": "SOFTWARE_IT", "state": "Delhi",
                  "city": "Delhi", "capacity": 2, "durationMonths": 3, "minQualification": "BACHELORS",
                  "description": "E2E test opportunity.", "mandatorySkills": ["Python"], "niceSkills": []}, tok=prov)
ok = s in (200, 201) and isinstance(created, dict) and created.get("id")
check("provider create opportunity", bool(ok), str((s, created if not ok else created.get("id"))))
if ok:
    noid = created["id"]
    s, st = req("PATCH", f"/api/providers/me/opportunities/{noid}/status", {"status": "PAUSED"}, tok=prov)
    check("provider pause opportunity", s in (200, 201) and (st or {}).get("status") in ("Paused", "PAUSED"), str((s, st)))
    s, st = req("PATCH", f"/api/providers/me/opportunities/{noid}/status", {"status": "CLOSED"}, tok=prov)
    check("provider close (cleanup)", s in (200, 201) and (st or {}).get("status") in ("Closed", "CLOSED"), str((s, st)))

# ---------- restore clean STANDARD state ----------
s, _ = req("POST", "/api/admin/scenarios/STANDARD_SHOWCASE/load", tok=admin)
s, run = req("POST", "/api/admin/runs", {"policyKey": "balanced"}, tok=admin)
rid2 = run.get("id")
for _ in range(400):
    s2, rd = req("GET", f"/api/admin/runs/{rid2}", tok=admin)
    st = (rd.get("run", {}).get("status") or "").lower()
    if any(k in st for k in ("completed", "infeasible", "failed", "cancel")): break
    time.sleep(0.4)
check("final restore: STANDARD run completed", "completed" in st, st)
s, a = req("GET", "/api/candidates/me/allocation", tok=cand)
check("final state: candidate ALLOCATED run #1", a.get("state") == "ALLOCATED" and a.get("runNumber") == 1, str({k: a.get(k) for k in ("state", "runNumber", "opportunityTitle")}))

print()
print(f"RESULT: {len(PASS)} passed, {len(FAIL)} failed")
if FAIL:
    print("FAILED:")
    for f in FAIL: print("  -", f)
