"""
Deterministic resume/skill intelligence.

Pipeline: raw text -> candidate skill mentions -> normalized labels ->
canonical taxonomy skills. Only taxonomy-validated skills are returned;
unvalidated labels never become trusted allocation input.

When a richer local model is not installed the engine is the deterministic
lexicon ("local-fallback"); when sentence-transformers is available the
engine can additionally rank mentions ("ai-assisted").
"""

import re

CANONICAL = [
    "Python", "Java", "C++", "SQL", "Data Analysis", "Machine Learning", "Excel",
    "Statistics", "Power BI", "Tableau", "Web Development", "React", "Node.js",
    "JavaScript", "HTML CSS", "Git", "Cloud Computing", "DevOps", "Cybersecurity",
    "NLP", "Deep Learning", "Communication", "Presentation", "Research",
    "Content Writing", "Graphic Design", "UI/UX Design", "Digital Marketing",
    "Social Media", "SEO", "Finance", "Accounting", "Budgeting",
    "Public Administration", "Policy Analysis", "HR Management", "Operations",
    "Supply Chain", "Legal Research", "Environmental Science", "Healthcare Data",
    "Project Management",
]

SYNONYMS = {
    "python3": "Python", "py": "Python",
    "java programming": "Java", "spring boot": "Java", "spring": "Java",
    "c plus plus": "C++", "cpp": "C++",
    "mysql": "SQL", "postgresql": "SQL", "postgres": "SQL", "sql server": "SQL", "databases": "SQL",
    "data analytics": "Data Analysis", "data science": "Data Analysis", "analytics": "Data Analysis",
    "ml": "Machine Learning", "ai models": "Machine Learning",
    "ms excel": "Excel", "spreadsheets": "Excel", "ms office": "Excel", "advanced excel": "Excel",
    "powerbi": "Power BI", "data visualization": "Tableau",
    "full stack": "Web Development", "full-stack": "Web Development", "web dev": "Web Development", "frontend": "Web Development",
    "react.js": "React", "reactjs": "React",
    "node": "Node.js", "node js": "Node.js", "nodejs": "Node.js",
    "js": "JavaScript", "html": "HTML CSS", "css": "HTML CSS", "html5": "HTML CSS", "tailwind": "HTML CSS",
    "git github": "Git", "github": "Git",
    "aws": "Cloud Computing", "azure": "Cloud Computing", "gcp": "Cloud Computing", "google cloud": "Cloud Computing",
    "docker": "DevOps", "kubernetes": "DevOps", "ci cd": "DevOps",
    "cyber security": "Cybersecurity",
    "natural language processing": "NLP",
    "neural networks": "Deep Learning",
    "public speaking": "Communication", "interpersonal": "Communication",
    "blogging": "Content Writing",
    "figma": "UI/UX Design", "ui design": "UI/UX Design", "ux design": "UI/UX Design", "product design": "UI/UX Design",
    "social media marketing": "Social Media",
    "search engine optimization": "SEO",
    "financial analysis": "Finance", "accountancy": "Accounting",
    "public policy": "Policy Analysis", "policy research": "Policy Analysis",
    "government": "Public Administration", "policy": "Policy Analysis",
    "human resources": "HR Management", "hr management": "HR Management",
    "operations management": "Operations",
    "supply chain management": "Supply Chain",
    "sustainability": "Environmental Science",
    "health data": "Healthcare Data", "healthcare analytics": "Healthcare Data",
    "agile": "Project Management", "scrum": "Project Management",
}

_PHRASE_RE = {
    canonical: re.compile(r"(?<![A-Za-z0-9+#])" + re.escape(canonical) + r"(?![A-Za-z0-9+#])", re.IGNORECASE)
    for canonical in CANONICAL
}
_SYNONYM_RE = {
    syn: (canonical, re.compile(r"(?<![A-Za-z0-9+#])" + re.escape(syn) + r"(?![A-Za-z0-9+#])", re.IGNORECASE))
    for syn, canonical in SYNONYMS.items()
}


def normalize(label):
    if not label:
        return None
    n = label.strip().lower()
    if not n:
        return None
    if n in SYNONYMS:
        return SYNONYMS[n]
    for canonical in CANONICAL:
        if canonical.lower() == n:
            return canonical
    return None


def extract_skills(text):
    """Scan text and return taxonomy-validated skill mentions.

    The engine is a deterministic lexicon + synonym table over the validated
    taxonomy. It is intentionally labelled exactly that — no embeddings are
    used for matching, and nothing is claimed beyond what the rules produce.
    """
    if not text:
        return [], "taxonomy-lexicon"
    lowered = text.lower()
    found = {}
    for canonical, rx in _PHRASE_RE.items():
        if rx.search(lowered):
            found[canonical] = canonical
    for syn, (canonical, rx) in _SYNONYM_RE.items():
        if syn in lowered and rx.search(lowered):
            found[canonical] = syn
    skills = [{"raw": raw, "canonical": canon, "confidence": 1.0} for canon, raw in sorted(found.items())]
    return skills, "taxonomy-lexicon"


def engine_mode():
    """Honest engine identity: deterministic taxonomy matching (no embeddings)."""
    return "deterministic-taxonomy"


def engine_description():
    return ("Deterministic taxonomy matching — validated skill lexicon with synonym "
            "normalization. No embedding model is used, and the engine only ever "
            "reports skills that exist in the validated taxonomy.")
