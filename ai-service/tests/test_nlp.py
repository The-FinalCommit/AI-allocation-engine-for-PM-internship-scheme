import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import nlp


def test_extract_canonical_skills():
    text = "Experienced in Python, SQL and machine learning. Built React dashboards with Power BI."
    skills, engine = nlp.extract_skills(text)
    canonicals = {s["canonical"] for s in skills}
    assert {"Python", "SQL", "Machine Learning", "React", "Power BI"} <= canonicals


def test_synonym_normalization():
    text = "Worked with mysql, ms excel and public speaking."
    skills, _ = nlp.extract_skills(text)
    canonicals = {s["canonical"] for s in skills}
    assert "SQL" in canonicals
    assert "Excel" in canonicals
    assert "Communication" in canonicals


def test_no_invented_skills():
    skills, _ = nlp.extract_skills("I like gardening and cooking pasta.")
    assert skills == []


def test_normalize():
    assert nlp.normalize("mysql") == "SQL"
    assert nlp.normalize("Python") == "Python"
    assert nlp.normalize("quantum flux") is None


def test_deterministic():
    text = "Python, Java, Data Analysis and research experience."
    a, _ = nlp.extract_skills(text)
    b, _ = nlp.extract_skills(text)
    assert a == b
