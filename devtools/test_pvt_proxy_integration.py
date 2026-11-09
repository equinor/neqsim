"""NeqSim-backed acceptance tests of the PVT forward-proxy generator.

Covers spec acceptance tests 1 (identical-process recovery), 2 (reference with liquid recycle),
3 (multiple fluid classes, one parameter set), 5 (failed reference case kept), 6 (reproducibility),
7 (extrapolation warning) and 8 (dry-gas dominance) with real NeqSim flowsheets. Skipped when no
Java runtime / NeqSim classes are available.
"""
import copy
import math
import sys
from pathlib import Path

import pytest
import yaml

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from pvt_proxy import workflow  # noqa: E402
from pvt_proxy.calibrate import Calibrator, run_references  # noqa: E402
from pvt_proxy.config import parse_config  # noqa: E402
from pvt_proxy.fluids import generate_ensemble  # noqa: E402

EXAMPLES = HERE / "pvt_proxy" / "examples"


@pytest.fixture(scope="module", autouse=True)
def neqsim_runtime():
    # The HotSpot JVM uses access violations internally (safepoints, implicit null checks) and handles
    # them itself; pytest's faulthandler would print each as a "fatal exception" traceback.
    import faulthandler
    faulthandler.disable()
    try:
        from pvt_proxy.engine import jclass
        jclass()("neqsim.thermo.system.SystemSrkEos")
    except Exception as exc:  # noqa: BLE001
        pytest.skip("NeqSim runtime unavailable: %s" % exc)


def _quiet(*_args):
    return None


def _example(name):
    return yaml.safe_load((EXAMPLES / name).read_text(encoding="utf-8"))


def _unit_classes(ps):
    return [str(u.getClass().getSimpleName()) for u in ps.getUnitOperations()]


@pytest.fixture(scope="module")
def synthetic_run(tmp_path_factory):
    out = tmp_path_factory.mktemp("synthetic")
    cfg = parse_config(_example("synthetic_three_stage.yaml"), base_dir=EXAMPLES)
    return cfg, out, workflow.run(cfg, out, log=_quiet)


def test_identical_process_recovery(synthetic_run):
    """Test 1: reference and proxy are the same forward process; the known conditions are recovered."""
    _, out, summary = synthetic_run
    stages = {s["name"]: s for s in summary["definition"]["proxy_process"]["stages"]}
    assert stages["hp"]["pressure_bara"] == pytest.approx(60.0, abs=0.3)
    assert stages["lp"]["temperature_C"] == pytest.approx(35.0, abs=0.3)
    assert summary["definition"]["proxy_process"]["calibration"]["status"]["status"] == "pass"
    for name in ("proxy_definition.yaml", "proxy_definition.json", "validation_per_fluid.csv", "validation_report.md",
                 "fluids.json", "reference_results.csv", "exports/e300_sepcond.inc", "exports/separator_stages.csv"):
        assert (out / name).is_file(), name


def test_reproducible_calibration(synthetic_run):
    """Test 6: same configuration and seed give the same parameters."""
    cfg, _, _ = synthetic_run
    eng, ens = workflow.prepare(cfg, log=_quiet)
    refs = run_references(cfg, eng, ens, log=_quiet)
    a = Calibrator(cfg, eng, ens, refs, log=_quiet).run()
    b = Calibrator(cfg, eng, ens, refs, log=_quiet).run()
    assert a.free_values == pytest.approx(b.free_values, rel=1e-9)


def test_extrapolation_warning_for_fluid_outside_calibration_range(synthetic_run):
    """Test 7: a much heavier oil is evaluated but flagged as extrapolation."""
    cfg, out, summary = synthetic_run
    eng, ens = workflow.prepare(cfg, log=_quiet)
    spec = copy.deepcopy(cfg.fluids)
    spec["base_fluids"]["heavy_oil"] = {"methane": 0.20, "ethane": 0.04, "propane": 0.04, "n-butane": 0.02,
                                        "n-hexane": 0.03, "C7": 0.12, "C10": 0.20, "C15": 0.17, "C25": 0.18}
    spec["methods"] = [{"type": "explicit", "fluids": ["heavy_oil", "volatile_oil"]}]
    new = generate_ensemble(spec, ens["template"], engine=eng)
    rows = {r["fluid_id"]: r for r in workflow.evaluate_fluids(summary["definition"], cfg, new, eng)}
    assert rows["heavy_oil"]["status"] == "ok" and rows["heavy_oil"]["extrapolation"]
    assert any("outside the calibration range" in w for w in rows["heavy_oil"]["warnings"])
    assert not rows["volatile_oil"]["extrapolation"]


@pytest.fixture(scope="module")
def recompression_run(tmp_path_factory):
    d = _example("recompression_reference.yaml")
    d["fluids"]["methods"] = [{"type": "explicit"},
                              {"type": "mixing", "label": "mix", "endmembers": ["black_oil", "condensate"], "n": 2}]
    d["optimizer"] = {"method": "lsq", "local_maxiter": 25}
    out = tmp_path_factory.mktemp("recompression")
    cfg = parse_config(d, base_dir=EXAMPLES)
    return cfg, out, workflow.run(cfg, out, log=_quiet)


def test_reference_with_liquid_recycle_and_forward_proxy(recompression_run):
    """Test 2: recycle only in the reference; residual errors are quantified."""
    cfg, _, summary = recompression_run
    eng, ens = workflow.prepare(cfg, log=_quiet)
    case = ens["cases"][0]
    ref = eng.run_reference(cfg.reference, ens["template"], case.z)
    assert ref["status"] == "ok" and ref["n_recycles"] >= 1
    assert abs(ref["mass_balance_rel_err"]) < 1e-4
    train = eng.forward_train(ens["template"], case.z, cfg.proxy)
    assert "Recycle" not in _unit_classes(train.ps)
    m = summary["metrics"]
    assert m["n_reference_failed"] == 0 and m["n_proxy_failed"] == 0
    assert math.isfinite(m["oil"]["mape_pct"]) and math.isfinite(m["gas"]["mape_pct"])
    opt = summary["definition"]["proxy_process"]["calibration"]["optimizer"]
    assert opt["objective"] < opt["initial_objective"]


def test_multiple_fluid_classes_one_parameter_set(recompression_run):
    """Test 3: heavy oil, volatile oil, condensate and gas fluids share one P/T set; errors per class."""
    _, _, summary = recompression_run
    classes = set(summary["by_class"])
    assert {"black_oil", "volatile_oil", "gas_condensate", "dry_gas"} <= classes
    pp = summary["definition"]["proxy_process"]
    assert len(pp["stages"]) == 3 and pp["scrubber"] is not None
    assert all(isinstance(s["pressure_bara"], float) for s in pp["stages"])


def test_super_dry_gas_does_not_dominate(recompression_run):
    """Test 8: the super-dry fluid is down-weighted and the moderate-GOR oils stay accurate."""
    _, _, summary = recompression_run
    rows = {r["fluid_id"]: r for r in summary["rows"]}
    dry = rows["super_dry_gas"]
    assert dry["fluid_class"] == "dry_gas"
    assert dry["effective_weight"] < dry["user_weight"]
    assert not any("dry-gas" in f for f in summary["dominance"]["flags"])
    moderate = [r for r in summary["rows"] if r["status"] == "ok" and r["ref_gor_Sm3_Sm3"] < 2000.0]
    assert moderate and max(abs(r["oil_rel_err"]) for r in moderate) < 0.03


def test_failed_reference_case_is_kept_with_reason(tmp_path):
    """Test 5: a failing reference simulation is recorded, reported and excluded from the fit, not dropped."""
    (tmp_path / "flaky_reference.py").write_text(
        "def run(fluid, feed, std, J):\n"
        "    if fluid.getComponent('methane').getz() > 0.8:\n"
        "        raise RuntimeError('deliberate reference failure')\n"
        "    ops = J('neqsim.thermodynamicoperations.ThermodynamicOperations')\n"
        "    fluid.setTemperature(std.temperature_K)\n"
        "    fluid.setPressure(std.pressure_bara)\n"
        "    ops(fluid).TPflash()\n"
        "    fluid.initProperties()\n"
        "    n = feed.rate_kmol_h / fluid.getTotalNumberOfMoles()\n"
        "    gas = fluid.getPhase(0).getNumberOfMolesInPhase() * n * std.sm3_per_kmol\n"
        "    liq = fluid.getPhase(1)\n"
        "    oil = liq.getNumberOfMolesInPhase() * n * liq.getMolarMass() / liq.getDensity('kg/m3')\n"
        "    return {'oil_Sm3_h': oil, 'gas_Sm3_h': gas}\n", encoding="utf-8")
    d = _example("recompression_reference.yaml")
    d["fluids"]["methods"] = [{"type": "explicit", "fluids": ["black_oil", "volatile_oil", "condensate", "lean_gas"]}]
    d["fluids"].pop("weights")
    d["reference"] = {"type": "python", "id": "flaky", "callable": "flaky_reference:run"}
    d["proxy"].pop("scrubber")
    d["optimizer"] = {"method": "lsq", "local_maxiter": 5}
    cfg = parse_config(d, base_dir=tmp_path)
    summary = workflow.run(cfg, tmp_path / "out", log=_quiet)
    rows = {r["fluid_id"]: r for r in summary["rows"]}
    assert rows["lean_gas"]["status"] == "reference_failed"
    assert "deliberate reference failure" in rows["lean_gas"]["failure_reason"]
    assert rows["lean_gas"]["effective_weight"] == 0.0
    assert summary["metrics"]["n_reference_failed"] == 1
    assert summary["definition"]["proxy_process"]["calibration"]["status"]["status"] == "review_required"
    assert "lean_gas" in (tmp_path / "out" / "validation_report.md").read_text(encoding="utf-8")


def test_constant_volume_depletion_generates_valid_compositions():
    cfg = parse_config(_example("recompression_reference.yaml"), base_dir=EXAMPLES)
    eng, ens = workflow.prepare(cfg, log=_quiet)
    cvd = [c for c in ens["cases"] if c.method == "depletion"]
    assert cvd
    for c in cvd:
        assert sum(c.z) == pytest.approx(1.0) and min(c.z) >= 0.0
    produced = sorted((c for c in cvd if c.provenance["stream"] == "produced_gas"),
                      key=lambda c: -c.provenance["pressure_bara"])
    cum = [c.provenance["cumulative_gas_produced_frac"] for c in produced]
    assert cum == sorted(cum)


def test_cli_example_and_export(tmp_path, synthetic_run):
    from pvt_proxy.cli import main
    assert main(["example", "recompression", "--out", str(tmp_path / "cfg.yaml")]) == 0
    assert (tmp_path / "cfg.yaml").is_file()
    _, out, _ = synthetic_run
    assert main(["export", str(out / "proxy_definition.yaml"), "--target", "pvtsim", "--out", str(tmp_path / "x")]) == 0
    assert (tmp_path / "x" / "pvtsim_separator_test.json").is_file()
