"""PVT input/lineage/identity regressions and optional exact-runtime integrations."""

import copy
import json
import os
from pathlib import Path
import subprocess
import sys

import pytest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import pvt_data
import pvt_study
from pvt_data import PVTStudyError


@pytest.fixture
def study():
    return pvt_study.load_study(HERE / "pvt_examples/oil.yaml")


def test_units_absolute_pressure_and_temperature():
    assert pvt_data.quantity({"value": 20, "unit": "C"}, "K") == pytest.approx(293.15)
    assert pvt_data.quantity({"value": 15, "unit": "MPa"}, "bara") == 150
    with pytest.raises(PVTStudyError, match="absolute pressure"):
        pvt_data.quantity({"value": 10, "unit": "barg"}, "bara")
    with pytest.raises(PVTStudyError, match="absolute scale"):
        pvt_data.quantity({"value": -300, "unit": "C"}, "K")


def test_raw_basis_closure_and_mass_to_mole_preserved():
    analysis = {"id": "A", "basis": "mass_percent", "components": [
        {"name": "methane", "value": 50.0, "molar_mass_kg_mol": 0.016},
        {"name": "n-octane", "value": 50.0, "molar_mass_kg_mol": 0.114}]}
    prepared = pvt_data.normalize_analysis(analysis)
    assert analysis["components"][0] == {"name": "methane", "value": 50.0, "molar_mass_kg_mol": 0.016}
    assert prepared["components"][0]["mole_fraction"] == pytest.approx(114.0 / 130.0)
    assert prepared["components"][0]["raw_value"] == 50.0
    assert prepared["pre_normalization_sum"] == 100.0
    analysis["components"][0]["value"] = 49
    with pytest.raises(PVTStudyError, match="no silent normalization"):
        pvt_data.normalize_analysis(analysis)


@pytest.mark.parametrize("mutation", [
    lambda s: s["analyses"][0].pop("basis"),
    lambda s: s["fluid"].update(sample_id="UNRELATED-SAMPLE"),
    lambda s: s["samples"][0].update(accepted=False),
    lambda s: s["experiments"][0]["pressures"].update(values=[100, 200]),
    lambda s: s["fluid"].update(eos="CPA"),
    lambda s: s["fluid"].update(hidden_tuned_omegaA=0.48),
    lambda s: s["fluid"].update(binary_interactions=[{"component_i": "methane", "component_j": "ethane", "kij": 0.02, "kijT1": 1.0}]),
    lambda s: s["fluid"]["characterization"].update(unsupported_tail_clipping=True),
    lambda s: s["analyses"][0]["components"][0].update(Tc_override=200),
    lambda s: s.update(recombination=[{"basis": "molar_amount"}]),
    lambda s: s["analyses"][0]["components"][0].update(value=float("nan")),
])
def test_incomplete_or_unsupported_contracts_are_rejected(study, mutation):
    mutation(study)
    with pytest.raises(PVTStudyError):
        pvt_data.validate_study(study)


def test_binary_interaction_unknown_reversed_or_plus_mapping_is_not_ignored(study):
    for pairs in ([{"component_i": "methane", "component_j": "MISSPELLED", "kij": 0.02}],
                  [{"component_i": "methane", "component_j": "C10", "kij": 0.02}],
                  [{"component_i": "methane", "component_j": "ethane", "kij": 0.02},
                   {"component_i": "ethane", "component_j": "methane", "kij": 0.03}]):
        study["fluid"]["binary_interactions"] = pairs
        with pytest.raises(PVTStudyError):
            pvt_data.validate_study(study)


def test_recombination_has_exact_component_and_mole_closure():
    first = pvt_data.normalize_analysis({"id": "gas", "basis": "mole_fraction", "components": [
        {"name": "methane", "value": 0.9}, {"name": "n-heptane", "value": 0.1}]})
    second = pvt_data.normalize_analysis({"id": "oil", "basis": "mole_fraction", "components": [
        {"name": "methane", "value": 0.2}, {"name": "n-heptane", "value": 0.8}]})
    result, audit = pvt_data.recombine({"gas": first, "oil": second},
                                       {"id": "R", "basis": "molar_amount", "output_analysis_id": "blend",
                                        "output_sample_id": "sample", "phases": [
                                            {"analysis_id": "gas", "moles": 3},
                                            {"analysis_id": "oil", "moles": 1}]})
    assert result["components"][0]["mole_fraction"] == pytest.approx(0.725)
    assert audit["component_moles"]["methane"] == pytest.approx(2.9)
    assert audit["mole_balance_error"] == pytest.approx(0, abs=1e-14)
    assert first["components"][0]["value"] == 0.9


def _observation(study, source, role="calibration", experiment_index=0):
    experiment = study["experiments"][experiment_index]
    experiment["role"] = role
    return {"id": "O%d" % experiment_index, "experiment_id": experiment["id"], "property": "relative_volume",
            "values": [1.0] * len(experiment["pressures"]["values"]), "unit": "1", "source": source,
            "source_kind": "synthetic_reference"}


def test_holdout_same_measurement_source_cannot_be_reused_as_independent(study):
    study["observations"] = [_observation(study, "same instrument run", "calibration", 0),
                             _observation(study, "same instrument run", "validation", 1)]
    with pytest.raises(PVTStudyError, match="same measurement source"):
        pvt_data.validate_study(study)


def test_invalid_observation_array_and_phase_are_rejected(study):
    observation = _observation(study, "synthetic array")
    observation["values"] = [1.0]
    study["observations"] = [observation]
    with pytest.raises(PVTStudyError, match="array length"):
        pvt_data.validate_study(study)
    with pytest.raises(PVTStudyError, match="absent-phase"):
        pvt_study._prediction_values({"id": "viscosity", "predictions": {"values": {"oilViscosityPaS": [0.0]}}},
                                     {"property": "viscosity_pa_s", "phase": "oil", "values": [0.1]})


@pytest.mark.parametrize("predictions", [
    {"values": {"aqueousViscosityPaS": [None]}, "phaseAvailability": {"aqueous": [False]}},
    {"values": {"aqueousViscosityPaS": [0.001]}, "phaseAvailability": {"aqueous": [False]}},
    {"values": {"aqueousViscosityPaS": [None]}},
])
def test_absent_phase_observations_remain_explicit_failures(predictions):
    with pytest.raises(PVTStudyError, match="absent-phase|missing/failed"):
        pvt_study._prediction_values({"id": "VISC-1", "predictions": predictions},
                                     {"property": "viscosity_pa_s", "phase": "aqueous", "values": [0.001]})


def test_available_aqueous_viscosity_can_be_compared_to_observations():
    predictions = {"values": {"aqueousViscosityPaS": [0.001]},
                   "phaseAvailability": {"aqueous": [True]}}
    assert pvt_study._prediction_values({"id": "VISC-1", "predictions": predictions},
                                       {"property": "viscosity_pa_s", "phase": "aqueous", "values": [0.001]}) == [0.001]


@pytest.mark.parametrize("masks", [None, [], False, {}, {"oil": [True]}, {"aqueous": False}, {"aqueous": "true"},
                                  {"aqueous": [1]}, {"aqueous": []}])
def test_malformed_explicit_phase_availability_has_a_controlled_adapter_error(masks):
    predictions = {"values": {"aqueousViscosityPaS": [0.001]}, "phaseAvailability": masks}
    with pytest.raises(PVTStudyError, match="phaseAvailability"):
        pvt_study._prediction_values({"id": "VISC-1", "predictions": predictions},
                                     {"property": "viscosity_pa_s", "phase": "aqueous", "values": [0.001]})


def test_simulation_preserves_phase_availability_in_canonical_dataset(monkeypatch):
    predictions = {"values": {"aqueousViscosityPaS": [None, 0.001]},
                   "phaseAvailability": {"aqueous": [False, True]}}

    class Adapter:
        def simulate(self, fluid, kind, temperature, pressures):
            return json.dumps(predictions)

    monkeypatch.setattr(pvt_study, "_array", lambda values: values)
    experiments = [{"id": "VISC-1", "kind": "VISCOSITY", "temperature_k": 350.0,
                    "pressures_bara": [200, 100]}]
    output = pvt_study._simulate(Adapter(), object(), experiments, "FINAL")
    assert output[0]["predictions"] == predictions
    assert "predictions" not in experiments[0]


def test_unimplemented_gas_density_observable_is_rejected_before_simulation(study):
    study["observations"][0].update(property="gas_density_kg_m3", unit="kg/m3")
    with pytest.raises(PVTStudyError, match="unsupported observable"):
        pvt_data.validate_study(study)


@pytest.mark.parametrize("mutation, message", [
    (lambda c: c.update(max_iterations=0), "positive integer"),
    (lambda c: c.update(max_iterations=True), "positive integer"),
    (lambda c: c.update(max_iterations=1.5), "positive integer"),
    (lambda c: c.update(weight_by_uncertainty=True), "unsupported calibration settings"),
    (lambda c: c.update(parameters={"name": "BIP_METHANE_C7PLUS"}), "must be a list"),
    (lambda c: c["parameters"].append(copy.deepcopy(c["parameters"][0])), "duplicate fit parameter"),
    (lambda c: c["parameters"][0].update(initial=100), "bounded initial guess"),
    (lambda c: c["parameters"][0].update(prior_sigma=0.01), "unsupported calibration parameter"),
])
def test_fit_contract_is_validated_without_starting_the_engine(study, mutation, message):
    mutation(study["calibration"])
    with pytest.raises(PVTStudyError, match=message):
        pvt_data.validate_study(study)


def test_metrics_separate_roles_and_do_not_divide_near_zero_dropout():
    experiment = {"id": "E", "predictions": {"values": {"liquidRelativeVolume": [0.0, 0.5]}}}
    data = {"experiments": [experiment], "observations": [
        {"id": "O", "experiment_id": "E", "property": "liquid_dropout_percent", "unit": "% saturation volume",
         "values": [0.01, 0.4], "role": "validation", "source_kind": "synthetic_reference",
         "acceptance_max_absolute_error": 0.11}]}
    pvt_study._residuals(data)
    assert data["observations"][0]["predicted_values"] == [0.0, 0.5]
    assert data["metrics"][0]["max_absolute_error"] == pytest.approx(0.1)
    assert data["metrics"][0]["status"] == "PASS"
    assert "relative_error" not in data["metrics"][0]


def test_import_resume_checks_artifact_integrity_and_retains_previous_input(study, tmp_path, monkeypatch):
    summary = pvt_study.run_pipeline(study, tmp_path, stop_after="import")
    raw = list((tmp_path / "inputs").glob("*.raw.json"))[0]
    original = raw.read_bytes()
    monkeypatch.setattr(pvt_study, "_runtime", lambda: pytest.fail("import and exact resume must not start solver"))
    assert pvt_study.run_pipeline(study, tmp_path, resume=True, stop_after="import") == summary
    (tmp_path / "dataset/summary.json").write_text("{}", encoding="utf-8")
    pvt_study.run_pipeline(study, tmp_path, resume=True, stop_after="import")
    assert list((tmp_path / "history").glob("*/dataset/summary.json"))
    changed = copy.deepcopy(study)
    changed["study"]["title"] = "Changed input"
    pvt_study.run_pipeline(changed, tmp_path, resume=True, stop_after="import")
    assert raw.read_bytes() == original
    assert len(list((tmp_path / "inputs").glob("*.raw.json"))) == 2


def test_cli_init_does_not_create_a_model_or_report_and_will_not_overwrite(tmp_path):
    assert pvt_study.main(["--init", str(tmp_path)]) == 0
    assert (tmp_path / "study.yaml").exists()
    assert not (tmp_path / "models").exists()
    assert pvt_study.main(["--init", str(tmp_path)]) == 2
    command = [sys.executable, str(HERE / "neqsim_cli.py"), "pvt-study", "--help"]
    result = subprocess.run(command, capture_output=True, text=True)
    assert result.returncode == 0
    assert "--stage" in result.stdout


def test_quality_failure_exit_code_is_structured_not_inferred_from_message(tmp_path, monkeypatch):
    def fail(*args, **kwargs):
        raise pvt_data.PVTQualityError("fresh-runtime replay differs")
    monkeypatch.setattr(pvt_study, "run_pipeline", fail)
    assert pvt_study.main(["--demo", "oil", "--out", str(tmp_path), "--strict"]) == 1


def test_default_characterization_requires_input_tables_before_model_build(study, tmp_path, monkeypatch):
    study["observations"] = []
    study["calibration"] = {"parameters": []}
    for experiment in study["experiments"]:
        experiment["role"] = "simulation"
    monkeypatch.setattr(pvt_study, "_runtime", lambda: pytest.fail("missing data must be rejected before model construction"))
    with pytest.raises(PVTStudyError, match="starts with calibration"):
        pvt_study.run_pipeline(study, tmp_path)
    study["study"]["mode"] = "simulation_only"
    with pytest.raises(pvt_data.PVTQualityError, match="failed acceptance gates"):
        pvt_study.run_pipeline(study, tmp_path, stop_after="import", strict=True)


@pytest.mark.parametrize("case", ["oil", "condensate"])
def test_frozen_reference_data_are_inputs_separate_from_initial_candidate(case):
    raw = pvt_study.load_study(HERE / "pvt_examples" / (case + ".yaml"))
    fixture = json.loads((HERE / "pvt_examples" / (case + "_reference.json")).read_text(encoding="utf-8"))
    prepared = pvt_data.validate_study(raw)
    assert raw["observations"] == fixture["observations"]
    assert {o["role"] for o in prepared["observations"]} == {"calibration", "validation"}
    assert raw["synthetic_reference"]["reference_parameter_identity"] == pvt_data.identity(fixture["reference_parameter_inventory"])
    assert raw["synthetic_reference"]["added_noise"] == "none"
    assert not raw["fluid"].get("binary_interactions")
    assert raw["calibration"]["parameters"][0]["initial"] != raw["synthetic_reference"]["reference_grouped_methane_heavy_bip"]
    assert all(len(o["uncertainty"]) == len(o["values"]) for o in raw["observations"])


def test_fit_acceptance_is_frozen_idempotently_before_report(tmp_path):
    data = {"study": {"id": "S"}, "calibration": {"status": "CANDIDATE"}, "metrics": [
        {"role": "calibration", "status": "NOT ASSESSED"}], "findings": [], "completed_stages": []}
    pvt_study._accept_calibration(data)
    assert data["calibration"]["status"] == "NOT ACCEPTED"
    assert pvt_study._summary(data, tmp_path)["status"] == "FAIL"
    assert pvt_study._summary(data, tmp_path)["status"] == "FAIL"


@pytest.mark.skipif(os.environ.get("NEQSIM_PVT_TEST_JVM") != "1", reason="set NEQSIM_PVT_TEST_JVM=1 for exact-source JVM integration")
@pytest.mark.parametrize("case", ["oil", "condensate"])
def test_real_cli_oil_and_condensate_complete_export_replay(case, tmp_path):
    command = [sys.executable, str(HERE / "neqsim_cli.py"), "pvt-study", "--demo", case,
               "--out", str(tmp_path), "--strict"]
    result = subprocess.run(command, capture_output=True, text=True, timeout=300)
    assert result.returncode == 0, result.stderr[-5000:]
    data = json.loads((tmp_path / "dataset/pvt_study.json").read_text(encoding="utf-8"))
    assert data["completed_stages"] == list(pvt_data.STAGES)
    assert {m["stage"] for m in data["models"]} == {"base", "characterized", "tuned", "final"}
    assert data["characterization"]["mole_mass_status"] == "PASS"
    audit = data["exports"][0]["audit"]
    assert audit["passed"] and audit["fresh_process"] and audit["process_exit_code"] == 0
    assert all(c["status"] == "PASS" for c in data["consistency"])
    assert Path(data["report"]["docx"]).is_file() and Path(data["report"]["pdf"]).is_file()
    assert "NUMERICALLY VERIFIED" in data["evidence_level"]
    assert data["observations"]
    assert all(o["source_kind"] == "synthetic_reference" for o in data["observations"])
    assert data["calibration"]["status"] == "ACCEPTED BY RESIDUAL GATE"


@pytest.mark.skipif(os.environ.get("NEQSIM_PVT_TEST_JVM") != "1", reason="set NEQSIM_PVT_TEST_JVM=1 for exact-source JVM integration")
def test_bounded_cce_fit_and_separate_temperature_numerical_holdout(study, tmp_path):
    """Numerical fixture recovery is software evidence, never physical validation."""
    study["observations"] = []
    study["calibration"] = {"parameters": []}
    for experiment in study["experiments"]:
        experiment["role"] = "simulation"
    prepared = pvt_data.validate_study(study)
    ns, adapter = pvt_study._runtime()
    truth = pvt_study._build_fluid(ns, prepared)
    pvt_study._characterize(truth, adapter, prepared)
    parameter = ns.JClass("neqsim.pvtsimulation.regression.RegressionParameter")
    parameter.BIP_METHANE_C7PLUS.applyToFluid(truth, 0.04)
    calibration = study["experiments"][0]
    calibration["role"] = "calibration"
    holdout = copy.deepcopy(calibration)
    holdout["id"] = "OIL-CCE-NUMERICAL-HOLDOUT"
    holdout["role"] = "validation"
    holdout["temperature"]["value"] = 90.0
    study["experiments"].append(holdout)
    for experiment in (calibration, holdout):
        temperature = pvt_data.quantity(experiment["temperature"], "K")
        predictions = json.loads(str(adapter.simulate(truth, "CCE", temperature,
                                                       pvt_study._array(experiment["pressures"]["values"]))))
        study["observations"].append({"id": "OBS-" + experiment["id"], "experiment_id": experiment["id"],
                                      "property": "relative_volume", "unit": "1", "values": predictions["values"]["relativeVolume"],
                                      "source": "Synthetic PR grouped-BIP0.04 fixture at %sK" % temperature,
                                      "source_kind": "synthetic_reference", "acceptance_max_absolute_error": 0.0005})
    study["calibration"] = {"parameters": [{"name": "BIP_METHANE_C7PLUS", "lower": 0.0, "upper": 0.1,
                                             "initial": 0.03, "source": "Numerical fixture recovery bounds"}],
                             "max_iterations": 15}
    summary = pvt_study.run_pipeline(study, tmp_path, stop_after="validate", strict=True)
    assert summary["status"] == "PASS" and summary["experimental_validation"] == "NOT CLAIMED"
    data = json.loads((tmp_path / "dataset/pvt_study.json").read_text(encoding="utf-8"))
    assert data["calibration"]["status"] == "ACCEPTED BY RESIDUAL GATE"
    assert data["calibration"]["parameters"][0]["final"] == pytest.approx(0.04, abs=5e-5)
    assert {m["role"] for m in data["metrics"]} == {"calibration", "validation"}
    assert all(m["status"] == "PASS" for m in data["metrics"])
    assert "not asserted converged" in data["calibration"]["stopping_reason"]
