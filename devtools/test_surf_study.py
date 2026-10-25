"""Tests of the SURF study toolkit (specification, model generation, design assessment, drawings, report, CLI).

Fast tests run without the JVM. The end-to-end tests start the NeqSim JVM on the workspace classes through
``surf_model.start_jvm`` and skip when the runtime (jpype or a NeqSim with ``FieldNetworkTopology``) is unavailable.
"""
import copy
import json
import math
import os
import sys

import pytest
import yaml

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import surf_dataset  # noqa: E402
import surf_design  # noqa: E402
import surf_model  # noqa: E402
import surf_quantities  # noqa: E402
import surf_run  # noqa: E402
import surf_spec  # noqa: E402
import surf_study  # noqa: E402
import surf_validate  # noqa: E402

DEMO = surf_study.DEMO_SPEC


def load_demo():
    with open(DEMO, encoding="utf-8") as fh:
        return yaml.safe_load(fh)


@pytest.fixture(scope="module")
def demo():
    spec = load_demo()
    findings, model = surf_spec.check_spec(spec)
    return spec, findings, model


def codes(findings, level="error"):
    return {f["code"] for f in findings if f["level"] == level}


# --- specification ---------------------------------------------------------------------------------
def test_demo_specification_has_no_errors_and_three_concepts(demo):
    spec, findings, model = demo
    assert not codes(findings), findings
    assert set(model["concepts"]) == {"A", "B", "C"}
    assert set(model["cases"]) == {"BASE", "TURNDOWN", "LATE"}


def test_hydraulic_id_is_od_minus_two_walls_and_route_length_is_the_3d_polyline(demo):
    spec, _f, model = demo
    e = model["edges"]["FL-TRUNK"]
    assert e["id_m"] == pytest.approx((323.9 - 2 * 15.9) / 1000.0)
    pts = e["points"]
    manual = sum(math.dist(a, b) for a, b in zip(pts[:-1], pts[1:]))
    assert e["route_length_m"] == pytest.approx(manual)
    assert e["installed_length_m"] == pytest.approx(manual)  # no allowance: it must not be added twice


def test_concept_override_changes_dimensions_but_not_identity(demo):
    spec, _f, model = demo
    a = surf_spec.case_for_concept(model, spec, "A")["FL-TRUNK"]
    c = surf_spec.case_for_concept(model, spec, "C")["FL-TRUNK"]
    assert a["id"] == c["id"] == "FL-TRUNK"
    assert c["id_m"] < a["id_m"]
    assert c["id_m"] == pytest.approx((219.1 - 2 * 12.7) / 1000.0)
    assert "FL-T2D" not in surf_spec.case_for_concept(model, spec, "A")
    assert "FL-T2D" in surf_spec.case_for_concept(model, spec, "B")


def test_allowance_is_applied_once_and_needs_a_basis():
    spec = load_demo()
    next(e for e in spec["edges"] if e["id"] == "FL-TRUNK")["allowance_fraction"] = 0.05
    f, _m = surf_spec.check_spec(spec)
    assert "ALLOWANCE" in codes(f)
    next(e for e in spec["edges"] if e["id"] == "FL-TRUNK")["allowance_basis"] = "synthetic as-laid allowance"
    f, m = surf_spec.check_spec(spec)
    assert not codes(f)
    e = m["edges"]["FL-TRUNK"]
    assert e["installed_length_m"] == pytest.approx(e["route_length_m"] * 1.05)


def test_failure_fixture_missing_geometry():
    spec = load_demo()
    del next(n for n in spec["nodes"] if n["id"] == "M1")["z_m"]
    f, m = surf_spec.check_spec(spec)
    assert "GEOMETRY-MISSING" in codes(f) and m is None


def test_failure_fixture_duplicate_port_is_a_disconnected_or_ambiguous_port():
    spec = load_demo()
    next(e for e in spec["edges"] if e["id"] == "J-P2")["to_port"] = "in-P1"
    f, _m = surf_spec.check_spec(spec)
    assert "PORT-DUPLICATE" in codes(f)


def test_failure_fixture_disconnected_node():
    spec = load_demo()
    spec["edges"] = [e for e in spec["edges"] if e["id"] != "J-P4"]
    f, _m = surf_spec.check_spec(spec)
    assert "TOPOLOGY-ORPHAN" in codes(f)
    spec = load_demo()
    spec["edges"] = [e for e in spec["edges"] if e["id"] not in ("FL-TRUNK", "FL-T2D")]
    f, _m = surf_spec.check_spec(spec)
    assert "TOPOLOGY-DISCONNECTED" in codes(f)


def test_failure_fixture_conflicting_dimensions_and_declared_length():
    spec = load_demo()
    e = next(e for e in spec["edges"] if e["id"] == "FL-TRUNK")
    e["id_mm"] = 300.0
    e["declared_length_m"] = 9000.0
    f, _m = surf_spec.check_spec(spec)
    assert {"DIMENSION-CONFLICT", "LENGTH-CONFLICT"} <= codes(f)


def test_gauge_pressure_and_unavailable_values_are_rejected():
    spec = load_demo()
    spec["cases"][0]["host_pressure_barg"] = 10.0
    spec["fluids"]["prod_early"]["source"] = "unavailable - TO BE CONFIRMED"
    f, _m = surf_spec.check_spec(spec)
    assert {"UNIT-GAUGE", "VALUE-UNAVAILABLE"} <= codes(f)


def test_a_single_concept_is_rejected_because_concept_selection_needs_alternatives():
    spec = load_demo()
    spec["concepts"] = spec["concepts"][:1]
    f, _m = surf_spec.check_spec(spec)
    assert "CONCEPTS" in codes(f)


def test_flexible_line_needs_a_supplier_u_value_not_a_derived_one():
    spec = load_demo()
    del next(e for e in spec["edges"] if e["id"] == "RI-1")["thermal"]["u_value_w_m2k"]
    f, _m = surf_spec.check_spec(spec)
    assert "THERMAL-FLEX" in codes(f)


def test_template_is_refused_with_exit_2(tmp_path, capsys):
    out = tmp_path / "tpl"
    assert surf_study.main(["--init", str(out)]) == 0
    assert (out / "study.yaml").is_file() and (out / "standards_register.yaml").is_file()
    assert surf_study.main([str(out / "study.yaml"), "--out", str(tmp_path / "x"), "--quiet"]) == 2
    assert not (tmp_path / "x" / "dataset").exists()


def test_cli_without_source_is_a_usage_error(capsys):
    assert surf_study.main([]) == 2


# --- generated plan, builder, caching --------------------------------------------------------------
def test_plan_keeps_specification_ids_and_services(demo):
    spec, _f, model = demo
    plan = surf_model.build_plan(spec, model, "A", "BASE", "production")
    ids = {o["id"] for o in plan if o["op"] in ("node", "pipe", "pump")}
    assert {"P1", "P2", "P3", "P4", "M1", "M2", "PLEM-1", "HOST-IN", "FL-T2", "FL-TRUNK", "RI-1", "J-P1"} <= ids
    assert "FL-T2D" not in ids and "I1" not in ids
    inj = surf_model.build_plan(spec, model, "A", "BASE", "injection")
    assert {"INJ-SRC", "INJ-PUMP", "IFL-1", "I1", "I2"} <= {o["id"] for o in inj if o["op"] in ("node", "pipe", "pump")}


def test_diameter_propagates_from_the_concept_to_plan_and_line_design(demo):
    spec, _f, model = demo
    d_a = next(o for o in surf_model.build_plan(spec, model, "A", "BASE", "production") if o.get("id") == "FL-TRUNK")
    d_c = next(o for o in surf_model.build_plan(spec, model, "C", "BASE", "production") if o.get("id") == "FL-TRUNK")
    assert d_a["diameter_m"] == pytest.approx(0.2921) and d_c["diameter_m"] == pytest.approx(0.1937)
    ld_c = next(o for o in surf_model.build_plan(spec, model, "C", "BASE", "production") if o["op"] == "line_design" and o["edge"] == "FL-TRUNK")
    assert ld_c["od_mm"] == pytest.approx(219.1) and ld_c["dnv_st_f101"] is not None


def test_cache_key_changes_with_inputs_and_mesh_but_not_with_descriptions(demo):
    spec, _f, model = demo
    base = surf_run.cache_key(surf_model.build_plan(spec, model, "A", "BASE", "production"), "production", 1)
    assert base == surf_run.cache_key(surf_model.build_plan(spec, model, "A", "BASE", "production"), "production", 1)
    assert base != surf_run.cache_key(surf_model.build_plan(spec, model, "A", "BASE", "production"), "production", 2)
    changed = copy.deepcopy(spec)
    next(n for n in changed["nodes"] if n["id"] == "P1")["well"]["productivity_index_msm3d_per_bar2"] = 2.0e-5
    f, m2 = surf_spec.check_spec(changed)
    assert base != surf_run.cache_key(surf_model.build_plan(changed, m2, "A", "BASE", "production"), "production", 1)
    relabelled = copy.deepcopy(spec)
    relabelled["study"]["objective"] = "different words"
    f, m3 = surf_spec.check_spec(relabelled)
    assert base == surf_run.cache_key(surf_model.build_plan(relabelled, m3, "A", "BASE", "production"), "production", 1)


def test_emitted_builder_is_valid_python_with_the_same_plan(demo, tmp_path):
    spec, _f, model = demo
    plan = surf_model.build_plan(spec, model, "A", "BASE", "production")
    p = surf_model.emit_python(plan, spec, "A", "BASE", str(tmp_path / "b.py"))
    src = open(p, encoding="utf-8").read()
    compile(src, p, "exec")
    ns = {}
    head = src.split("domain = surf_model.execute_plan")[0].split("PLAN = ", 1)[1]
    assert eval(head, {"__builtins__": {}}) == plan  # the literal plan in the builder equals the generated plan


# --- quantities ------------------------------------------------------------------------------------
def test_mto_steel_mass_matches_the_annulus_formula(demo):
    spec, _f, model = demo
    mto = surf_quantities.mto(spec, model, "A")
    trunk = next(x for x in mto["lines"] if x["id"] == "FL-TRUNK")
    e = model["edges"]["FL-TRUNK"]
    assert trunk["steel_mass_kg"] == pytest.approx(7850.0 * math.pi / 4 * (0.3239 ** 2 - 0.2921 ** 2) * e["installed_length_m"])
    assert mto["equipment_counts"]["production_well"] == 4 and mto["equipment_counts"]["injection_well"] == 2


def test_line_register_installed_length_equals_the_model_edge(demo):
    spec, _f, model = demo
    for r in surf_quantities.line_register(spec, model, "B"):
        assert r["installed_length_m"] == pytest.approx(model["edges"][r["id"]]["installed_length_m"])


# --- assessment logic (no solver) ------------------------------------------------------------------
def test_criteria_never_pass_a_case_that_did_not_converge_and_missing_values_are_not_pass(demo):
    spec, _f, _m = demo
    good = {"converged": True, "problems": [], "quantities": {c["quantity"]: {"value": 0.0 if c["operator"] == "<=" else 1e9, "where": "X", "unit": "-"} for c in spec["acceptance_criteria"]}}
    bad = {"converged": False, "problems": ["hydraulic fallback or failure on FL-TRUNK (BEGGS_BRILL_FALLBACK_DARCY)"], "quantities": good["quantities"]}
    unknown = {"converged": True, "problems": [], "quantities": {}}
    out = surf_design.evaluate_criteria(spec, {"BASE": good, "LATE": bad, "TURNDOWN": unknown})
    assert all(c["by_case"]["LATE"]["status"] == "FAIL" and "fallback" in c["by_case"]["LATE"]["reason"] for c in out)
    assert all(c["by_case"]["TURNDOWN"]["status"] == "NOT ASSESSED" for c in out)
    assert all(c["by_case"]["BASE"]["status"] == "PASS" for c in out)


def test_concept_comparison_ranks_only_feasible_concepts(demo):
    spec, _f, _m = demo
    crit = {"A": [{"id": "X", "by_case": {"BASE": {"status": "PASS"}}}], "B": [{"id": "X", "by_case": {"BASE": {"status": "FAIL"}}}], "C": [{"id": "X", "by_case": {"BASE": {"status": "NOT ASSESSED"}}}]}
    cost = {"A": {"total_usd": 3.0e8}, "B": {"total_usd": 1.0e8}, "C": {"total_usd": 2.0e8}}
    cmp = surf_design.concept_comparison(spec, ["A", "B", "C"], crit, cost)
    assert cmp["selected"] == "A"
    assert {r["concept"]: r["feasibility"] for r in cmp["rows"]} == {"A": "feasible", "B": "infeasible", "C": "not_assessed"}
    crit["A"][0]["by_case"]["BASE"]["status"] = "FAIL"
    assert surf_design.concept_comparison(spec, ["A", "B", "C"], crit, cost)["selected"] is None


def test_swamee_jain_matches_the_laminar_and_a_known_turbulent_value():
    assert surf_design.swamee_jain(1000.0, 1e-4) == pytest.approx(0.064)
    assert surf_design.swamee_jain(1.0e5, 1.0e-4) == pytest.approx(0.0185, abs=0.0015)


def test_hydrate_interpolation_is_unavailable_outside_the_curve():
    curve = {"p_bara": [20.0, 40.0, 70.0], "t_c": [5.0, 12.0, 17.0]}
    assert surf_design.t_hyd(curve, 10.0) is None and surf_design.t_hyd(curve, 100.0) is None
    assert 5.0 < surf_design.t_hyd(curve, 30.0) < 12.0


def test_readiness_is_never_reviewed_and_failed_checks_make_it_not_ready():
    ds = {"meta": {"synthetic": True}, "handoffs": [1], "data_gaps": [1]}
    assert surf_validate.compute_readiness(ds, [{"id": "a", "status": "PASS"}])["label"] == "SCREENING"
    assert surf_validate.compute_readiness(ds, [{"id": "a", "status": "FAIL"}])["label"] == "NOT_READY"


def test_standards_register_makes_no_conformity_claim():
    reg = surf_study.load_standards()
    assert len(reg) >= 20
    assert all(not s["text_available"] for s in reg)
    assert not any(surf_validate.CONFORMITY.search(str(s["status"])) for s in reg)
    ids = {s["id"] for s in reg}
    assert {"ISO-13628-1", "DNV-ST-F101", "DNV-ST-F201", "API-17J", "ISO-10628-1", "ISO-7200"} <= ids


def test_resolver_rejects_unknown_reference_and_unit_mismatch():
    ds = {"facts": {"n": 3}, "results": {"a": {"p_bara": 10.0, "t_c": 5.0}}}
    rs = surf_dataset.SurfResolver(ds)
    assert rs.text("fact:n") == "3"
    assert rs.text("ds:results.a.p_bara", ".1f", "unit=bara") == "10.0"
    assert rs.text("ds:results.a.p_bara", ".1f", "unit=C") == "n/a" and rs.unit_errors
    assert rs.text("ds:results.nope.x") == "n/a" and "ds:results.nope.x" in rs.errors


# --- end to end (JVM) ------------------------------------------------------------------------------
def mini_spec():
    spec = load_demo()
    spec["cases"] = [c for c in spec["cases"] if c["id"] == "BASE"]
    spec["concepts"] = [c for c in spec["concepts"] if c["id"] in ("A", "B")]
    for e in spec["edges"]:
        if e["kind"] != "pump" and e["hydraulic"]["model"] == "beggs_brill":
            e["hydraulic"]["segments"] = max(1, e["hydraulic"]["segments"] // 3)
        if e.get("concepts") == ["A", "C"]:
            e["concepts"] = ["A"]
    spec["solver"]["hydrate_grid_bara"] = [20.0, 70.0, 160.0]
    del spec["solver"]["mesh_sensitivity"]
    spec["document"]["document_number"] = "SURF-TEST-001"
    return spec


@pytest.fixture(scope="module")
def mini_run(tmp_path_factory):
    pytest.importorskip("jpype")
    try:
        surf_model.start_jvm()
    except surf_model.ModelError as exc:
        pytest.skip(str(exc))
    root = tmp_path_factory.mktemp("surf")
    spec_path = root / "mini.yaml"
    spec_path.write_text(yaml.safe_dump(mini_spec(), sort_keys=False), encoding="utf-8")
    out = root / "out"
    code, summary = surf_study.run_pipeline(str(spec_path), str(out), "all", strict=False, quiet=True, use_word=False)
    with open(out / "dataset" / "surf_study.json", encoding="utf-8") as fh:
        ds = json.load(fh)
    return {"root": root, "spec_path": spec_path, "out": out, "code": code, "summary": summary, "ds": ds}


def test_e2e_generates_and_solves_the_network_with_closed_balances(mini_run):
    ds = mini_run["ds"]
    for cid in ds["concepts"]:
        rec = ds["results"][cid]["BASE"]
        assert rec["derived"]["converged"], rec["derived"]["problems"]
        wells = sum(w["applied_rate_kg_s"] for w in rec["production"]["wells"])
        assert rec["production"]["host"]["mass_rate_kg_s"] == pytest.approx(wells, rel=1e-4)
        assert rec["production"]["coupling"]["mass_balance_residual_kg_s"] < 1e-6
        assert all(h["status"] == "PASS" for h in ds["hand_checks"][cid] if h["id"].startswith(("HC-MASS", "HC-NODE", "HC-HYDRO", "HC-THERMAL")))


def test_e2e_producers_are_solved_not_prescribed_and_pressures_are_ordered(mini_run):
    rec = mini_run["ds"]["results"]["A"]["BASE"]["production"]
    p = {n: v["pressure_bara"] for n, v in rec["nodes"].items()}
    assert p["HOST-IN"] == pytest.approx(40.0) and p["P1"] > p["M1"] > p["PLEM-1"] > p["HOST-IN"]
    assert p["M2"] > p["M1"]  # the daisy-chained template sees the extra line loss
    assert all(w["status"] == "FLOWING" for w in rec["wells"])


def test_e2e_generated_builder_reproduces_the_topology_definition(mini_run):
    out = mini_run["out"] / "model"
    ns = {"__name__": "generated_builder"}
    exec(compile(open(out / "builders" / "build_A_production.py", encoding="utf-8").read(), "build_A_production.py", "exec"), ns)
    replay = json.loads(str(ns["topology"].toJson()))
    stored = json.loads(open(out / "topology_A_production.json", encoding="utf-8").read())
    assert replay == stored
    nodes = {n["id"] for n in replay["nodes"]}
    manifest = json.load(open(out / "mapping_manifest.json", encoding="utf-8"))["A"]
    mapped = {m["id"] for m in manifest if m["domain"] == "production" and m["type"] == "node"}
    assert mapped == nodes


def test_e2e_cost_basis_and_register_reconcile(mini_run):
    ds = mini_run["ds"]
    for cid in ds["concepts"]:
        assert all(r["status"] == "PASS" for r in ds["registers"][cid]["reconciliation"])
        assert ds["cost"][cid]["total_usd"] > 0 and ds["cost"][cid]["currency"] == "USD"
    assert ds["cost"]["B"]["total_usd"] > ds["cost"]["A"]["total_usd"]  # one more 13 km line


def test_e2e_drawing_set_report_and_validation(mini_run):
    out, s = mini_run["out"], mini_run["summary"]
    assert s["n_sheets"] == 15 and os.path.isfile(s["drawings_pdf"]) and os.path.isfile(s["docx"])
    layout = json.load(open(out / "drawings" / "layout_spec.json", encoding="utf-8"))
    assert all(sh["qa"]["status"] == "PASS" for sh in layout["sheets"]), [(sh["code"], sh["qa"]["status"]) for sh in layout["sheets"] if sh["qa"]["status"] != "PASS"]
    checks = s["validation_checks"]
    assert checks["V-DWG-ASSETS"] == "PASS" and checks["V-DOC-TOKENS"] == "PASS" and checks["V-DOC-SECTIONS"] == "PASS" and checks["V-RECONCILE"] == "PASS"
    assert s["readiness"]["label"] in ("SCREENING", "NOT_READY") and s["readiness"]["label"] != "REVIEWED"
    assert json.load(open(out / "handoff" / "HO-01.json", encoding="utf-8"))["status"] == "open"
    for sheet in os.listdir(out / "drawings" / "svg"):
        assert sheet.endswith(".svg")


def test_e2e_report_stage_refuses_a_stale_dataset(mini_run, tmp_path):
    spec = mini_spec()
    spec["cases"][0]["host_pressure_bara"] = 45.0
    p = tmp_path / "changed.yaml"
    p.write_text(yaml.safe_dump(spec, sort_keys=False), encoding="utf-8")
    shutil_out = mini_run["out"]
    with pytest.raises(surf_spec.SpecError, match="stale dataset"):
        surf_study.run_pipeline(str(p), str(shutil_out), "report", quiet=True, use_word=False)


def test_e2e_strict_mode_exits_nonzero_when_a_gate_fails(mini_run, tmp_path):
    spec = mini_spec()
    spec["acceptance_criteria"][4]["value"] = 5.0  # host inlet limit far below the solved rate: both concepts infeasible
    p = tmp_path / "strict.yaml"
    p.write_text(yaml.safe_dump(spec, sort_keys=False), encoding="utf-8")
    out = tmp_path / "out"
    code = surf_study.main([str(p), "--out", str(out), "--strict", "--no-word", "--quiet"])
    ds = json.load(open(out / "dataset" / "surf_study.json", encoding="utf-8"))
    assert ds["selected_concept"] is None
    assert {r["feasibility"] for r in ds["comparison"]["rows"]} == {"infeasible"}
    assert code in (0, 1)  # an infeasible concept set is a result, not a gate failure; strict fails only on validation FAIL
    assert ds["validation"]["status"] in ("PASS", "FAIL")
