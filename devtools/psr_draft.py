"""Draft engineering basis for ``neqsim process-report`` when the user has not supplied a basis.

Everything that can be derived from the solved model, the case definitions and the task folder is filled in and
labelled with its origin. Everything else is the explicit text :data:`UNAVAILABLE`, listed in ``drafted_fields`` and
turned into a ``data_gaps`` entry that states the effect on conclusions. Nothing is invented: a project name, a
company, an objective or an acceptance criterion that nobody supplied stays unavailable. The draft is labelled
``SCREENING - basis not confirmed`` and the validator keeps warning until a person confirms it.
"""
import copy
import datetime
import hashlib
import json
import os
import re

import psr_basis

UNAVAILABLE = "unavailable - TO BE CONFIRMED"
CONFIRM_RE = re.compile(r"TO BE CONFIRMED", re.IGNORECASE)
GENERIC_MODEL_NAMES = ("model", "process", "build model", "process model", "process definition", "plant")
READINESS_LABEL = "SCREENING"
READINESS_NOTE = "basis not confirmed"


def sha256_prefix(path, n=12):
    """First ``n`` hex digits of the SHA-256 of a file ('unavailable' when unreadable)."""
    try:
        digest = hashlib.sha256()
        with open(path, "rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b""):
                digest.update(chunk)
        return digest.hexdigest()[:n]
    except OSError:
        return "unavailable"


def _plain(text, limit=600):
    """Single-line text without characters the placeholder scanner would read as a template marker."""
    out = re.sub(r"\s+", " ", str(text or "")).strip()
    out = out.replace("[", "(").replace("]", ")").replace("{{", "(").replace("}}", ")").replace("<!--", "").replace("-->", "")
    return out[:limit]


def read_task_context(task_dir):
    """Facts from a NeqSim task folder: title, author, classification, original request, task_spec text.

    Args:
        task_dir: task folder path or None.

    Returns:
        dict with the keys found (``title``, ``author``, ``classification``, ``request``, ``spec_objective``, ``files``).
    """
    ctx = {"files": []}
    if not task_dir or not os.path.isdir(task_dir):
        return ctx
    cfg = os.path.join(task_dir, "study_config.yaml")
    if os.path.exists(cfg):
        try:
            study = (psr_basis.load_structured(cfg) or {}).get("study") or {}
            title = str(study.get("title") or "").strip()
            if title and not title.startswith("["):
                ctx["title"] = title
            for key in ("author", "classification"):
                if str(study.get(key) or "").strip():
                    ctx[key] = str(study[key]).strip()
            ctx["files"].append(("study_config.yaml", "task configuration"))
        except Exception:  # noqa: BLE001 - a broken optional file must not stop the report
            pass
    ui = os.path.join(task_dir, "user_input.md")
    if os.path.exists(ui):
        with open(ui, encoding="utf-8", errors="replace") as fh:
            text = fh.read()
        match = re.search(r"<!--\s*ORIGINAL_USER_PROMPT\s*-->\s*(.*?)```", text, re.DOTALL)
        request = match.group(1).strip() if match else ""
        if request and not request.startswith("[Paste"):
            ctx["request"] = request
        ctx["files"].append(("user_input.md", "verbatim task request"))
    spec = os.path.join(task_dir, "step1_scope_and_research", "task_spec.md")
    if os.path.exists(spec):
        with open(spec, encoding="utf-8", errors="replace") as fh:
            text = fh.read()
        match = re.search(r"^#+\s*(?:\d+\.?\s*)?(?:Problem Statement|Objective|Scope)[^\n]*\n+(.*?)(?=^#|\Z)", text,
                          re.DOTALL | re.MULTILINE | re.IGNORECASE)
        if match:
            body = match.group(1).strip()
            if body and not body.startswith("[") and "<!--" not in body[:5]:
                ctx["spec_objective"] = body
        ctx["files"].append(("step1_scope_and_research/task_spec.md", "task specification"))
    results = os.path.join(task_dir, "results.json")
    if os.path.exists(results):
        try:
            with open(results, encoding="utf-8") as fh:
                res = json.load(fh)
            title = str((res.get("study") or {}).get("title") or res.get("title") or "").strip()
            if title and "title" not in ctx and not title.startswith("["):
                ctx["title"] = title
            ctx["files"].append(("results.json", "task results"))
        except Exception:  # noqa: BLE001
            pass
    return ctx


def _slug(text, n=24):
    return re.sub(r"[^A-Z0-9]+", "-", str(text).upper()).strip("-")[:n] or "MODEL"


def _case_text(case):
    sets = case.get("set") or {}
    if not sets:
        return "Model as built, no parameter overrides"
    parts = []
    for address, spec in sets.items():
        value, unit = spec
        parts.append("%s = %s%s" % (address, value, (" " + unit) if unit else ""))
    return "Overrides applied to the model: " + "; ".join(parts)


def draft_basis(data, cases, source, options=None, task=None, today=None):
    """Build a draft basis dict from extracted data (see module docstring).

    Args:
        data: merged data set of :mod:`psr_extract`.
        cases: case definitions (``id``, ``label``, optional ``role``, ``description``, ``data_source``, ``set``).
        source: dict with ``type``, ``path``, ``name`` of the model source.
        options: ``title``, ``doc_no``, ``revision``, ``status``, ``author``, ``company``, ``project``.
        task: result of :func:`read_task_context`.
        today: ISO date string (default today).

    Returns:
        A basis dict that passes ``psr_basis.check_basis`` and carries ``basis_status: draft``.
    """
    options, task = options or {}, task or {}
    today = today or datetime.date.today().isoformat()
    meta = data.get("meta") or {}
    gaps, drafted = [], []

    def unavailable(field, item, effect, mitigation):
        drafted.append(field)
        gaps.append({"id": "G%d" % (len(gaps) + 1), "item": item, "effect_on_conclusions": effect, "mitigation": mitigation})
        return UNAVAILABLE

    def note_gap(field, item, effect, mitigation):
        drafted.append(field)
        gaps.append({"id": "G%d" % (len(gaps) + 1), "item": item, "effect_on_conclusions": effect, "mitigation": mitigation})

    stem = os.path.splitext(str(source.get("name") or meta.get("model_name") or "model"))[0].replace("_", " ").replace("-", " ").strip()
    if stem.lower() in GENERIC_MODEL_NAMES and source.get("path"):
        stem = os.path.basename(os.path.dirname(os.path.abspath(source["path"]))).replace("_", " ").replace("-", " ").strip() or stem
    title = options.get("title") or task.get("title") or "Process study of %s" % (stem or "the model")
    doc_no = options.get("doc_no") or "DRAFT-" + _slug(stem, 28)
    author = options.get("author") or task.get("author") or "not assigned"
    company = options.get("company") or unavailable("document.company", "Company or owner of the study is not stated",
                                                    "The report cannot be attributed to an owner", "State the company in the basis")
    project = options.get("project") or unavailable(
        "document.project", "Project or facility is not stated", "The model cannot be tied to a facility", "State the project in the basis")
    request = task.get("spec_objective") or task.get("request")
    if request:
        objective = "As stated in the task request (to be confirmed by the study owner): " + _plain(request)
        drafted.append("objective (quoted from the task request, confirm)")
    else:
        objective = unavailable("objective", "Study objective is not stated", "Results cannot be judged against a purpose",
                                "State the decision or understanding the study supports")
    audience = unavailable("audience", "Intended audience is not stated", "Level of detail cannot be matched to the readers",
                           "State who reviews and uses the report")
    maturity = unavailable("maturity", "Study maturity is not stated",
                           "It is unknown whether the numbers are screening, concept or design values",
                           "State the maturity and the estimate class")
    excluded = unavailable("boundaries.excluded", "Items outside the model are not stated",
                           "Readers may assume that systems outside the model are included", "List the excluded systems")
    n_units, n_streams = len(data["equipment"]), len(data["streams"])
    area_names = [a["name"] for a in data["areas"]]
    included = ["All %d unit operations and %d material streams of the solved model, grouped in the areas: %s"
                % (n_units, n_streams, ", ".join(area_names) or "one area")]
    eos_list = meta.get("eos_list") or ([meta["eos"]] if meta.get("eos") else [])
    rules = meta.get("mixing_rules") or []
    if rules:
        mixing = "Read from the model fluid: %s" % ", ".join(rules)
    else:
        mixing = unavailable("model.mixing_rule", "Mixing rule is not reported by the model",
                             "Phase behaviour of non-ideal mixtures cannot be judged",
                             "State the mixing rule and the source of the interaction parameters")
    recycles = [u["tag"] for u in data["equipment"] if u["category"] == "recycle"]
    convergence = ("The toolkit ran the model once per operating case. Recycle units in the model: %s. Solver tolerances and iteration "
                   "residuals are %s" % (", ".join(recycles) if recycles else "none", UNAVAILABLE))
    note_gap("model.convergence (tolerances)", "Solver tolerances and convergence history are not recorded",
             "Closure of recycles is judged only from the balance residuals of this report",
             "Record the tolerance and iteration count of the model run")
    eos_rationale = unavailable("model.eos_rationale", "Reason for the equation of state is not stated",
                                "Suitability of the thermodynamic model for the fluids is not demonstrated",
                                "State why the equation of state suits the fluids")
    pseudo = [c["name"] for c in data["components"] if c.get("is_tbp") or c.get("is_plus")]
    characterisation = ("Components read from the solved model: [[fact:n_components]], of which [[fact:n_pseudo]] are characterised "
                        "pseudo-components%s. Source and tuning of the heavy-end characterisation: %s"
                        % (" (%s)" % ", ".join(pseudo[:6]) if pseudo else "", UNAVAILABLE))
    note_gap("model.fluid_characterization (source of heavy-end data)", "Source and tuning of the fluid characterisation are not stated",
             "Phase split and heavy-end behaviour carry an unknown uncertainty", "Reference the PVT report or fluid file")
    validation_statement = unavailable("validation", "No measured data were supplied",
                                       "Results are model predictions without a comparison with the plant",
                                       "Supply operating data and add comparison rows to the basis")
    criteria = [
        {"id": "AC1", "text": "Overall mass balance of every case closes within 0.1 % of the boundary feed", "source": "toolkit default (draft)",
         "requirement_class": "informative", "check": {"metric": "overall_mass_rel", "max": 0.001}},
        {"id": "AC2", "text": "Project acceptance criteria are %s" % UNAVAILABLE, "source": "unavailable", "requirement_class": "informative"}]
    note_gap("acceptance_criteria (project criteria)", "Project acceptance criteria are not stated",
             "Only model-integrity checks can be judged; no pass or fail against project requirements",
             "Add the project criteria with their source")
    assumptions = [{"id": "AS1", "text": "Assumptions built into the model source (feed, setpoints, efficiencies) have not been reviewed for "
                                         "this report: %s" % UNAVAILABLE, "effect": "Every result inherits the unreviewed model inputs"}]
    note_gap("assumptions (model inputs)", "Model input assumptions are not reviewed",
             "Results are valid only for the inputs as coded in the model", "List and justify the model inputs")
    exclusions = [unavailable("exclusions", "Study exclusions are not stated", "Scope limits are not documented",
                              "State what the study does not cover")]
    op_cases = []
    for c in cases:
        op_cases.append({"id": c["id"], "label": c.get("label") or "Case %s" % c["id"], "role": c.get("role") or "scenario",
                         "description": c.get("description") or _case_text(c),
                         "data_source": c.get("data_source") or "inputs of the model source %s%s" % (
                             source.get("name"), " with the overrides of the case definition" if c.get("set") else "")})
    version = "%s file, sha256 %s" % (source.get("type"), source.get("sha256") or "unavailable")
    sources = [{"id": "S1", "kind": "model", "title": source.get("name") or "model", "version": version, "status": "used",
                "used_for": "all results"}]
    for i, (name, role) in enumerate(task.get("files", []), 2):
        sources.append({"id": "S%d" % i, "kind": "task_brief", "title": name, "version": "as found in the task folder",
                        "status": "read", "used_for": role})
    classification = [
        {"item": "Stream results, duties, powers, balances", "class": "calculated", "note": "NeqSim solution of the model"},
        {"item": "Feed conditions, setpoints, efficiencies coded in the model", "class": "specified",
         "note": "origin of the specified values is %s" % UNAVAILABLE},
        {"item": "Plant measurements and vendor data", "class": "unavailable", "note": "none supplied"}]
    interpretation, findings = [], []
    for c in cases:
        cid = c["id"]
        interpretation.append({"topic": "Case %s energy use" % cid,
                               "text": "Compressor and pump shaft power is [[fact:power_total_%s|.0f]] kW, cooling duty [[fact:cooling_total_%s|.0f]] kW "
                                       "and heating duty [[fact:heating_total_%s|.0f]] kW." % (cid, cid, cid)})
        findings.append("Case %s: overall mass balance residual [[fact:mass_rel_pct_%s|.2e]] percent of the boundary feed." % (cid, cid))
    process_description = {}
    streams = {s["id"]: s for s in data["streams"]}
    for area in data["areas"]:
        units = [u for u in data["equipment"] if u["area"] == area["name"] and u["category"] != "logic"]
        listing = ", ".join("%s (%s)" % (u["tag"], u["java_class"]) for u in units[:25])
        extra = " and further units listed in Appendix A" if len(units) > 25 else ""
        feeds = [s["label"] for s in streams.values() if s["kind"] == "feed" and s["area"] == area["name"]]
        text = "Area %s comprises the unit operations %s%s." % (area["name"], listing or "none", extra)
        if feeds:
            text += " Boundary feeds entering in this area: %s." % ", ".join(feeds[:8])
        process_description[area["name"]] = text
    revision = options.get("revision") or "A"
    basis = {
        "document": {
            "title": title, "subtitle": "Draft generated from the solved model - basis not confirmed", "document_number": doc_no,
            "revision": revision, "issue_status": options.get("status") or "Draft - not for issue",
            "readiness": READINESS_LABEL, "readiness_note": READINESS_NOTE,
            "readiness_explanation": "The engineering basis was drafted by the toolkit from the model; items marked %s have not been "
                                     "confirmed by a person." % UNAVAILABLE,
            "date": today, "company": company, "project": project, "prepared_by": author, "checked_by": "not assigned",
            "approved_by": "not assigned",
            "revision_history": [{"rev": revision, "date": today, "description": "First draft generated by neqsim process-report",
                                  "by": author}]},
        "basis_status": "draft", "drafted_fields": drafted,
        "objective": objective, "audience": audience, "maturity": maturity,
        "boundaries": {"included": included, "excluded": [excluded],
                       "unmodelled_systems": ["Utility, flare, relief, control and safety systems unless they appear in the model (%s)"
                                              % UNAVAILABLE]},
        "operating_cases": op_cases, "acceptance_criteria": criteria, "inspected_sources": sources,
        "model": {"name": source.get("name") or meta.get("model_name") or "model", "version": version,
                  "simulator": "NeqSim %s" % str(meta.get("neqsim_version", "unknown")).replace("neqsim ", ""),
                  "eos": "Read from the model: %s" % (", ".join(eos_list) or "unknown"), "eos_rationale": eos_rationale,
                  "mixing_rule": mixing, "fluid_characterization": characterisation, "convergence": convergence},
        "conventions": copy.deepcopy(_default_conventions(meta)),
        "data_classification": classification, "assumptions": assumptions, "exclusions": exclusions, "data_gaps": gaps,
        "known_balance_exceptions": [], "requirement_matrices": [], "process_description": process_description,
        "validation": {"statement": validation_statement, "rows": []}, "sensitivities": [], "constraint_tables": [],
        "interpretation": interpretation, "principal_findings": findings,
        "conclusions": ["The solved model was extracted completely for every case; balance closure is reported in Section 8.4 and the "
                        "validation report. The basis is a draft and the results are not confirmed for any decision."],
        "recommendations": ["Confirm or replace every item marked %s in the basis, then re-run the command with the edited basis." % UNAVAILABLE,
                            "Re-verify the standards register before issue."],
        "references": ["NeqSim documentation, process modelling guides", "Model source: %s" % (source.get("name") or "model")],
        "figures": [], "bfd_blocks": None, "pfd_sheets": None, "diagram_notes": [], "tolerances": {},
    }
    if len(eos_list) > 1:
        basis["diagram_notes"].append("The model uses more than one equation-of-state class; see the model section.")
    return basis


def _default_conventions(meta):
    std = meta.get("standard_conditions") or {"t_c": 15.0, "p_bara": 1.01325}
    return {
        "units": "SI-derived: T in degC, P in bara (absolute) unless barg is stated, mass flow kg/h, molar flow kmol/h, enthalpy kJ/kg and kW",
        "pressure": "Absolute pressure (bara) in all tables; gauge (barg) = bara - 1.01325",
        "composition_basis": "Mole fraction unless 'mass fraction' is stated; all components listed",
        "standard_conditions": {"t_c": std["t_c"], "p_bara": std["p_bara"],
                                "definition": "Standard volumes are flash volumes at the stated temperature and pressure; "
                                              "ideal-gas standard volume is also given"},
        "phase_fraction_basis": "Phase fractions are mole fractions (beta); mass fractions are given in the phase table",
        "heat_sign": "Heat added to the process fluid is positive; cooler duties are negative",
        "work_sign": "Shaft work added to the fluid (compressor, pump) is positive",
        "enthalpy_reference": "NeqSim enthalpy reference; only differences are meaningful",
    }


def count_unconfirmed(node):
    """Number of strings in a nested basis that still contain the 'TO BE CONFIRMED' marker."""
    if isinstance(node, str):
        return 1 if CONFIRM_RE.search(node) else 0
    if isinstance(node, dict):
        return sum(count_unconfirmed(v) for v in node.values())
    if isinstance(node, (list, tuple)):
        return sum(count_unconfirmed(v) for v in node)
    return 0
