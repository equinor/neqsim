"""Tests for ``neqsim process-report`` (source detection, cases, draft basis, --init, --strict, end-to-end runs).

JVM-heavy tests use the synthetic fixtures of ``psr_demo`` and ``psr_examples`` only and skip when ``jneqsim`` is missing.
"""
import copy
import json
import os
import shutil
import sys

import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import process_report  # noqa: E402
import psr_balances  # noqa: E402
import psr_basis  # noqa: E402
import psr_draft  # noqa: E402
import psr_validate  # noqa: E402

EXAMPLES = os.path.join(HERE, "psr_examples")
GAS_MODEL = os.path.join(EXAMPLES, "gas_plant_model.py")
GAS_CASES = os.path.join(EXAMPLES, "cases.yaml")
OIL_JSON = os.path.join(EXAMPLES, "oil_stabilisation.json")
OIL_CASES = os.path.join(EXAMPLES, "oil_stabilisation_cases.yaml")

TINY_MODEL = '''"""Tiny model file used by the tests."""
from psr_demo import build_demo_model


def build(case=None):
    params = (case or {}).get("parameters") or {}
    return build_demo_model(feed_kg_h=float(params.get("feed_kg_h", 20000.0)))
'''


def _need_jvm():
    return pytest.importorskip("jneqsim")


def _tiny_model(tmp_path):
    path = tmp_path / "tiny_model.py"
    path.write_text(TINY_MODEL, encoding="utf-8")
    return str(path)


# ── source detection (no JVM) ────────────────────────────────

def test_detect_source_by_extension_and_content(tmp_path):
    py = tmp_path / "m.py"
    py.write_text("x = 1\n")
    assert process_report.detect_source(str(py)) == ("python", str(py))
    ser = tmp_path / "plant.neqsim"
    ser.write_bytes(b"x")
    assert process_report.detect_source(str(ser))[0] == "neqsim"
    definition = tmp_path / "p.json"
    definition.write_text(json.dumps({"fluid": {}, "process": []}))
    assert process_report.detect_source(str(definition))[0] == "json"
    multi = tmp_path / "areas.json"
    multi.write_text(json.dumps({"areas": {}}))
    assert process_report.detect_source(str(multi))[0] == "json"
    data = tmp_path / "d.json"
    data.write_text(json.dumps({"schema": "neqsim.process_study_report.data/1"}))
    assert process_report.detect_source(str(data))[0] == "data"
    assert process_report.detect_source(str(py), forced="python")[0] == "python"


def test_detect_source_errors_and_folders(tmp_path):
    other = tmp_path / "other.json"
    other.write_text(json.dumps({"hello": 1}))
    with pytest.raises(process_report.ReportError, match="neither a NeqSim process definition"):
        process_report.detect_source(str(other))
    with pytest.raises(process_report.ReportError, match="not found"):
        process_report.detect_source(str(tmp_path / "missing.py"))
    txt = tmp_path / "model.txt"
    txt.write_text("x")
    with pytest.raises(process_report.ReportError, match="cannot tell"):
        process_report.detect_source(str(txt))
    with pytest.raises(process_report.ReportError, match="--source-type"):
        process_report.detect_source(str(txt), forced="bogus")
    folder = tmp_path / "task" / "step2_analysis"
    folder.mkdir(parents=True)
    with pytest.raises(process_report.ReportError, match="no model found"):
        process_report.detect_source(str(tmp_path / "task"))
    (folder / "model.py").write_text("x = 1\n")
    assert process_report.detect_source(str(tmp_path / "task"))[1].endswith("model.py")
    (tmp_path / "task" / "process.json").write_text("{}")
    with pytest.raises(process_report.ReportError, match="several candidate"):
        process_report.detect_source(str(tmp_path / "task"))


def test_find_task_dir(tmp_path):
    task = tmp_path / "task"
    (task / "step1_scope_and_research").mkdir(parents=True)
    (task / "step2_analysis").mkdir()
    model = task / "step2_analysis" / "model.py"
    model.write_text("x = 1\n")
    assert process_report.find_task_dir(str(model)) == str(task)
    outside = tmp_path / "loose.py"
    outside.write_text("x = 1\n")
    assert process_report.find_task_dir(str(outside)) is None


# ── cases (no JVM) ───────────────────────────────────────────

def test_parse_cases_forms_and_validation():
    cases, options = process_report.parse_cases({"options": {"x": 1}, "cases": [
        {"id": "A", "label": "Base"},
        {"id": "B", "set": {"feed.flowRate": [20000, "kg/hr"], "v.outletPressure": "35 bara", "t.temp": {"value": 30, "unit": "C"},
                            "p.n": 3}, "parameters": {"k": 1}, "role": "scenario"}]})
    assert options == {"x": 1}
    assert cases[1]["set"] == {"feed.flowRate": (20000.0, "kg/hr"), "v.outletPressure": (35.0, "bara"), "t.temp": (30.0, "C"), "p.n": (3.0, "")}
    assert cases[1]["parameters"] == {"k": 1} and cases[1]["role"] == "scenario"
    assert cases[0]["label"] == "Base" and cases[0]["set"] == {}
    for bad, match in (([], "non-empty"), ([{"id": "A"}, {"id": "A"}], "duplicate"), ([{"id": "bad id"}], "letters"),
                       ([{"id": "A", "set": {"x": "abc"}}], "not a number"), ([{"id": "A", "set": {"x": [1, 2, 3]}}], "value, unit"),
                       (["x"], "mapping")):
        with pytest.raises(process_report.ReportError, match=match):
            process_report.parse_cases(bad)


def test_load_cases_yaml_default_and_missing(tmp_path):
    assert process_report.load_cases(None)[0][0]["id"] == "A"
    path = tmp_path / "cases.yaml"
    path.write_text("cases:\n  - id: A\n    set:\n      'feed.flowRate': [1000, kg/hr]\n", encoding="utf-8")
    cases, _ = process_report.load_cases(str(path))
    assert cases[0]["set"]["feed.flowRate"] == (1000.0, "kg/hr")
    with pytest.raises(process_report.ReportError, match="not found"):
        process_report.load_cases(str(tmp_path / "none.yaml"))
    bad = tmp_path / "bad.yaml"
    bad.write_text("cases: [unclosed", encoding="utf-8")
    with pytest.raises(process_report.ReportError, match="cannot read cases"):
        process_report.load_cases(str(bad))


def test_find_side_cases_only_for_unambiguous_layouts(tmp_path):
    (tmp_path / "cases.yaml").write_text("cases: []\n")
    (tmp_path / "plant.neqsim").write_bytes(b"x")
    assert process_report.find_side_cases(str(tmp_path / "plant.neqsim")) is None
    (tmp_path / "plant.cases.yaml").write_text("cases: []\n")
    assert process_report.find_side_cases(str(tmp_path / "plant.neqsim")).endswith("plant.cases.yaml")
    (tmp_path / "model.py").write_text("x = 1\n")
    assert process_report.find_side_cases(str(tmp_path / "model.py")).endswith("cases.yaml")
    (tmp_path / "other.py").write_text("x = 1\n")
    assert process_report.find_side_cases(str(tmp_path / "other.py")) is None


def test_shipped_example_cases_parse():
    cases, _ = process_report.load_cases(GAS_CASES)
    assert [c["id"] for c in cases] == ["A", "B"] and cases[1]["parameters"]["feed_temperature_c"] == 55.0
    assert process_report.load_cases(OIL_CASES)[0][1]["set"]["oil heater.outletTemperature"] == (70.0, "C")


# ── command dispatch (no JVM) ────────────────────────────────

def test_cli_dispatch_help_and_errors(monkeypatch, capsys):
    import neqsim_cli

    monkeypatch.setattr(sys, "argv", ["neqsim", "process-report", "--help"])
    with pytest.raises(SystemExit) as exc:
        neqsim_cli.main()
    assert exc.value.code == 0
    out = capsys.readouterr().out
    assert "--cases" in out and "--strict" in out and "--init" in out
    monkeypatch.setattr(sys, "argv", ["neqsim", "process-report", os.path.join(HERE, "no_such_model.py")])
    with pytest.raises(SystemExit) as exc:
        neqsim_cli.main()
    assert exc.value.code == 2
    assert "ERROR" in capsys.readouterr().err
    monkeypatch.setattr(sys, "argv", ["neqsim", "--help"])
    with pytest.raises(SystemExit):
        neqsim_cli.main()
    assert "process-report" in capsys.readouterr().out


def test_module_entry_point_matches_cli(capsys):
    assert process_report.main([os.path.join(HERE, "no_such_model.py")]) == 2
    assert "ERROR" in capsys.readouterr().err


def test_packaging_registers_modules_and_templates():
    text = open(os.path.join(HERE, "pyproject.toml"), encoding="utf-8").read()
    for token in ('"process_report"', '"psr_draft"', '"psr_templates"', 'psr_templates = ["*.yaml"]'):
        assert token in text
    skill = os.path.join(os.path.dirname(HERE), ".github", "skills", "process-study-report", "templates")
    for name in os.listdir(skill):
        mirrored = os.path.join(HERE, "psr_templates", name)
        assert os.path.exists(mirrored), name
        assert open(mirrored, "rb").read().replace(b"\r\n", b"\n") == open(os.path.join(skill, name), "rb").read().replace(b"\r\n", b"\n"), name
    assert psr_basis.templates_dir().endswith("psr_templates")


# ── task context and draft basis ─────────────────────────────

def _task_folder(tmp_path, request="Compare operating cases of the compression train."):
    task = tmp_path / "task"
    (task / "step1_scope_and_research").mkdir(parents=True)
    (task / "study_config.yaml").write_text('study:\n  title: "Compression study"\n  author: "A. Engineer"\n', encoding="utf-8")
    (task / "user_input.md").write_text("# Log\n\n```text\n<!-- ORIGINAL_USER_PROMPT -->\n%s\n```\n" % request, encoding="utf-8")
    (task / "results.json").write_text(json.dumps({"study": {"title": "Compression study"}}), encoding="utf-8")
    return str(task)


def test_read_task_context(tmp_path):
    ctx = psr_draft.read_task_context(_task_folder(tmp_path))
    assert ctx["title"] == "Compression study" and ctx["author"] == "A. Engineer"
    assert ctx["request"].startswith("Compare operating cases")
    assert psr_draft.read_task_context(None) == {"files": []}
    assert psr_draft.read_task_context(str(tmp_path / "nothing")) == {"files": []}
    placeholder = tmp_path / "t2"
    placeholder.mkdir()
    (placeholder / "user_input.md").write_text("```text\n<!-- ORIGINAL_USER_PROMPT -->\n[Paste the user's original prompt here]\n```", encoding="utf-8")
    assert "request" not in psr_draft.read_task_context(str(placeholder))


@pytest.fixture(scope="module")
def demo_data():
    _need_jvm()
    import psr_demo
    import psr_extract

    first = psr_extract.extract_process(psr_demo.build_demo_model(), "A", "Base case", model_name="tiny_model.py")
    second = psr_extract.extract_process(psr_demo.build_demo_model(feed_kg_h=26000.0), "B", "High feed", model_name="tiny_model.py")
    return psr_extract.merge_cases([first, second])


SOURCE = {"type": "python", "name": "tiny_model.py", "sha256": "abc123abc123"}
CASES = [{"id": "A", "label": "Base case", "set": {}}, {"id": "B", "label": "High feed", "set": {"separation::feed.flowRate": (26000.0, "kg/hr")}}]


def _unavailable_paths(node, path=""):
    if isinstance(node, str):
        return [path] if psr_draft.CONFIRM_RE.search(node) else []
    found = []
    if isinstance(node, dict):
        for k, v in node.items():
            found += _unavailable_paths(v, "%s.%s" % (path, k) if path else k)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            found += _unavailable_paths(v, "%s[%d]" % (path, i))
    return found


def test_draft_basis_marks_everything_it_cannot_derive(demo_data):
    basis = psr_draft.draft_basis(copy.deepcopy(demo_data), CASES, SOURCE, {}, {}, today="2026-10-21")
    assert not [f for f in psr_basis.check_basis(basis) if f["level"] == "error"]
    unavailable = psr_draft.UNAVAILABLE
    assert basis["document"]["company"] == unavailable and basis["document"]["project"] == unavailable
    for field in ("objective", "audience", "maturity"):
        assert basis[field] == unavailable, field
    assert basis["boundaries"]["excluded"] == [unavailable] and basis["exclusions"] == [unavailable]
    assert basis["model"]["eos_rationale"] == unavailable and basis["validation"]["statement"] == unavailable
    assert basis["basis_status"] == "draft" and basis["drafted_fields"]
    assert basis["document"]["readiness"] == "SCREENING" and basis["document"]["readiness_note"] == "basis not confirmed"
    assert basis["document"]["date"] == "2026-10-21" and basis["document"]["prepared_by"] == "not assigned"
    assert not basis["data_classification"][0]["class"] == "measured"
    assert all(g["effect_on_conclusions"] for g in basis["data_gaps"])
    # every drafted field is backed by a data gap and nothing was written as a value that is not derivable
    assert len(basis["data_gaps"]) >= len([f for f in basis["drafted_fields"] if "quoted" not in f])
    assert psr_draft.count_unconfirmed(basis) >= 8
    assert not basis["known_balance_exceptions"] and not basis["requirement_matrices"]
    assert not basis["sensitivities"] and not basis["figures"]
    # case texts come from the case definitions, not from invention
    assert "separation::feed.flowRate = 26000.0 kg/hr" in basis["operating_cases"][1]["description"]
    assert basis["model"]["version"].endswith("sha256 abc123abc123")


def test_draft_basis_uses_task_and_options_only_when_given(demo_data, tmp_path):
    task = psr_draft.read_task_context(_task_folder(tmp_path, request="Check compressor power for [two] cases"))
    options = {"title": "My title", "doc_no": "DOC-7", "revision": "B", "author": "Name", "company": "Acme", "project": "Demo"}
    basis = psr_draft.draft_basis(copy.deepcopy(demo_data), CASES, SOURCE, options, task)
    assert basis["document"]["title"] == "My title" and basis["document"]["document_number"] == "DOC-7"
    assert basis["document"]["company"] == "Acme" and basis["document"]["project"] == "Demo" and basis["document"]["revision"] == "B"
    assert basis["objective"].startswith("As stated in the task request")
    assert "(two)" in basis["objective"] and "[" not in basis["objective"]
    assert not [f for f in psr_basis.check_basis(basis) if f["level"] == "error"]
    assert "document.company" not in basis["drafted_fields"] and "objective" not in basis["drafted_fields"]
    assert any(s["title"] == "user_input.md" for s in basis["inspected_sources"])


def test_draft_title_and_number_use_the_folder_for_generic_model_names(demo_data, tmp_path):
    folder = tmp_path / "oil_train"
    folder.mkdir()
    source = {"type": "json", "name": "process.json", "path": str(folder / "process.json"), "sha256": "x"}
    basis = psr_draft.draft_basis(copy.deepcopy(demo_data), CASES, source, {})
    assert basis["document"]["title"] == "Process study of oil train" and basis["document"]["document_number"] == "DRAFT-OIL-TRAIN"
    assert basis["document"]["project"] == psr_draft.UNAVAILABLE


def test_validator_basis_status(demo_data):
    basis = psr_draft.draft_basis(copy.deepcopy(demo_data), CASES, SOURCE, {})
    standards = psr_basis.load_structured(os.path.join(psr_basis.templates_dir(), "standards_register_template.yaml"))["standards"]
    bal = psr_balances.compute_balances(demo_data)

    def status(b):
        res = psr_validate.validate_all(copy.deepcopy(demo_data), b, standards, bal, None, None, None, None)
        return {c["id"]: c["status"] for c in res["checks"]}

    assert status(basis)["BASIS-STATUS"] == "WARN"
    raised = copy.deepcopy(basis)
    raised["document"]["readiness"] = "ISSUED FOR REVIEW"
    assert status(raised)["BASIS-STATUS"] == "FAIL"
    claimed = copy.deepcopy(basis)
    del claimed["basis_status"]
    assert status(claimed)["BASIS-STATUS"] == "FAIL"
    confirmed = json.loads(json.dumps(basis).replace(psr_draft.UNAVAILABLE, "stated by the study owner"))
    del confirmed["basis_status"]
    assert status(confirmed)["BASIS-STATUS"] == "PASS"
    default_register = copy.deepcopy(basis)
    default_register["standards_register_source"] = "toolkit default register template (verify before issue)"
    assert status(default_register)["STD-VERIFY"] == "WARN"


# ── balances: equipment bookkeeping vs genuine errors (no JVM) ──

def _balance_data(h_out_gas, h_in, mass_out, category="separator", java_class="GasScrubber", cp=2.0):
    def stream(sid, mass, h, kind):
        return {"id": sid, "label": sid, "kind": kind, "producers": [], "consumers": [], "area": "a", "source": None, "destination": None,
                "results": {"A": {"available": True, "mass_kg_h": mass, "H_kw": h, "cp_kj_kg_k": cp, "flow_kg_h": {"x": mass}, "z": {"x": 1.0}}}}

    s_in, s_out = stream("S001", 36000.0, h_in, "feed"), stream("S002", mass_out, h_out_gas, "product")
    s_in["consumers"], s_out["producers"] = ["U"], ["U"]
    unit = {"tag": "U", "area": "a", "java_class": java_class, "category": category, "inlet_streams": ["S001"], "outlet_streams": ["S002"],
            "results": {"A": {"duty_kw": None, "power_kw": None, "dp_bar": 0.0, "extras": {}}}}
    return {"cases": [{"id": "A"}], "areas": [{"name": "a", "units": ["U"]}], "components": [{"name": "x"}], "equipment": [unit],
            "streams": [s_in, s_out]}


def test_energy_bookkeeping_of_scrubber_is_a_note_not_a_pass():
    # 20 kW residual on a 10 kg/s stream of cp 2 kJ/kg/K is 1 K: separator re-flash artefact -> NOTE
    bal = psr_balances.compute_balances(_balance_data(h_out_gas=100.0, h_in=120.0, mass_out=36000.0))
    unit = bal["cases"]["A"]["units"][0]
    assert unit["energy_status"] == "NOTE" and "re-flashed" in unit["energy_note"]
    assert bal["cases"]["A"]["areas"][0]["energy_status"] == "NOTE" and bal["cases"]["A"]["overall"]["energy_status"] == "NOTE"
    assert bal["summary"]["NOTE"] >= 3 and not bal["fail_items"]
    assert bal["note_items"] and "K of inlet heat-capacity flow" in bal["note_items"][0]["note"]


def test_large_energy_or_any_mass_residual_stays_a_fail():
    large = psr_balances.compute_balances(_balance_data(h_out_gas=100.0, h_in=1000.0, mass_out=36000.0))
    assert large["cases"]["A"]["units"][0]["energy_status"] == "FAIL" and large["fail_items"]
    mass = psr_balances.compute_balances(_balance_data(h_out_gas=120.0, h_in=120.0, mass_out=35000.0))
    assert mass["cases"]["A"]["units"][0]["mass_status"] == "FAIL"
    valve = psr_balances.compute_balances(_balance_data(h_out_gas=100.0, h_in=120.0, mass_out=36000.0, category="valve", java_class="ThrottlingValve"))
    assert valve["cases"]["A"]["units"][0]["energy_status"] == "FAIL"


def test_recycle_tear_residual_note_limit():
    small = psr_balances.compute_balances(_balance_data(120.0, 120.0, 36000.0 - 20.0, category="recycle", java_class="Recycle"))
    assert small["cases"]["A"]["units"][0]["mass_status"] == "NOTE"
    big = psr_balances.compute_balances(_balance_data(120.0, 120.0, 36000.0 - 500.0, category="recycle", java_class="Recycle"))
    assert big["cases"]["A"]["units"][0]["mass_status"] == "FAIL"


# ── generalisation: components, EOS, generic symbols, no HX/compressor ──

def test_component_registry_keeps_conflicting_pseudo_components_apart():
    _need_jvm()
    from jneqsim import neqsim

    import psr_extract

    def fluid(mw, rho):
        f = neqsim.thermo.system.SystemSrkEos(300.0, 10.0)
        f.addComponent("methane", 0.9)
        f.addTBPfraction("C7", 0.1, mw, rho)
        f.setMixingRule("classic")
        return f

    registry = psr_extract.ComponentRegistry()
    a = registry.scan(fluid(0.100, 0.760))
    b = registry.scan(fluid(0.100, 0.760))
    c = registry.scan(fluid(0.120, 0.800))
    assert a == b and a != c
    assert any("variant 2" in key for key in c) and registry.conflicts
    assert len(registry.keys()) == 3


def test_mixed_eos_and_generic_symbol_are_reported_and_drawn(demo_data, tmp_path):
    import psr_diagrams

    data = copy.deepcopy(demo_data)
    data["meta"]["eos_list"] = ["SystemSrkEos", "SystemPrEos"]
    data["meta"]["component_conflicts"] = ["component C7 is defined with different properties"]
    data["equipment"][2]["category"] = "other"
    data["equipment"][2]["java_class"] = "SomeNewUnitOperation"
    standards = psr_basis.load_structured(os.path.join(psr_basis.templates_dir(), "standards_register_template.yaml"))["standards"]
    basis = psr_draft.draft_basis(copy.deepcopy(data), CASES, SOURCE, {})
    res = psr_validate.validate_all(data, basis, standards, psr_balances.compute_balances(data), None, None, None, None)
    status = {c["id"]: c["status"] for c in res["checks"]}
    assert status["EQ-GENERIC-SYMBOL"] == "WARN" and status["MODEL-EOS-COMPONENTS"] == "WARN"
    manifest = psr_diagrams.build_pfd(data, str(tmp_path / "pfd"))
    svg = open(manifest["sheets"][0]["svg"], encoding="utf-8").read()
    assert "SomeNewUnitOperation (generic symbol)" in svg and "without a dedicated symbol" in svg


# ── layout: crossing reduction and sheet splitting (no JVM) ──

def test_refined_ordering_never_routes_worse_and_reduces_crossings(tmp_path):
    import psr_diagrams
    from psr_layout_fixtures import synthetic_data

    total = {"barycentre": 0, "refined": 0}
    for seed in (6, 11, 12, 1, 4):
        data = synthetic_data(n_layers=5, width=4, seed=seed)
        for mode in total:
            manifest = psr_diagrams.build_pfd(data, str(tmp_path / ("%s_%d" % (mode, seed))), ordering=mode, max_units_per_sheet=30)
            total[mode] += manifest["crossings_total"]
        assert manifest["sheets"][0]["order_crossings"]["kept"] is not None
    assert total["refined"] < total["barycentre"]


def test_large_single_area_is_split_at_cheap_cuts(tmp_path):
    import psr_diagrams
    from psr_layout_fixtures import synthetic_data

    data = synthetic_data(n_layers=8, width=4, seed=3, max_in=1)
    manifest = psr_diagrams.build_pfd(data, str(tmp_path / "pfd"), max_units_per_sheet=10)
    assert len(manifest["sheets"]) >= 3
    assert all(len(s["units"]) <= 10 for s in manifest["sheets"])
    assert not manifest["unplaced_units"] and not manifest["unplaced_streams"]
    assert "crossings_total" in manifest and "sparse_sheets" in manifest


# ── solving from the four sources (JVM) ──────────────────────

def test_python_model_with_overrides_and_parameters(tmp_path):
    _need_jvm()
    model = _tiny_model(tmp_path)
    cases, _ = process_report.parse_cases([{"id": "A", "label": "Base"},
                                           {"id": "B", "label": "High", "set": {"separation::feed.flowRate": [30000, "kg/hr"]}},
                                           {"id": "C", "label": "Param", "parameters": {"feed_kg_h": 25000.0}}])
    data = process_report.extract_cases("python", model, cases, 15.0, 1.01325, "tiny_model.py")
    feed = {s["name"]: s for s in data["streams"]}["feed"]
    assert [round(feed["results"][c]["mass_kg_h"]) for c in "ABC"] == [20000, 30000, 25000]
    assert [c["id"] for c in data["cases"]] == ["A", "B", "C"]


def test_override_errors_are_explained(tmp_path):
    _need_jvm()
    model = _tiny_model(tmp_path)
    cases, _ = process_report.parse_cases([{"id": "A", "set": {"separation::no such unit.flowRate": [1, "kg/hr"]}}])
    with pytest.raises(process_report.ReportError, match="case A: cannot set"):
        process_report.extract_cases("python", model, cases, 15.0, 1.01325, "m")
    bad = tmp_path / "bad.py"
    bad.write_text("def build(case=None):\n    return 42\n")
    with pytest.raises(process_report.ReportError, match="not a ProcessSystem"):
        process_report.extract_cases("python", str(bad), process_report.default_cases(), 15.0, 1.01325, "m")
    none = tmp_path / "none.py"
    none.write_text("x = 1\n")
    with pytest.raises(process_report.ReportError, match="neither build"):
        process_report.extract_cases("python", str(none), process_report.default_cases(), 15.0, 1.01325, "m")
    boom = tmp_path / "boom.py"
    boom.write_text("def build():\n    raise ValueError('nope')\n")
    with pytest.raises(process_report.ReportError, match="build\\(\\) of .* failed"):
        process_report.extract_cases("python", str(boom), process_report.default_cases(), 15.0, 1.01325, "m")


def test_module_level_model_and_serialized_process(tmp_path):
    _need_jvm()
    from psr_demo import build_demo_model

    module = tmp_path / "module_model.py"
    module.write_text("from psr_demo import build_demo_model\nmodel = build_demo_model()\n")
    data = process_report.extract_cases("python", str(module), process_report.default_cases(), 15.0, 1.01325, "m")
    assert len(data["streams"]) == 11
    saved = tmp_path / "demo.neqsim"
    assert build_demo_model().saveToNeqsim(str(saved))
    assert process_report.detect_source(str(saved))[0] == "neqsim"
    loaded = process_report.extract_cases("neqsim", str(saved), process_report.default_cases(), 15.0, 1.01325, "demo.neqsim")
    assert len(loaded["streams"]) == len(data["streams"]) and len(loaded["equipment"]) == len(data["equipment"])


def test_json_definition_errors_are_reported(tmp_path):
    _need_jvm()
    bad = tmp_path / "bad.json"
    bad.write_text(json.dumps({"fluid": {"model": "SRK", "temperature": 300.0, "pressure": 10.0, "mixingRule": "classic",
                                         "components": {"methane": 1.0}},
                               "process": [{"type": "Stream", "name": "f", "properties": {"flowRate": [1.0, "kg/hr"]}},
                                           {"type": "NoSuchEquipment", "name": "x", "inlet": "f"}]}))
    with pytest.raises(process_report.ReportError, match="did not build"):
        process_report.extract_cases("json", str(bad), process_report.default_cases(), 15.0, 1.01325, "bad.json")


# ── --init, exit codes, end-to-end ───────────────────────────

def test_init_writes_inputs_only_then_rerun_with_edited_basis(tmp_path, capsys):
    _need_jvm()
    model = _tiny_model(tmp_path)
    out = tmp_path / "out"
    assert process_report.main([model, "--init", "--out", str(out), "--no-word", "--quiet"]) == 0
    inputs = out / "inputs"
    assert (inputs / "basis.yaml").exists() and (inputs / "cases.yaml").exists() and (inputs / "standards_register.yaml").exists()
    assert not (out / "report").exists() and not (out / "diagrams").exists()
    basis = psr_basis.load_structured(str(inputs / "basis.yaml"))
    assert basis["basis_status"] == "draft" and psr_draft.count_unconfirmed(basis) > 5
    assert (out / "data" / "process_data.json").exists()
    cases, _ = process_report.load_cases(str(inputs / "cases.yaml"))
    assert cases[0]["id"] == "A"
    # confirm: fill every marker, drop the draft flag, change the readiness and run with the edited basis
    text = (inputs / "basis.yaml").read_text(encoding="utf-8").replace(psr_draft.UNAVAILABLE, "stated by the study owner")
    confirmed = psr_basis.load_structured(str(inputs / "basis.yaml"))
    confirmed = json.loads(json.dumps(confirmed).replace(psr_draft.UNAVAILABLE, "stated by the study owner"))
    del confirmed["basis_status"], confirmed["drafted_fields"]
    confirmed["document"]["readiness_note"] = "confirmed by the study owner, not for design or construction"
    (tmp_path / "basis_confirmed.json").write_text(json.dumps(confirmed), encoding="utf-8")
    assert text
    assert process_report.main([model, "--basis", str(tmp_path / "basis_confirmed.json"), "--cases", str(inputs / "cases.yaml"),
                                "--out", str(out), "--no-word", "--quiet", "--strict"]) == 0
    result = json.loads((out / "report" / "validation_report.json").read_text(encoding="utf-8"))
    states = {c["id"]: c["status"] for c in result["checks"]}
    assert states["BASIS-STATUS"] == "PASS" and result["status"] != "FAIL"


def test_strict_exit_codes(monkeypatch, tmp_path):
    _need_jvm()
    model = _tiny_model(tmp_path)

    def fake_pipeline(data, basis, standards, out_dir, **kwargs):
        os.makedirs(out_dir, exist_ok=True)
        return {"docx": os.path.join(out_dir, "r.docx"), "pdf": None, "validation": "FAIL", "validation_json": "v.json",
                "validation_md": "v.md", "exports": {}, "diagrams": {"bfd": "b.svg", "pfd_pdf": "p.pdf", "pfd_sheets": [], "pfd_spec": "s.json"},
                "balances_summary": {}, "word_updated": False, "timings_s": {}}

    monkeypatch.setattr(process_report.psr, "run_pipeline", fake_pipeline)
    args = [model, "--out", str(tmp_path / "o"), "--quiet", "--no-word"]
    assert process_report.main(args) == 0
    assert process_report.main(args + ["--strict"]) == 1


def test_basis_case_mismatch_is_rejected(tmp_path, demo_data):
    _need_jvm()
    model = _tiny_model(tmp_path)
    basis = psr_draft.draft_basis(copy.deepcopy(demo_data), CASES, SOURCE, {})
    path = tmp_path / "basis.json"
    path.write_text(json.dumps(basis), encoding="utf-8")
    assert process_report.main([model, "--basis", str(path), "--out", str(tmp_path / "o"), "--quiet"]) == 2


@pytest.fixture(scope="module")
def gas_run(tmp_path_factory):
    """Proof run 1: Python model file with cases.yaml overrides (two operating cases, multi-area, recycle, intercooling)."""
    _need_jvm()
    out = tmp_path_factory.mktemp("gas") / "report"
    code = process_report.main([GAS_MODEL, "--cases", GAS_CASES, "--out", str(out), "--no-word", "--quiet", "--title", "Synthetic gas plant"])
    return code, out


@pytest.fixture(scope="module")
def oil_task_run(tmp_path_factory):
    """Proof run 2: JSON definition inside a task folder (default output location, results.json merge)."""
    _need_jvm()
    task = tmp_path_factory.mktemp("oiltask") / "task"
    (task / "step1_scope_and_research").mkdir(parents=True)
    (task / "step2_analysis").mkdir()
    (task / "study_config.yaml").write_text('study:\n  title: "Oil stabilisation screening"\n  author: "Test Author"\n', encoding="utf-8")
    (task / "results.json").write_text(json.dumps({"study": {"title": "Oil stabilisation screening"}, "key_results": {"x": 1}}), encoding="utf-8")
    shutil.copyfile(OIL_JSON, task / "step2_analysis" / "process.json")
    shutil.copyfile(OIL_CASES, task / "step2_analysis" / "cases.yaml")
    code = process_report.main([str(task / "step2_analysis" / "process.json"), "--no-word", "--quiet"])
    return code, task


def test_gas_plant_end_to_end(gas_run):
    code, out = gas_run
    assert code == 0
    result = json.loads((out / "report" / "validation_report.json").read_text(encoding="utf-8"))
    assert result["status"] in ("PASS", "PASS WITH WARNINGS"), result["status"]
    states = {c["id"]: c["status"] for c in result["checks"]}
    assert states["BASIS-STATUS"] == "WARN" and states["STD-VERIFY"] == "WARN"
    summary = json.loads((out / "run_summary.json").read_text(encoding="utf-8"))
    assert summary["readiness"] == "SCREENING - basis not confirmed" and summary["basis_drafted"] is True
    assert summary["n_cases"] == 2 and summary["n_units"] == 18 and summary["n_streams"] == 26
    assert summary["diagrams"]["crossings_total"] == 0
    data = json.loads((out / "data" / "process_data.json").read_text(encoding="utf-8"))
    feed = {s["name"]: s for s in data["streams"]}["feed"]
    assert round(feed["results"]["A"]["mass_kg_h"]) == 60000 and round(feed["results"]["B"]["mass_kg_h"]) == 75000
    power = {u["tag"]: u for u in data["equipment"]}["export compressor"]["results"]
    assert power["B"]["power_kw"] != power["A"]["power_kw"]
    for rel in ("report/validation_report.md", "diagrams/bfd/bfd.svg", "diagrams/pfd/pfd_drawing_set.pdf", "data/stream_components.csv",
                "data/process_data.xlsx", "inputs/basis.yaml", "inputs/cases.yaml", "data/balances.json"):
        assert (out / rel).exists(), rel
    assert any(p.suffix == ".docx" for p in (out / "report").iterdir())
    components_csv = (out / "data" / "stream_components.csv").read_text(encoding="utf-8").splitlines()
    assert len(components_csv) - 1 == 26 * 11 * 2
    basis = psr_basis.load_structured(str(out / "inputs" / "basis.yaml"))
    assert basis["document"]["title"] == "Synthetic gas plant" and basis["basis_status"] == "draft"


def test_gas_plant_diagram_sheets_stay_readable(gas_run):
    _, out = gas_run
    spec = json.loads((out / "diagrams" / "pfd" / "pfd_layout_spec.json").read_text(encoding="utf-8"))
    assert len(spec["sheets"]) == 3
    tags = {u["tag"] for sheet in spec["sheets"] for u in sheet["units"]}
    assert {"LP compressor", "intercooler", "scrubber liquid recycle", "export cooler"} <= tags


def test_json_oil_stabilisation_in_task_folder(oil_task_run):
    code, task = oil_task_run
    assert code == 0
    out = task / "deliverables" / "process_report"
    assert (out / "report" / "validation_report.json").exists()
    results = json.loads((task / "results.json").read_text(encoding="utf-8"))
    block = results["process_study_report"]
    assert results["key_results"] == {"x": 1} and block["validation_status"] in ("PASS", "PASS WITH WARNINGS")
    assert block["report_docx"].startswith("deliverables/process_report/report/")
    basis = psr_basis.load_structured(str(out / "inputs" / "basis.yaml"))
    assert basis["document"]["title"] == "Oil stabilisation screening" and basis["document"]["prepared_by"] == "Test Author"
    data = json.loads((out / "data" / "process_data.json").read_text(encoding="utf-8"))
    heater = {u["tag"]: u for u in data["equipment"]}["oil heater"]["results"]
    assert heater["A"]["duty_kw"] > heater["B"]["duty_kw"] > 0
    assert not [u for u in data["equipment"] if u["category"] == "compressor"]
    assert data["meta"]["eos_list"] == ["SystemSrkEos"] and data["meta"]["mixing_rules"]
