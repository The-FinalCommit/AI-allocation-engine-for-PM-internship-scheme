"""
PRAGATI AI service (FastAPI).

Provides:
  GET  /health        liveness
  GET  /info          AI mode, embedding model, optimizer identity
  POST /optimize      global allocation (OR-Tools CP-SAT) + sequential baseline
  POST /nlp/skills    resume text -> taxonomy-validated skills
  POST /nlp/normalize raw skill label -> canonical taxonomy skill

The AI service understands and explains; it never allocates by itself —
allocation decisions come from the deterministic constraint model above.
"""

import os

import ortools
from fastapi import FastAPI
from pydantic import BaseModel, Field

import nlp
import optimizer

app = FastAPI(
    title="PRAGATI AI Service",
    description="Skill intelligence and the global allocation engine for PRAGATI (SIH25033).",
    version="1.0.0",
)


class OptOpp(BaseModel):
    id: int
    capacity: int


class OptPair(BaseModel):
    c: int
    i: int
    score: int
    pref: int = 0


class OptGroup(BaseModel):
    key: str
    candidates: list[int]
    floorPct: int


class OptPolicy(BaseModel):
    fullCoverage: bool = False
    groups: list[OptGroup] = Field(default_factory=list)


class OptParams(BaseModel):
    maxTimeSeconds: int = 60
    workers: int = 1
    seed: int = 0


class OptimizeRequest(BaseModel):
    candidates: list[int]
    opportunities: list[OptOpp]
    pairs: list[OptPair]
    policy: OptPolicy = Field(default_factory=OptPolicy)
    params: OptParams = Field(default_factory=OptParams)


class SkillsRequest(BaseModel):
    text: str = ""


class NormalizeRequest(BaseModel):
    label: str


class GapFactor(BaseModel):
    key: str
    label: str
    fit: float
    weight: float
    contribution: float
    lostPoints: float


class GapGuidanceRequest(BaseModel):
    question: str  # why_skill | weak_area | learn_first | improve_impact
    skill: str | None = None
    opportunityTitle: str = ""
    sector: str = ""
    mandatorySkills: list[str] = Field(default_factory=list)
    preferredSkills: list[str] = Field(default_factory=list)
    candidateSkills: list[str] = Field(default_factory=list)
    eligible: bool = True
    factors: list[GapFactor] = Field(default_factory=list)
    overall: float = 0.0


@app.get("/health")
def health():
    return {"status": "UP"}


@app.get("/info")
def info():
    # Honest engine identity: the NLP engine is deterministic taxonomy
    # matching. No embedding model is used or claimed.
    return {
        "aiMode": nlp.engine_mode(),
        "engineDescription": nlp.engine_description(),
        "embeddingModel": None,
        "optimizer": "or-tools-cpsat",
        "optimizerVersion": ortools.__version__,
        "version": "1.0.0",
    }


@app.post("/optimize")
def optimize(req: OptimizeRequest):
    payload = req.model_dump()
    return optimizer.optimize(payload)


@app.post("/nlp/skills")
def skills(req: SkillsRequest):
    found, engine = nlp.extract_skills(req.text)
    return {"skills": found, "engine": engine}


@app.post("/nlp/normalize")
def normalize(req: NormalizeRequest):
    canonical = nlp.normalize(req.label)
    return {"canonical": canonical}


def _build_gap_guidance(req: GapGuidanceRequest) -> str:
    """Deterministic, strictly grounded guidance for the skill-gap assistant.

    Answers are composed only from the candidate's verified skills, the
    opportunity's stated requirements and the live score breakdown that the
    backend supplies. No certifications, employer claims, market statistics,
    achievements or guarantees are ever invented.
    """
    opp = req.opportunityTitle or "this internship"
    sector = f" in the {req.sector} sector" if req.sector else ""
    have = set(req.candidateSkills)
    mandatory = [s for s in req.mandatorySkills if s not in have]
    preferred_missing = [s for s in req.preferredSkills if s not in have]

    if req.question == "why_skill":
        skill = req.skill or ""
        if not skill:
            return ("Pick a specific skill from the list above and I will explain its role in "
                    + opp + ".")
        if skill in req.mandatorySkills:
            return (f"{skill} is a required skill for {opp}{sector}. Every candidate competing for "
                    f"its seats must have it, so it is part of the eligibility gate — and it also "
                    f"shapes the skills match in your fit score.")
        if skill in req.preferredSkills:
            return (f"{skill} is a preferred (not required) skill for {opp}{sector}. You remain "
                    f"eligible without it, but candidates who have it score higher on the skills "
                    f"factor, which is worth { _weight_of(req.factors, 'skills') }% of the fit score.")
        return (f"{skill} is not part of the stated requirements for {opp}. Improving it will not "
                f"change your fit for this particular internship; focus on the skills listed above "
                f"instead.")
    if req.question == "weak_area":
        head = ""
        if not req.eligible and mandatory:
            skills = ", ".join(mandatory[:2])
            more = f" and {len(mandatory) - 2} more" if len(mandatory) > 2 else ""
            head = (f"Right now the eligibility gate is what matters: {skills}{more} is required "
                    f"for {opp} and is missing from your profile. ")
        weak = [f for f in req.factors if f.lostPoints >= 0.5]
        weak.sort(key=lambda f: f.lostPoints, reverse=True)
        if not weak:
            return (head + "Your profile is balanced — no single factor is leaving meaningful "
                    "points on the table for this opportunity.")
        top = weak[0]
        skill_note = ""
        if top.key == "skills":
            parts = []
            if mandatory:
                parts.append(", ".join(mandatory[:3]) + " (required)")
            if preferred_missing:
                parts.append(", ".join(preferred_missing[:3]) + " (preferred)")
            if parts:
                skill_note = " For this internship the skills still missing are " + " and ".join(parts) + "."
        rest = [f.label for f in weak[1:] if f.label != top.label][:1]
        tail = f" Next comes {rest[0]}." if rest else ""
        return (head + f"Relative to {opp}, your weakest lever is {top.label}: you are at "
                f"{top.fit:.0f}% fit on a factor worth {top.weight:.0f}%, leaving about "
                f"{top.lostPoints:.1f} points unused.{skill_note}{tail} The score card on this "
                f"page shows the same numbers.")
    if req.question == "learn_first":
        if mandatory:
            first = mandatory[0]
            rest = f" and {', '.join(mandatory[1:3])}" if len(mandatory) > 1 else ""
            return (f"Start with {first}{rest} — these are required for {opp}. Without them you "
                    f"are not eligible, and no amount of other strength can compensate. Adding a "
                    f"required skill is the only change that turns 'not eligible' into 'eligible'.")
        if preferred_missing:
            first = preferred_missing[0]
            return (f"You are already eligible. The fastest fit gain is {first}, a preferred skill "
                    f"for {opp}: it directly raises your skills match without changing anything "
                    f"else in your profile.")
        return ("You cover every required and preferred skill for this internship. To raise your "
                "overall fit further, look at the non-skill factors on the score card — for "
                "example your stated preference rank or your location relative to the host city.")
    if req.question == "improve_impact":
        skill = req.skill or ""
        w = _weight_of(req.factors, "skills")
        if skill in have:
            return (f"You already list {skill} as a verified skill, so it is already counted in "
                    f"your skills match for {opp}.")
        if skill in req.mandatorySkills:
            return (f"Adding {skill} would not change your numeric fit here — it would change your "
                    f"eligibility: with it, {opp} opens up to you as a candidate at all.")
        if skill in req.preferredSkills:
            return (f"Adding {skill} would raise your skills factor, which is weighted {w:.0f}%. "
                    f"Because preferred skills are scored proportionally, the gain is partial — the "
                    f"score card recalculates it exactly once the skill is verified on your profile.")
        return (f"{skill} is not required or preferred for {opp}, so adding it would not move your "
                f"fit for this internship.")
    return ("I can only answer grounded questions: why a listed skill matters, where your profile "
            "is weak for this opportunity, what to learn first, and how an improvement would move "
            "your fit.")


def _weight_of(factors, key: str) -> float:
    for f in factors:
        if f.key == key:
            return f.weight
    return 0.0


@app.post("/nlp/gap-guidance")
def gap_guidance(req: GapGuidanceRequest):
    """Grounded skill-gap guidance. Deterministic templates over the supplied
    structured context — never invented facts, and never a substitute for
    the eligibility or allocation engine."""
    return {
        "answer": _build_gap_guidance(req),
        "engine": "deterministic-guidance",
        "grounding": "structured opportunity requirements + your verified profile",
    }
