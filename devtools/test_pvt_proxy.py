"""Hermetic tests of the PVT forward-proxy generator (no JVM; analytic stand-in for the process).

Spec acceptance tests covered here: 1 (recovery, analytic model), 4 (invalid pressure sequence),
5 (failed case kept and penalized), 6 (reproducibility), 7 (extrapolation warning), 8 (dry-gas
dominance), 9 (oil priority over GOR). NeqSim-backed versions are in test_pvt_proxy_integration.py.
"""
import copy
import json
import math
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from pvt_proxy import objective as obj  # noqa: E402
from pvt_proxy import validate as val  # noqa: E402
from pvt_proxy.calibrate import Calibrator  # noqa: E402
from pvt_proxy.config import ConfigError, ObjectiveConfig, parse_config  # noqa: E402
from pvt_proxy.engine import failed  # noqa: E402
from pvt_proxy.export import EXPORTERS, flow_sequence, load_definition, run_exports, write_definition  # noqa: E402
from pvt_proxy.fluids import (FluidCase, FluidTemplate, classify, generate_ensemble, latin_hypercube,  # noqa: E402
                              map_composition, mix, vary)
from pvt_proxy.optimize import INVALID_BASE_PENALTY, ParameterSpace  # noqa: E402

COMPONENTS = ["methane", "ethane", "propane", "n-hexane", {"name": "C10", "mw_kg_mol": 0.142, "density_g_cm3": 0.79}]


def base_config(**over):
    """Small valid configuration (not run against NeqSim in this file)."""
    d = {
        "name": "unit",
        "feed": {"pressure_bara": 120.0, "temperature_C": 75.0, "rate_kmol_h": 100.0},
        "fluids": {"template": {"eos": "srk", "components": COMPONENTS},
                   "base_fluids": {"oil": {"methane": 0.4, "ethane": 0.1, "propane": 0.1, "n-hexane": 0.1, "C10": 0.3},
                                   "gas": {"methane": 0.85, "ethane": 0.08, "propane": 0.04, "n-hexane": 0.02, "C10": 0.01}},
                   "methods": [{"type": "explicit"}]},
        "reference": {"type": "forward", "id": "ref", "stages": [
            {"name": "hp", "pressure_bara": 60.0, "temperature_C": 55.0},
            {"name": "lp", "pressure_bara": 2.5, "temperature_C": 35.0}]},
        "proxy": {"stages": [
            {"name": "hp", "pressure_bara": {"value": 80.0, "lower": 30.0, "upper": 100.0}, "temperature_C": 55.0},
            {"name": "lp", "pressure_bara": 2.5, "temperature_C": {"value": 45.0, "lower": 20.0, "upper": 60.0}}]},
        "objective": {"loss": "squared"},
        "optimizer": {"method": "lsq", "local_maxiter": 200},
    }
    for k, v in over.items():
        d[k] = v
    return parse_config(d)


# ---------------------------------------------------------------------------------------------
# Analytic stand-in for the NeqSim proxy: identifiable in (hp pressure, lp temperature)
# ---------------------------------------------------------------------------------------------
class FakeEvaluator:
    """oil = a (1 - 0.004 (T - 30) - 0.0015 b (P - 50)), gas = g (1 + 0.003 (T - 30) + 0.001 (P - 50))."""

    def __init__(self, cases, fail_ids=()):
        self.cases = list(cases)
        self.fail_ids = set(fail_ids)
        self.calls = 0

    def record(self, case, params):
        a, g, b = case.provenance["a"], case.provenance["g"], case.provenance["b"]
        p, t = params["hp"][0], params["lp"][1]
        oil = a * (1.0 - 0.004 * (t - 30.0) - 0.0015 * b * (p - 50.0))
        gas = g * (1.0 + 0.003 * (t - 30.0) + 0.001 * (p - 50.0))
        return {"status": "ok", "reason": "", "oil_Sm3_h": oil, "gas_Sm3_h": gas, "gor_Sm3_Sm3": gas / oil,
                "oil_kg_h": 0.0, "gas_kg_h": 0.0, "oil_density_kg_m3": 800.0, "mass_balance_rel_err": 0.0}

    def evaluate(self, params):
        self.calls += 1
        return {c.id: (failed("injected flash failure") if c.id in self.fail_ids else self.record(c, params))
                for c in self.cases}


def fake_ensemble(n=8):
    cases = []
    for i in range(n):
        cases.append(FluidCase("f%02d" % i, [0.2] * 5, "synthetic",
                               {"a": 2.0 + i, "g": 1000.0 + 300.0 * i, "b": 0.5 + 0.2 * i}))
    return {"id": "fake", "template": FluidTemplate(components=[]), "cases": cases, "rejected": []}


def fake_refs(ens, true=(60.0, 35.0)):
    ev = FakeEvaluator(ens["cases"])
    params = {"hp": (true[0], 55.0), "lp": (2.5, true[1])}
    return ev.evaluate(params)


# ---------------------------------------------------------------------------------------------
# configuration
# ---------------------------------------------------------------------------------------------
def test_config_rejects_unknown_keys_and_backward_scrubber_routing():
    with pytest.raises(ConfigError, match="unknown key"):
        base_config(objectiv={})
    proxy = {"stages": [{"name": "hp", "pressure_bara": 60.0, "temperature_C": 50.0},
                        {"name": "lp", "pressure_bara": 3.0, "temperature_C": 40.0}],
             "scrubber": {"pressure_bara": 20.0, "temperature_C": 30.0, "gas_sources": ["lp"], "liquid_destination": "hp"}}
    with pytest.raises(ConfigError, match="recycle"):
        base_config(proxy=proxy)
    proxy["scrubber"]["liquid_destination"] = "stock_tank"
    assert base_config(proxy=proxy).proxy.scrubber.liquid_destination == "stock_tank"


def test_config_free_variable_needs_bounds_and_forward_reference_has_no_recycle():
    proxy = {"stages": [{"name": "hp", "pressure_bara": {"value": 60.0}, "temperature_C": 50.0}]}
    with pytest.raises(ConfigError, match="bounds"):
        base_config(proxy=proxy)
    ref = {"type": "forward", "stages": [{"name": "a", "pressure_bara": 50.0, "temperature_C": 40.0},
                                         {"name": "b", "pressure_bara": 5.0, "temperature_C": 30.0}],
           "recompression": [{"name": "k", "gas_sources": ["b"], "discharge_pressure_bara": 50.0,
                              "cooler_temperature_C": 30.0, "liquid_return_stage": "b"}]}
    with pytest.raises(ConfigError, match="recycle"):
        base_config(reference=ref)
    ref["stages"][1]["pressure_bara"] = 80.0
    ref["recompression"] = []
    with pytest.raises(ConfigError, match="decrease"):
        base_config(reference=ref)


def test_standard_conditions_ideal_gas_volume():
    cfg = base_config()
    assert cfg.standard_conditions.sm3_per_kmol == pytest.approx(23.6448, rel=1e-4)


def test_recompression_scrubber_without_compressor_and_gas_return_validation():
    ref = {"type": "recompression", "stages": [{"name": "sep", "pressure_bara": 12.5, "temperature_C": 57.0}],
           "recompression": [
               {"name": "scr1", "gas_sources": ["sep"], "scrubber_pressure_bara": 12.26, "cooler_temperature_C": 18.0,
                "liquid_return_stage": "sep"},
               {"name": "scr2", "gas_sources": ["scr1"], "discharge_pressure_bara": 38.0, "cooler_temperature_C": 19.0,
                "liquid_return_stage": "sep", "gas_return": {"to_stage": "sep", "rate_Sm3_h": 1000.0}}]}
    r = base_config(reference=ref).reference.recompression
    assert r[0].scrubber_pressure_bara == pytest.approx(12.26)
    assert r[1].gas_return_stage == "sep" and r[1].gas_return_Sm3_h == 1000.0
    bad = copy.deepcopy(ref)
    bad["recompression"][1]["gas_return"] = {"to_stage": "nowhere", "rate_Sm3_h": 1000.0}
    with pytest.raises(ConfigError, match="gas_return"):
        base_config(reference=bad)
    bad = copy.deepcopy(ref)
    bad["recompression"][0]["discharge_pressure_bara"] = 12.26
    with pytest.raises(ConfigError, match="not both"):
        base_config(reference=bad)


def test_factory_template_and_list_compositions():
    t = FluidTemplate.from_dict({"factory": "my_module:make_fluid"})
    assert t.factory == "my_module:make_fluid" and t.components == []
    with pytest.raises(ConfigError, match="exactly one"):
        FluidTemplate.from_dict({"factory": "m:f", "components": ["methane"]})
    with pytest.raises(ConfigError, match="module:function"):
        FluidTemplate.from_dict({"factory": "nofunction"})
    assert map_composition([0.5, 0.5, 0.0], ["a", "b", "c"], "x") == [0.5, 0.5, 0.0]
    with pytest.raises(ConfigError, match="3-component"):
        map_composition([0.5, 0.5], ["a", "b", "c"], "x")


# ---------------------------------------------------------------------------------------------
# fluids
# ---------------------------------------------------------------------------------------------
def test_fluid_composition_validation_and_mapping():
    names = ["methane", "ethane", "C10"]
    assert map_composition({"methane": 0.5, "C10": 0.5}, names, "x") == [0.5, 0.0, 0.5]
    with pytest.raises(ConfigError, match="not in the template"):
        map_composition({"methane": 0.5, "argon": 0.5}, names, "x")
    with pytest.raises(ConfigError, match="sum to"):
        map_composition({"methane": 0.5, "C10": 0.2}, names, "x")
    with pytest.raises(ConfigError, match="water"):
        FluidTemplate.from_dict({"components": ["methane", "water"]})


def test_mixing_and_variation_are_valid_and_reproducible():
    a, b = [0.2, 0.3, 0.5], [0.9, 0.1, 0.0]
    m = mix(a, b, 0.25)
    assert sum(m) == pytest.approx(1.0) and m[0] == pytest.approx(0.375)
    v1 = vary(a, ["x", "y", "z"], 10, 0.2, seed=5)
    v2 = vary(a, ["x", "y", "z"], 10, 0.2, seed=5)
    assert v1 == v2
    for z in v1:
        assert sum(z) == pytest.approx(1.0) and min(z) >= 0
    assert latin_hypercube(4, 2, 1) == latin_hypercube(4, 2, 1)


def test_generate_ensemble_methods_weights_and_ids():
    cfg = base_config()
    spec = copy.deepcopy(cfg.fluids)
    spec["methods"] = [{"type": "explicit"}, {"type": "mixing", "endmembers": ["oil", "gas"], "n": 3},
                       {"type": "variation", "base": "oil", "n": 4, "seed": 2}]
    spec["weights"] = {"gas": 0.5}
    template = FluidTemplate.from_dict(spec["template"])
    ens = generate_ensemble(spec, template)
    assert len(ens["cases"]) == 2 + 3 + 4
    assert {c.method for c in ens["cases"]} == {"explicit", "mixing", "variation"}
    assert [c for c in ens["cases"] if c.id == "gas"][0].weight == 0.5
    spec["methods"].append({"type": "depletion", "base": "gas", "reservoir_temperature_C": 90.0,
                            "pressures_bara": [200.0, 100.0]})
    with pytest.raises(ConfigError, match="engine"):
        generate_ensemble(spec, template)


def test_classify_by_reference_gor():
    assert classify(100.0) == "black_oil"
    assert classify(400.0) == "volatile_oil"
    assert classify(3000.0) == "gas_condensate"
    assert classify(50000.0) == "dry_gas"
    assert classify(math.inf) == "dry_gas"
    assert classify(None) == "unclassified"


# ---------------------------------------------------------------------------------------------
# objective: tests 8 and 9
# ---------------------------------------------------------------------------------------------
def _rec(oil, gas):
    return {"status": "ok", "oil_Sm3_h": oil, "gas_Sm3_h": gas, "gor_Sm3_Sm3": gas / oil if oil > 0 else math.inf}


def test_oil_priority_default_objective_prefers_oil_match_over_gor_match():
    """Test 9: candidate A matches GOR exactly with 10 % biased oil; B matches oil, GOR off by 5 %."""
    refs = {"f%d" % i: _rec(10.0 + i, 2000.0 + 100 * i) for i in range(5)}
    cand_a = {k: _rec(0.9 * r["oil_Sm3_h"], 0.9 * r["gas_Sm3_h"]) for k, r in refs.items()}
    cand_b = {k: _rec(r["oil_Sm3_h"], 1.05 * r["gas_Sm3_h"]) for k, r in refs.items()}
    weights = {k: 1.0 for k in refs}
    default = ObjectiveConfig()
    norm = obj.normalization(refs, default)
    assert obj.evaluate(refs, cand_b, weights, default, norm).total < obj.evaluate(refs, cand_a, weights, default, norm).total
    gor_only = ObjectiveConfig(oil_weight=0.0, gas_weight=0.0, gor_weight=1.0)
    norm_g = obj.normalization(refs, gor_only)
    assert obj.evaluate(refs, cand_a, weights, gor_only, norm_g).total < obj.evaluate(refs, cand_b, weights, gor_only, norm_g).total


def test_super_dry_gas_does_not_dominate_default_objective():
    """Test 8: a super-dry fluid (GOR ~ 1e6) must not outweigh oil accuracy at moderate GOR."""
    refs = {"m%d" % i: _rec(10.0 + i, 20000.0 + 1000 * i) for i in range(6)}  # GOR ~ 2000
    refs["dry"] = _rec(0.002, 2000.0)  # GOR 1e6
    weights_in = [FluidCase(k, [], "x") for k in refs]
    # A: moderate oils within 1 %, super-dry oil off by 80 %.  B: super-dry exact, moderate oils off by 6 %.
    cand_a = {k: (_rec(1.01 * r["oil_Sm3_h"], r["gas_Sm3_h"]) if k != "dry" else _rec(0.0036, 2000.0)) for k, r in refs.items()}
    cand_b = {k: (_rec(1.06 * r["oil_Sm3_h"], r["gas_Sm3_h"]) if k != "dry" else _rec(0.002, 2000.0)) for k, r in refs.items()}
    naive = ObjectiveConfig(oil_weight=1.0, eps_oil_fraction=0.0, dry_gas_weight=1.0, loss="squared", gor_weight=1.0)
    w_naive = obj.effective_weights(weights_in, refs, naive)
    n_naive = obj.normalization(refs, naive)
    assert obj.evaluate(refs, cand_b, w_naive, naive, n_naive).total < obj.evaluate(refs, cand_a, w_naive, naive, n_naive).total
    default = ObjectiveConfig()
    w = obj.effective_weights(weights_in, refs, default)
    assert w["dry"] == pytest.approx(default.dry_gas_weight)
    norm = obj.normalization(refs, default)
    bd_a = obj.evaluate(refs, cand_a, w, default, norm)
    assert bd_a.total < obj.evaluate(refs, cand_b, w, default, norm).total
    dom = obj.dominance(bd_a, refs, default)
    assert dom["dry_gas_fluids"] == ["dry"]
    assert not dom["dominated"]


def test_dominance_flagged_when_one_fluid_carries_the_objective_and_ignored_when_negligible():
    refs = {"f%d" % i: _rec(10.0, 1000.0) for i in range(5)}
    cfg = ObjectiveConfig(loss="squared")
    norm = obj.normalization(refs, cfg)
    w = {k: 1.0 for k in refs}
    prox = dict(refs)
    prox["f0"] = _rec(13.0, 1000.0)
    assert obj.dominance(obj.evaluate(refs, prox, w, cfg, norm), refs, cfg)["dominated"]
    prox["f0"] = _rec(10.0 * (1 + 1e-6), 1000.0)
    assert not obj.dominance(obj.evaluate(refs, prox, w, cfg, norm), refs, cfg)["dominated"]


def test_robust_losses():
    c = ObjectiveConfig(loss="soft_l1", residual_scale=0.2)
    assert obj.loss(0.01, c) == pytest.approx(0.01 ** 2, rel=1e-3)
    assert obj.loss(10.0, c) < 10.0 ** 2 / 10
    assert obj.loss(10.0, ObjectiveConfig(loss="cap", residual_scale=0.2)) == pytest.approx(0.04)


@pytest.mark.parametrize("cfg", [ObjectiveConfig(), ObjectiveConfig(gor_weight=0.5, loss="cap"),
                                 ObjectiveConfig(loss="squared", failed_penalty=3.0)])
def test_objective_components_square_sum_equals_objective(cfg):
    refs = {"a": _rec(10.0, 1000.0), "b": _rec(2.0, 2000.0), "c": _rec(0.01, 2500.0)}
    prox = {"a": _rec(10.5, 990.0), "b": failed("x"), "c": _rec(0.0, 2600.0)}
    w = obj.effective_weights([FluidCase(k, [], "x") for k in refs], refs, cfg)
    norm = obj.normalization(refs, cfg)
    comps = obj.objective_components(refs, prox, w, cfg, norm)
    assert sum(v * v for v in comps) == pytest.approx(obj.evaluate(refs, prox, w, cfg, norm).total)
    assert len(comps) == 3 * (3 if cfg.gor_weight > 0 else 2)


# ---------------------------------------------------------------------------------------------
# constraints, calibration logic: tests 1, 4, 5, 6
# ---------------------------------------------------------------------------------------------
def test_invalid_pressure_sequence_is_rejected_without_simulation():
    """Test 4."""
    proxy = {"min_pressure_step_bar": 1.0, "stages": [
        {"name": "hp", "pressure_bara": {"value": 30.0, "lower": 2.0, "upper": 100.0}, "temperature_C": 55.0},
        {"name": "lp", "pressure_bara": {"value": 10.0, "lower": 2.0, "upper": 40.0}, "temperature_C": 35.0}]}
    cfg = base_config(proxy=proxy)
    space = ParameterSpace(cfg.proxy, cfg.standard_conditions)
    assert space.pressure_violation(space.decode([30.0, 10.0])) == 0.0
    bad = space.decode([10.0, 20.0])
    assert space.pressure_violation(bad) == pytest.approx(11.0)
    with pytest.raises(ConfigError, match="pressure sequence"):
        space.validate(bad)
    ens = fake_ensemble(3)
    for c, nm in zip(ens["cases"], ("a", "b", "c")):
        c.provenance = {"a": 1.0, "g": 100.0, "b": 1.0}
    refs = {c.id: _rec(1.0, 100.0) for c in ens["cases"]}

    class Boom:
        cases = ens["cases"]

        def evaluate(self, params):
            raise AssertionError("an invalid candidate must not be simulated")

    cal = Calibrator(cfg, None, ens, refs, evaluator=Boom(), log=lambda *a: None)
    f, _ = cal.evaluate_phys([10.0, 20.0])
    assert f >= INVALID_BASE_PENALTY
    assert cal.n_rejected == 1


def test_recovery_of_known_parameters_on_analytic_model():
    """Test 1 on the analytic stand-in: the fit returns the known (P, T)."""
    cfg = base_config()
    ens = fake_ensemble()
    refs = fake_refs(ens)
    cal = Calibrator(cfg, None, ens, refs, evaluator=FakeEvaluator(ens["cases"]), log=lambda *a: None)
    res = cal.run()
    assert res.free_values["hp.pressure"] == pytest.approx(60.0, abs=0.05)
    assert res.free_values["lp.temperature"] == pytest.approx(35.0, abs=0.05)
    assert res.identifiability["available"] and not res.identifiability["poorly_determined"]
    assert len(res.params) == 2  # one common set for all fluids


def test_failed_case_is_kept_reported_and_penalized():
    """Test 5: the failed fluid stays in the result with its reason and adds the configured penalty."""
    cfg = base_config(objective={"loss": "squared", "failed_penalty": 7.0})
    ens = fake_ensemble(5)
    refs = fake_refs(ens)
    ev = FakeEvaluator(ens["cases"], fail_ids={"f02"})
    res = Calibrator(cfg, None, ens, refs, evaluator=ev, log=lambda *a: None).run()
    assert res.proxies["f02"]["status"] == "failed"
    assert "injected" in res.proxies["f02"]["reason"]
    assert res.breakdown.n_failed == 1
    assert res.breakdown.contributions["f02"] == pytest.approx(7.0)
    rows = val.per_fluid_rows(ens["cases"], refs, res.proxies, res.weights, res.breakdown, {})
    bad = [r for r in rows if r["fluid_id"] == "f02"][0]
    assert bad["status"] == "proxy_failed" and "injected" in bad["failure_reason"]
    m = val.aggregate(rows)
    assert m["n_proxy_failed"] == 1 and m["n_fluids"] == 5
    assert val.status(m, res.dominance, cfg.acceptance)["status"] == "review_required"


def test_reproducible_with_same_seed():
    """Test 6."""
    cfg = base_config(optimizer={"method": "de+lsq", "seed": 3, "global_maxiter": 5, "popsize": 5,
                                 "local_maxiter": 100})
    ens = fake_ensemble()
    refs = fake_refs(ens, true=(70.0, 40.0))
    r1 = Calibrator(cfg, None, ens, refs, evaluator=FakeEvaluator(ens["cases"]), log=lambda *a: None).run()
    r2 = Calibrator(cfg, None, ens, refs, evaluator=FakeEvaluator(ens["cases"]), log=lambda *a: None).run()
    assert r1.free_values == r2.free_values
    assert r1.objective == r2.objective


def test_checkpoint_written_and_resumed(tmp_path):
    cfg = base_config(optimizer={"method": "lsq", "local_maxiter": 50, "checkpoint_every": 5})
    ens = fake_ensemble()
    refs = fake_refs(ens)
    Calibrator(cfg, None, ens, refs, out_dir=tmp_path, evaluator=FakeEvaluator(ens["cases"]), log=lambda *a: None).run()
    cp = json.loads((tmp_path / "checkpoint.json").read_text())
    assert cp["config_hash"] == cfg.config_hash() and cp["best_free_values"]["hp.pressure"] == pytest.approx(60.0, abs=0.1)
    msgs = []
    res = Calibrator(cfg, None, ens, refs, out_dir=tmp_path, evaluator=FakeEvaluator(ens["cases"]),
                     log=msgs.append).run(resume=True)
    assert any("resuming" in m for m in msgs)
    assert res.free_values["hp.pressure"] == pytest.approx(60.0, abs=0.1)


def test_prior_weight_keeps_flat_direction_at_initial_guess():
    """With a parameter the data do not see, the prior keeps it at the initial value."""
    proxy = {"stages": [
        {"name": "hp", "pressure_bara": {"value": 80.0, "lower": 30.0, "upper": 100.0},
         "temperature_C": {"value": 50.0, "lower": 20.0, "upper": 80.0}},
        {"name": "lp", "pressure_bara": 2.5, "temperature_C": {"value": 45.0, "lower": 20.0, "upper": 60.0}}]}
    cfg = base_config(proxy=proxy, objective={"loss": "squared", "prior_weight": 1e-6})
    ens = fake_ensemble()
    refs = fake_refs(ens)
    res = Calibrator(cfg, None, ens, refs, evaluator=FakeEvaluator(ens["cases"]), log=lambda *a: None).run()
    assert res.free_values["hp.temperature"] == pytest.approx(50.0, abs=0.5)
    assert "hp.temperature" in res.identifiability["poorly_determined"]
    assert res.free_values["hp.pressure"] == pytest.approx(60.0, abs=0.2)


# ---------------------------------------------------------------------------------------------
# validation and exports: test 7
# ---------------------------------------------------------------------------------------------
def test_extrapolation_warning_outside_calibration_range():
    """Test 7."""
    rng = {"c7plus_mole_fraction": {"min": 0.05, "max": 0.30}, "ref_gor_Sm3_Sm3": {"min": 100.0, "max": 3000.0}}
    assert val.extrapolation_warnings({"c7plus_mole_fraction": 0.2, "ref_gor_Sm3_Sm3": 500.0}, rng) == []
    w = val.extrapolation_warnings({"c7plus_mole_fraction": 0.45, "ref_gor_Sm3_Sm3": 500.0}, rng)
    assert len(w) == 1 and "extrapolating" in w[0]


def test_flow_sequence_and_exports(tmp_path):
    stages = [{"name": "hp", "pressure_bara": 60.0, "temperature_C": 50.0},
              {"name": "mp", "pressure_bara": 15.0, "temperature_C": 45.0},
              {"name": "lp", "pressure_bara": 3.0, "temperature_C": 40.0}]
    scr = {"name": "scrubber", "pressure_bara": 30.0, "temperature_C": 25.0, "gas_sources": ["mp", "lp"],
           "liquid_destination": "stock_tank"}
    std = {"temperature_K": 288.15, "pressure_bara": 1.01325, "sm3_per_kmol": 23.6448}
    seq = flow_sequence(stages, scr, std)
    by = {u["name"]: u for u in seq}
    assert [u["name"] for u in seq] == ["hp", "mp", "lp", "scrubber", "stock_tank"]
    assert by["mp"]["vapour_destination"] == by["scrubber"]["number"]
    assert by["hp"]["vapour_destination"] == 0
    assert by["lp"]["liquid_destination"] == by["stock_tank"]["number"]
    assert by["scrubber"]["liquid_destination"] == by["stock_tank"]["number"]
    for u in seq:  # strictly forward
        if u["liquid_destination"]:
            assert u["liquid_destination"] > u["number"]
        if u["vapour_destination"]:
            assert u["vapour_destination"] > u["number"]
    defn = {"proxy_process": {"name": "p", "purpose": "x", "standard_conditions": std, "stages": stages, "scrubber": scr,
                              "sequence": seq, "calibration": {"fitted_at": "now", "status": {"status": "pass"},
                                                               "x": math.nan}}}
    write_definition(defn, tmp_path)
    back = load_definition(tmp_path / "proxy_definition.yaml")
    assert back["proxy_process"]["calibration"]["x"] is None
    files = run_exports(back, sorted(EXPORTERS), tmp_path / "exports")
    assert (tmp_path / "exports" / "e300_sepcond.inc").read_text().count("PROXY") == 5
    assert all(p.exists() for p in files)
    with pytest.raises(ValueError):
        run_exports(back, ["unknown"], tmp_path)
