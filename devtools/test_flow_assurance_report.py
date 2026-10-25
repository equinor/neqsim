"""Tests for ``neqsim flow-assurance-report`` (units, geometry, topology, thermal, hand-off, tokens, gates, CLI).

Pure-Python tests run everywhere. The end-to-end tests solve a reduced synthetic demonstration (two cases) with the
NeqSim JVM and skip when ``jneqsim`` is missing.
"""
import copy
import json
import math
import os
import sys

import pytest
import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import fa_geometry  # noqa: E402
import fa_handoff  # noqa: E402
import fa_methods  # noqa: E402
import fa_study  # noqa: E402
import fa_thermal  # noqa: E402
import fa_units  # noqa: E402
import flow_assurance_report  # noqa: E402
from fa_units import FlowAssuranceError, GeometryError, TopologyError, UnitError  # noqa: E402

DEMO = os.path.join(HERE, "fa_examples", "offshore_tieback")


def _load(name):
    with open(os.path.join(DEMO, name), encoding="utf-8") as fh:
        return yaml.safe_load(fh)


@pytest.fixture(scope="module")
def route_model():
    findings, model = fa_geometry.check_route(_load("route.yaml"))
    assert not [f for f in findings if f["level"] == "error"]
    return model


# --- units -------------------------------------------------------------------------------------------
def test_unit_conversion_basic():
    assert fa_units.convert(100.0, "C", "K") == pytest.approx(373.15)
    assert fa_units.convert(1.0, "bara", "Pa") == pytest.approx(1.0e5)
    assert fa_units.convert(10.0, "barg", "bara") == pytest.approx(10.0 + fa_units.ATM_BAR)
    assert fa_units.convert(3600.0, "kg/h", "kg/s") == pytest.approx(1.0)


def test_unit_conversion_rejects_incompatible_and_dk():
    with pytest.raises(UnitError):
        fa_units.convert(1.0, "bara", "K")
    with pytest.raises(UnitError):
        fa_units.convert(5.0, "dK", "C")


def test_rate_basis_must_match_unit_and_be_positive():
    with pytest.raises(UnitError):
        fa_units.check_rate({"value": 1.0, "unit": "kg/s", "basis": "std_volume"})
    with pytest.raises(FlowAssuranceError):
        fa_units.check_rate({"value": -1.0, "unit": "kg/s", "basis": "mass"})
    with pytest.raises(FlowAssuranceError):
        fa_units.check_rate({"value": 1.0, "unit": "m3/h", "basis": "actual_volume"})
    with pytest.raises(FlowAssuranceError):
        fa_units.mass_rate_kg_s({"value": 1.0, "unit": "Sm3/d", "basis": "std_volume"})


def test_inhibitor_mass_versus_mole_is_not_confused():
    # 5 mol MEG per 15 mol water is 53 wt % MEG, not 25 %
    meg = 5.0 * fa_units.MW["MEG"]
    water = 15.0 * fa_units.MW["water"]
    assert fa_units.aqueous_wt_fraction(water, meg) == pytest.approx(0.534, abs=0.005)
    mol = fa_units.aqueous_inhibitor_moles(15.0, 0.25)
    assert mol * fa_units.MW["MEG"] / (mol * fa_units.MW["MEG"] + water) == pytest.approx(0.25)
    with pytest.raises(FlowAssuranceError):
        fa_units.aqueous_inhibitor_moles(15.0, 1.0)


def test_lean_injection_mass_balance_round_trip():
    lean = fa_units.lean_injection_rate(100.0, 0.30, 0.90)
    assert fa_units.aqueous_wt_after_injection(100.0, lean, 0.90) == pytest.approx(0.30)
    with pytest.raises(FlowAssuranceError):
        fa_units.lean_injection_rate(100.0, 0.95, 0.90)


def test_hammerschmidt_is_monotonic_and_bounded():
    assert fa_units.hammerschmidt_depression_k(0.4) > fa_units.hammerschmidt_depression_k(0.2) > 0.0
    with pytest.raises(FlowAssuranceError):
        fa_units.hammerschmidt_depression_k(1.0)


# --- geometry ----------------------------------------------------------------------------------------
def test_u_value_referenced_to_inner_area():
    seg = {"id": "X", "id_m": 0.2, "length_m": 100.0, "layers": [{"name": "steel", "thickness_m": 0.01, "k_w_mk": 45.0, "rho_kg_m3": 7850.0, "cp_j_kgk": 490.0},
                                                               {"name": "ins", "thickness_m": 0.05, "k_w_mk": 0.2, "rho_kg_m3": 900.0, "cp_j_kgk": 1900.0}],
           "ambient": {"t_c": 4.0, "type": "seabed", "external_h_w_m2k": 500.0}}
    th = fa_geometry.thermal_properties(seg)
    ri = 0.1
    r1, r2 = ri + 0.01, ri + 0.06
    resistance = math.log(r1 / ri) / (2 * math.pi * 45.0) + math.log(r2 / r1) / (2 * math.pi * 0.2) + 1.0 / (2 * math.pi * r2 * 500.0)
    assert th["u_inner_w_m2k"] == pytest.approx(1.0 / (2 * math.pi * ri * resistance), rel=1e-3)


def test_route_rejects_duplicate_ids_and_unknown_nodes():
    route = _load("route.yaml")
    dup = copy.deepcopy(route)
    dup["segments"][4]["id"] = dup["segments"][5]["id"]
    with pytest.raises(GeometryError):
        fa_geometry.check_route(dup)
    bad = copy.deepcopy(route)
    bad["segments"][4]["to"] = "NO-SUCH-NODE"
    with pytest.raises(GeometryError):
        fa_geometry.check_route(bad)


def test_route_rejects_length_shorter_than_chord():
    route = _load("route.yaml")
    route["segments"][4]["length_m"] = 100.0
    findings, _ = fa_geometry.check_route(route)
    assert any(f["code"] == "GEOM-LENGTH" and f["level"] == "error" for f in findings)


def test_route_rejects_non_numeric_length():
    route = _load("route.yaml")
    route["segments"][4]["length_m"] = "unavailable - TO BE CONFIRMED"
    with pytest.raises(GeometryError):
        fa_geometry.check_route(route)


def test_route_model_has_two_paths_and_one_sink(route_model):
    assert route_model["sink"] == "HOST-IN"
    assert sorted(route_model["sources"]) == ["TMPL-A", "TMPL-B"]
    assert len(route_model["paths"]) == 2


# --- topology variants -------------------------------------------------------------------------------
def test_variant_isolating_a_source_keeps_ids_stable(route_model):
    v = fa_geometry.apply_variant(route_model, {"id": "B-ISOLATED", "isolate_sources": ["TMPL-B"]})
    assert v["active_sources"] == ["TMPL-A"] and v["inactive_sources"] == ["TMPL-B"]
    assert "FL-B-01" in v["inactive_segments"] and "RS-01" in v["active_segments"]


def test_variant_isolating_shared_trunk_leaves_no_path(route_model):
    with pytest.raises(TopologyError):
        fa_geometry.apply_variant(route_model, {"id": "X", "isolate_segments": ["RS-01"]})


@pytest.mark.parametrize("key", ["bypass", "reroute", "add_segments", "new_assets"])
def test_unsupported_variant_operations_are_rejected(route_model, key):
    with pytest.raises(TopologyError):
        fa_geometry.apply_variant(route_model, {"id": "X", key: ["anything"]})


def test_variant_unknown_segment_or_source_is_rejected(route_model):
    with pytest.raises(TopologyError):
        fa_geometry.apply_variant(route_model, {"id": "X", "isolate_segments": ["NOPE"]})
    with pytest.raises(TopologyError):
        fa_geometry.apply_variant(route_model, {"id": "X", "isolate_sources": ["NOPE"]})


# --- thermal -----------------------------------------------------------------------------------------
def test_cooldown_closed_form_matches_rk4():
    tau = 3.0 * 3600.0
    exact = fa_thermal.time_to_temperature_s(60.0, 4.0, 20.0, tau)
    assert exact == pytest.approx(tau * math.log((60.0 - 4.0) / (20.0 - 4.0)))
    assert fa_thermal.rk4_time_to_temperature_s(60.0, 4.0, 20.0, tau) == pytest.approx(exact, rel=1e-3)
    assert fa_thermal.temperature_at(60.0, 4.0, tau, exact) == pytest.approx(20.0, abs=1e-6)


def test_cooldown_time_is_zero_when_already_below_and_none_when_unreachable():
    assert fa_thermal.time_to_temperature_s(15.0, 4.0, 20.0, 1000.0) == 0.0
    assert fa_thermal.time_to_temperature_s(60.0, 25.0, 20.0, 1000.0) == math.inf


# --- methods and hand-off ----------------------------------------------------------------------------
def test_method_register_never_marks_unimplemented_as_qualified():
    for m in fa_methods.METHODS:
        if str(m["implemented"]).lower().startswith("no"):
            assert str(m["experimentally_qualified"]).lower().startswith("no"), m["id"]


def test_threat_catalogue_is_complete_and_unsupported_threats_have_no_methods():
    ids = [t[0] for t in fa_methods.THREATS]
    assert len(ids) == len(set(ids))
    for tid, name, status, methods in fa_methods.THREATS:
        assert name
        if status in ("unsupported", "open"):
            assert not methods, tid


def test_event_with_unknown_case_is_an_input_error(route_model):
    basis = _load("basis.yaml")
    cases = _load("cases.yaml")
    cases["events"][0]["case"] = "NO-SUCH-CASE"
    findings, _, _ = fa_study.check_cases(cases, route_model, basis)
    assert any(f["code"] == "EVENT-CASE" and f["level"] == "error" for f in findings)


def _response(n=11, length=1000.0):
    pos = [length * i / (n - 1) for i in range(n)]
    return {"profile": {"positionM": pos, "pressureBara": [100.0 - i for i in range(n)], "temperatureC": [60.0 - i for i in range(n)], "liquidHoldup": [0.1] * n,
                        "gasVelocityMS": [5.0] * n, "liquidVelocityMS": [1.0] * n}, "simulationTimeSeconds": 10.0}


def test_two_fluid_profile_maps_segments_and_keeps_unavailable_as_none():
    segs = [{"id": "A", "length_m": 400.0}, {"id": "B", "length_m": 600.0}]
    prof = fa_handoff.two_fluid_profile(_response(), segs)
    assert prof["segment_id"][0] == "A" and prof["segment_id"][-1] == "B"
    assert all(v is None for v in prof["elevation_m"]) and all(v is None for v in prof["rho_mix_kg_m3"])
    assert prof["v_mix_m_s"][0] == pytest.approx(6.0)


def test_two_fluid_profile_rejects_length_mismatch_and_missing_profile():
    with pytest.raises(FlowAssuranceError):
        fa_handoff.two_fluid_profile(_response(), [{"id": "A", "length_m": 2000.0}])
    with pytest.raises(FlowAssuranceError):
        fa_handoff.two_fluid_profile({}, [{"id": "A", "length_m": 1000.0}])
    bad = _response()
    bad["profile"]["pressureBara"] = bad["profile"]["pressureBara"][:-1]
    with pytest.raises(FlowAssuranceError):
        fa_handoff.two_fluid_profile(bad, [{"id": "A", "length_m": 1000.0}])


# --- inputs and basis --------------------------------------------------------------------------------
def test_demo_inputs_load_without_errors():
    inputs, findings = fa_study.load_inputs(os.path.join(DEMO, "basis.yaml"), os.path.join(DEMO, "route.yaml"), os.path.join(DEMO, "cases.yaml"),
                                            os.path.join(HERE, "fa_templates", "standards_register.yaml"))
    assert not [f for f in findings if f["level"] == "error"]
    assert len(inputs["cases"]) == 8


def test_duplicate_case_ids_are_rejected(route_model):
    basis = _load("basis.yaml")
    cases = _load("cases.yaml")
    cases["cases"].append(copy.deepcopy(cases["cases"][0]))
    with pytest.raises(FlowAssuranceError):
        fa_study.check_cases(cases, route_model, basis)


def test_source_pressure_is_not_an_input_in_cases(route_model):
    cases = _load("cases.yaml")
    for c in cases["cases"]:
        for s in c["sources"].values():
            assert "p_bara" not in s


# --- CLI ---------------------------------------------------------------------------------------------
def test_cli_init_writes_templates_and_no_results(tmp_path, capsys):
    rc = flow_assurance_report.main(["--init", str(tmp_path / "tpl")])
    assert rc == 0
    names = sorted(os.listdir(tmp_path / "tpl"))
    assert {"basis.yaml", "route.yaml", "cases.yaml", "standards_register.yaml", "README.md"} <= set(names)
    assert not any(n.endswith((".docx", ".svg", ".json")) for n in names)


def test_cli_rejects_unfilled_template_with_exit_2(tmp_path, capsys):
    tpl = tmp_path / "tpl"
    flow_assurance_report.main(["--init", str(tpl)])
    out = tmp_path / "out"
    rc = flow_assurance_report.main([str(tpl / "route.yaml"), "--basis", str(tpl / "basis.yaml"), "--cases", str(tpl / "cases.yaml"), "--out", str(out), "--quiet"])
    assert rc == 2
    assert not out.exists()


def test_cli_without_source_is_usage_error(capsys):
    assert flow_assurance_report.main([]) == 2


def test_cli_rejects_two_fluid_json_as_study_source(tmp_path, capsys):
    p = tmp_path / "resp.json"
    p.write_text(json.dumps(_response()), encoding="utf-8")
    rc = flow_assurance_report.main([str(p), "--basis", os.path.join(DEMO, "basis.yaml"), "--out", str(tmp_path / "o"), "--quiet"])
    assert rc == 2


# --- end to end (JVM) --------------------------------------------------------------------------------
def _reduced_inputs(root, keep):
    basis = _load("basis.yaml")
    for c in basis["acceptance_criteria"]:
        c["applies_to"] = [x for x in c["applies_to"] if x in keep]
    basis["mitigation_options"] = [m for m in basis.get("mitigation_options", []) if m["case"] in keep and m["baseline"] in keep]
    cases = _load("cases.yaml")
    cases["cases"] = [c for c in cases["cases"] if c["id"] in keep]
    cases["topology_variants"] = [v for v in cases["topology_variants"] if v["id"] == "DEFAULT"]
    cases["events"] = [e for e in (cases.get("events") or []) if e.get("case") in keep]
    bp, cp = root / "basis.yaml", root / "cases.yaml"
    bp.write_text(yaml.safe_dump(basis, sort_keys=False), encoding="utf-8")
    cp.write_text(yaml.safe_dump(cases, sort_keys=False), encoding="utf-8")
    return bp, cp, cases


@pytest.fixture(scope="module")
def reduced_demo(tmp_path_factory):
    pytest.importorskip("jneqsim")
    root = tmp_path_factory.mktemp("fa_demo")
    bp, cp, _ = _reduced_inputs(root, {"BASE", "TURNDOWN"})
    out = root / "out"
    rc = flow_assurance_report.main([os.path.join(DEMO, "route.yaml"), "--basis", str(bp), "--cases", str(cp), "--out", str(out), "--no-word", "--no-mesh",
                                     "--no-results-json", "--quiet"])
    return rc, out


def _dataset(out):
    with open(out / "dataset" / "flow_assurance_study.json", encoding="utf-8") as fh:
        return json.load(fh)


def test_e2e_reduced_demo_runs_and_never_claims_reviewed(reduced_demo):
    rc, out = reduced_demo
    assert rc == 0
    ds = _dataset(out)
    with open(out / "report" / "validation_report.json", encoding="utf-8") as fh:
        val = json.load(fh)
    assert val["n_fail"] == 0
    assert val["readiness"]["label"] == "SCREENING"
    assert ds["schema"] == "flow_assurance_study.v1"


def test_e2e_drawings_are_written_for_every_sheet(reduced_demo):
    _, out = reduced_demo
    svgs = sorted(f for f in os.listdir(out / "drawings" / "svg") if f.endswith(".svg"))
    assert len(svgs) == 10
    assert os.path.exists(out / "drawings" / "layout_spec.json")


def test_e2e_open_threats_are_never_reported_as_passed(reduced_demo):
    _, out = reduced_demo
    ds = _dataset(out)
    for t in ds["threats"]:
        if t["status"] in ("open", "unsupported"):
            assert t["reason"]
            assert "pass" not in t["status"].lower()


def test_e2e_conservation_and_hydrostatic_sanity(reduced_demo):
    _, out = reduced_demo
    ds = _dataset(out)
    for cid, res in ds["results"].items():
        assert res["status"] == "converged", cid
        for sid, seg in res["segments"].items():
            assert math.isfinite(seg["p_in_bara"]) and seg["p_in_bara"] > 0.0
            assert -0.01 <= seg["holdup_mean"] <= 1.0


def test_e2e_dataset_hash_is_stable_against_volatile_fields(reduced_demo):
    _, out = reduced_demo
    ds = _dataset(out)
    other = copy.deepcopy(ds)
    other["meta"]["created"] = "1999-01-01T00:00:00"
    other["timings_s"] = {"total": 1.0}
    assert fa_study.dataset_hash(ds) == fa_study.dataset_hash(other)


def test_e2e_resolver_rejects_unit_mismatch_and_unknown_reference(reduced_demo):
    _, out = reduced_demo
    ds = _dataset(out)
    res = fa_study.FaResolver(ds)
    seg_id = next(iter(ds["results"]["BASE"]["segments"]))
    assert res.text("segres:BASE:%s:p_in_bara" % seg_id, ".1f") not in ("n/a", "unavailable")
    assert res.text("segres:BASE:%s:p_in_bara" % seg_id, ".1f", "unit=K") == "n/a"
    assert res.unit_errors
    assert res.text("segres:BASE:NOPE:p_in_bara", ".1f") == "n/a"
    assert res.errors


def test_e2e_strict_mode_exits_nonzero_on_validation_failure(tmp_path):
    pytest.importorskip("jneqsim")
    keep = {"BASE"}
    bp, cp, cases = _reduced_inputs(tmp_path, keep)
    # an unreachable host pressure makes the solve fail; the package must be a NOT_READY diagnostic, never a pass
    cases["cases"][0]["boundary"]["outlet"]["value"]["value"] = 5.0e3
    cp.write_text(yaml.safe_dump(cases, sort_keys=False), encoding="utf-8")
    out = tmp_path / "out"
    rc = flow_assurance_report.main([os.path.join(DEMO, "route.yaml"), "--basis", str(bp), "--cases", str(cp), "--out", str(out), "--no-word",
                                     "--no-mesh", "--no-results-json", "--strict", "--quiet"])
    assert rc in (1, 2)
    if rc == 1:
        with open(out / "report" / "validation_report.json", encoding="utf-8") as fh:
            assert json.load(fh)["readiness"]["label"] == "NOT_READY"


# --- second geometry: single path, no junction, no PLEM, route along one axis -------------------------
SINGLE = os.path.join(HERE, "fa_examples", "single_flowline")


@pytest.fixture(scope="module")
def single_path_run(tmp_path_factory):
    pytest.importorskip("jneqsim")
    out = tmp_path_factory.mktemp("fa_single") / "out"
    rc = flow_assurance_report.main([os.path.join(SINGLE, "route.yaml"), "--basis", os.path.join(SINGLE, "basis.yaml"), "--cases", os.path.join(SINGLE, "cases.yaml"),
                                     "--out", str(out), "--no-word", "--no-mesh", "--no-results-json", "--quiet", "--strict"])
    return rc, out


def test_single_path_route_builds_with_nine_sheets_and_no_failed_check(single_path_run):
    rc, out = single_path_run
    assert rc == 0
    svgs = sorted(f for f in os.listdir(out / "drawings" / "svg") if f.endswith(".svg"))
    assert len(svgs) == 9 and not any(f.endswith("DWG-04.svg") for f in svgs)
    with open(out / "report" / "validation_report.json", encoding="utf-8") as fh:
        val = json.load(fh)
    assert val["n_fail"] == 0 and val["readiness"]["label"] == "SCREENING"


def _paragraph_text(docx_path):
    import docx

    return "\n".join(p.text for p in docx.Document(str(docx_path)).paragraphs)


def test_single_path_report_has_no_demo_specific_statements(single_path_run):
    _, out = single_path_run
    docx_path = next(out.joinpath("report").glob("*.docx"))
    text = _paragraph_text(docx_path)
    for phrase in ("trunk", "thinly insulated branch", "M2", "M3", "M4", "TMPL-A", "TMPL-B", "TURNDOWN", "180 bara", "30 wt% aqueous MEG, still insufficient"):
        assert phrase not in text, phrase


def test_single_path_narrative_is_derived_from_the_dataset(single_path_run):
    _, out = single_path_run
    ds = _dataset(out)
    text = _paragraph_text(next(out.joinpath("report").glob("*.docx")))
    low = min(ds["cases"], key=lambda c: ds["results"][c["id"]]["extrema"]["hyd_margin_min"]["value"])["id"]
    assert "smallest inhibited margin of all cases is" in text and "in case %s " % low in text
    only = ds["mitigations"][0]
    assert only["id"] in text and only["case"] in text


def test_single_path_drawing_numbers_are_stable_and_sheet_numbers_consecutive(single_path_run):
    _, out = single_path_run
    with open(out / "drawings" / "layout_spec.json", encoding="utf-8") as fh:
        spec = json.load(fh)
    assert len(spec["sheets"]) == 9
    text = (out / "drawings" / "svg" / "FA-TEST-001_DWG-07.svg").read_text(encoding="utf-8")
    assert "Sheet 6 of 9" in text
