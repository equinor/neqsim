"""Engineering-basis capture and completeness checks for process-study reports.

The basis is a structured YAML/JSON document that is written BEFORE the report.
``check_basis`` returns findings (errors/warnings) instead of raising, so the
validation report can list every gap. Items that are unknown must be written as
explicit ``unavailable`` entries (with their effect on conclusions), never
guessed or left blank.
"""
import json
import os
import re

DATA_CLASSES = ("measured", "specified", "calculated", "estimated", "unavailable")
REQUIREMENT_CLASSES = ("mandatory", "recommended", "informative")
REQUIREMENT_STATUSES = ("met", "partially met", "not met", "not applicable", "not assessed")
READINESS_LEVELS = ("SCREENING", "CONCEPT", "ISSUED FOR REVIEW", "ISSUED FOR DESIGN", "ISSUED FOR INFORMATION",
                    "ISSUED FOR USE")

PLACEHOLDER_RE = re.compile(
    r"\b(TBD|TODO|TBC|FIXME|XXX|PLACEHOLDER|lorem ipsum)\b|\[(insert|paste|describe|fill|add|your)[^\]]*\]"
    r"|<!--|\{\{|\}\}|\?\?\?|<(?:name|date|value|text)>|(?<!\[)\[[^\[\]\d:|]{4,}\](?!\])", re.IGNORECASE)

REQUIRED_DOCUMENT = ("title", "document_number", "revision", "issue_status", "readiness", "date", "company", "project")
REQUIRED_CONVENTIONS = ("units", "pressure", "composition_basis", "standard_conditions", "heat_sign", "work_sign",
                        "enthalpy_reference", "phase_fraction_basis")
REQUIRED_TOP = ("objective", "audience", "maturity", "boundaries", "operating_cases", "acceptance_criteria",
                "inspected_sources", "model", "conventions", "data_classification", "assumptions", "exclusions",
                "data_gaps")


def load_structured(path):
    """Load a YAML or JSON file into a dict."""
    with open(path, "r", encoding="utf-8") as fh:
        text = fh.read()
    if path.lower().endswith((".yaml", ".yml")):
        import yaml

        return yaml.safe_load(text)
    return json.loads(text)


def placeholder_hits(text):
    """Return placeholder-like fragments found in ``text``."""
    return [m.group(0) for m in PLACEHOLDER_RE.finditer(str(text or ""))]


def _finding(level, code, message, where=""):
    return {"level": level, "code": code, "message": message, "where": where}


def _walk_strings(node, path=""):
    if isinstance(node, str):
        yield path, node
    elif isinstance(node, dict):
        for key, value in node.items():
            yield from _walk_strings(value, "%s.%s" % (path, key) if path else str(key))
    elif isinstance(node, list):
        for i, value in enumerate(node):
            yield from _walk_strings(value, "%s[%d]" % (path, i))


def check_basis(basis):
    """Check a basis dict for completeness and honesty. Returns a list of findings."""
    out = []
    if not isinstance(basis, dict):
        return [_finding("error", "BASIS-TYPE", "basis is not a mapping")]
    for key in REQUIRED_TOP:
        if key not in basis or basis[key] in (None, "", [], {}):
            out.append(_finding("error", "BASIS-MISSING", "required basis item '%s' is missing or empty" % key, key))
    doc = basis.get("document") or {}
    for key in REQUIRED_DOCUMENT:
        if not doc.get(key):
            out.append(_finding("error", "BASIS-DOC", "document.%s is missing" % key, "document." + key))
    if doc.get("readiness") and str(doc["readiness"]).upper() not in READINESS_LEVELS:
        out.append(_finding("warning", "BASIS-READINESS",
                            "readiness '%s' is not one of %s" % (doc["readiness"], ", ".join(READINESS_LEVELS)),
                            "document.readiness"))
    conv = basis.get("conventions") or {}
    for key in REQUIRED_CONVENTIONS:
        if not conv.get(key):
            out.append(_finding("error", "BASIS-CONVENTION", "conventions.%s is not defined" % key, "conventions." + key))
    std = conv.get("standard_conditions") or {}
    if isinstance(std, dict) and not ("t_c" in std and "p_bara" in std):
        out.append(_finding("error", "BASIS-STDCOND", "conventions.standard_conditions needs t_c and p_bara",
                            "conventions.standard_conditions"))
    boundaries = basis.get("boundaries") or {}
    if isinstance(boundaries, dict):
        for key in ("included", "excluded"):
            if key not in boundaries:
                out.append(_finding("error", "BASIS-BOUNDARY", "boundaries.%s is missing" % key, "boundaries." + key))
    for i, case in enumerate(basis.get("operating_cases") or []):
        for key in ("id", "label", "description", "data_source", "role"):
            if not case.get(key):
                out.append(_finding("error", "BASIS-CASE", "operating_cases[%d].%s missing" % (i, key),
                                    "operating_cases[%d]" % i))
    for i, crit in enumerate(basis.get("acceptance_criteria") or []):
        for key in ("id", "text", "source", "requirement_class"):
            if not crit.get(key):
                out.append(_finding("error", "BASIS-CRITERIA", "acceptance_criteria[%d].%s missing" % (i, key),
                                    "acceptance_criteria[%d]" % i))
        if crit.get("requirement_class") and crit["requirement_class"] not in REQUIREMENT_CLASSES:
            out.append(_finding("error", "BASIS-CRITERIA", "acceptance_criteria[%d].requirement_class must be one of %s"
                                % (i, REQUIREMENT_CLASSES), "acceptance_criteria[%d]" % i))
    for i, src in enumerate(basis.get("inspected_sources") or []):
        for key in ("id", "kind", "title", "status"):
            if not src.get(key):
                out.append(_finding("error", "BASIS-SOURCE", "inspected_sources[%d].%s missing" % (i, key),
                                    "inspected_sources[%d]" % i))
    for i, item in enumerate(basis.get("data_classification") or []):
        if item.get("class") not in DATA_CLASSES:
            out.append(_finding("error", "BASIS-CLASS", "data_classification[%d].class must be one of %s"
                                % (i, DATA_CLASSES), "data_classification[%d]" % i))
    classes = {item.get("class") for item in basis.get("data_classification") or []}
    for needed in ("calculated", "unavailable"):
        if needed not in classes:
            out.append(_finding("warning", "BASIS-CLASS-COVERAGE",
                                "data_classification contains no '%s' entry; state explicitly if none exist" % needed,
                                "data_classification"))
    for i, gap in enumerate(basis.get("data_gaps") or []):
        for key in ("id", "item", "effect_on_conclusions"):
            if not gap.get(key):
                out.append(_finding("error", "BASIS-GAP", "data_gaps[%d].%s missing (effect on conclusions must be stated)"
                                    % (i, key), "data_gaps[%d]" % i))
    model = basis.get("model") or {}
    for key in ("name", "version", "simulator", "eos", "mixing_rule", "fluid_characterization", "convergence"):
        if not model.get(key):
            out.append(_finding("error", "BASIS-MODEL", "model.%s is missing" % key, "model." + key))
    for mi, mat in enumerate(basis.get("requirement_matrices") or []):
        for key in ("title", "source", "items"):
            if not mat.get(key):
                out.append(_finding("error", "BASIS-MATRIX", "requirement_matrices[%d].%s missing" % (mi, key), "requirement_matrices[%d]" % mi))
        for item in mat.get("items") or []:
            if item.get("status") not in REQUIREMENT_STATUSES:
                out.append(_finding("error", "BASIS-MATRIX", "%s: status must be one of %s" % (item.get("id"), REQUIREMENT_STATUSES), item.get("id", "")))
            if not item.get("evidence"):
                out.append(_finding("error", "BASIS-MATRIX", "%s: evidence or reason is required for every status" % item.get("id"), item.get("id", "")))
    for path, text in _walk_strings(basis):
        hits = placeholder_hits(text)
        if hits:
            out.append(_finding("error", "BASIS-PLACEHOLDER", "placeholder text %s" % hits[:3], path))
    return out


STANDARD_FIELDS = ("reference", "title", "edition", "status", "source_url", "date_checked", "application",
                   "requirement_class", "compliance_statement", "deviations", "text_available")


def check_standards(register):
    """Check a standards register (list of dicts) for edition/source/date evidence."""
    out = []
    if not register:
        return [_finding("error", "STD-EMPTY", "standards register is empty")]
    for i, row in enumerate(register):
        ref = row.get("reference", "#%d" % i)
        for key in STANDARD_FIELDS:
            if row.get(key) in (None, ""):
                out.append(_finding("error", "STD-FIELD", "%s: field '%s' missing" % (ref, key), ref))
        if row.get("requirement_class") and row["requirement_class"] not in REQUIREMENT_CLASSES:
            out.append(_finding("error", "STD-CLASS", "%s: requirement_class must be one of %s" % (ref, REQUIREMENT_CLASSES), ref))
        statement = str(row.get("compliance_statement", "")).lower()
        if row.get("text_available") in (False, "no", "No") and re.search(r"\b(complies|compliant|full compliance|conforms)\b",
                                                                             statement) and "not" not in statement:
            out.append(_finding("error", "STD-OVERCLAIM",
                                "%s: compliance claimed although the standard text was not available" % ref, ref))
        for path, text in _walk_strings(row):
            if placeholder_hits(text):
                out.append(_finding("error", "STD-PLACEHOLDER", "%s: placeholder text in %s" % (ref, path), ref))
    return out


def skill_dir():
    """Folder of the skill (templates and references); works in the repo layout only."""
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(os.path.dirname(here), ".github", "skills", "process-study-report")
