import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import main
from main import GapGuidanceRequest, _build_gap_guidance


def _req(**kw):
    base = dict(
        question="weak_area",
        skill=None,
        opportunityTitle="Data Analytics Intern",
        sector="Data & Analytics",
        mandatorySkills=["Data Analysis", "SQL"],
        preferredSkills=["Power BI", "Statistics"],
        candidateSkills=["Data Analysis", "Excel"],
        eligible=True,
        factors=[{"key": "skills", "label": "Skills", "fit": 66.7, "weight": 35.0,
                  "contribution": 23.3, "lostPoints": 11.7,
                  "explanation": "You cover Data Analysis but not SQL."}],
        overall=71.2,
    )
    base.update(kw)
    return GapGuidanceRequest(**base)


def test_why_skill_explains_mandatory_role_without_promises():
    ans = _build_gap_guidance(_req(question="why_skill", skill="SQL"))
    assert "SQL" in ans
    assert "required" in ans.lower()
    assert "Data Analytics Intern" in ans


def test_why_skill_explains_preferred_role_not_eligibility():
    ans = _build_gap_guidance(_req(question="why_skill", skill="Power BI"))
    assert "Power BI" in ans
    a = ans.lower()
    assert "preferred" in a or "not required" in a


def test_weak_area_names_only_missing_skills_from_context():
    ans = _build_gap_guidance(_req())
    # Missing mandatory: SQL. Missing preferred: Power BI, Statistics.
    assert "SQL" in ans
    assert "Power BI" in ans
    # Already-covered skills must not be named as gaps.
    clause = ans.split("skills still missing are")[-1]
    assert "Excel" not in clause
    assert "Data Analysis" not in clause


def test_never_invents_guarantees_certificates_or_statistics():
    for q in ("why_skill", "weak_area", "learn_first", "improve_impact"):
        ans = _build_gap_guidance(_req(question=q, skill="SQL" if q == "why_skill" else None))
        low = ans.lower()
        for banned in ("guarantee", "guaranteed", "certified", "certificate of completion",
                       "offer letter", "selection is", "hired", "% increase",
                       "market rate", "90 days", "8 weeks"):
            assert banned not in low, f"{q}: fabricated-sounding phrase '{banned}' in: {ans}"


def test_ineligible_answer_names_the_blocking_skill():
    ans = _build_gap_guidance(_req(eligible=False))
    assert "SQL" in ans, "an ineligible candidate's guidance must name the blocking skill"


def test_answer_is_deterministic():
    a = _build_gap_guidance(_req())
    b = _build_gap_guidance(_req())
    assert a == b, "same inputs must produce the same grounded answer"


def test_route_contract():
    from fastapi.testclient import TestClient
    client = TestClient(main.app)
    res = client.post("/nlp/gap-guidance", json=_req().model_dump())
    assert res.status_code == 200
    body = res.json()
    assert body["engine"] == "deterministic-guidance"
    assert "answer" in body and "grounding" in body
    assert body["answer"]
