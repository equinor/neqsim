"""Run the source bridge against compiled workspace Java and independent gas-reference values."""

from copy import deepcopy
from datetime import date, timedelta
from decimal import Decimal
import json
import os
from pathlib import Path
from time import perf_counter

import pytest

from pvt_data import identity
from reservoir_history_intake import build_component_history

ROOT = Path(__file__).resolve().parents[1]
pytestmark = pytest.mark.skipif(
    os.environ.get("NEQSIM_TEST_RESERVOIR_JAVA") != "1",
    reason="Explicit compiled-workspace JVM qualification is enabled by the reservoir CI step")


@pytest.fixture(scope="module")
def java():
    import jpype
    from neqsim_dev_setup import neqsim_init

    # This opt-in suite owns one dedicated JVM process and its shutdown.
    assert not jpype.isJVMStarted()
    ns = neqsim_init(project_root=ROOT, verbose=False)
    try:
        cls = ns.JClass("neqsim.pvtsimulation.util.CompositionalReservoirHistory")
        location = Path(str(cls.class_.getProtectionDomain().getCodeSource().getLocation().toURI().getPath())).resolve()
        assert location == (ROOT / "target/classes").resolve()
        yield ns
    finally:
        # Shutdown before pytest restores fault handlers during unconfigure.
        jpype.shutdownJVM()


def reference():
    path = ROOT / "src/test/resources/neqsim/pvtsimulation/gas-lab-tank/reference.json"
    return json.loads(path.read_text(), parse_float=Decimal)


def sample(java, interaction):
    fluid = java.JClass("neqsim.thermo.system.SystemSrkEos")(400, 200)
    fluid.addComponent("methane", 0.9)
    fluid.addComponent("ethane", 0.1)
    fluid.setMixingRule("classic")
    fluid.useVolumeCorrection(False)
    fluid.init(0)
    for phase in range(fluid.getNumberOfPhases()):
        rule = fluid.getPhase(phase).getEosMixingRule()
        rule.setBinaryInteractionParameter(0, 1, interaction)
        rule.setBinaryInteractionParameter(1, 0, interaction)
    return fluid


def model(java, values):
    cls = java.JClass("neqsim.pvtsimulation.util.CalibratedPvtModel")
    return cls(sample(java, 0), sample(java, float(values["calibration"]["identifiedKij"])),
               "independent synthetic gas sample", "original kij0", "laboratory identified kij",
               "gas-lab-tank independent Decimal SRK laboratory calibration400K",
               ["synthetic laboratory calibration400K"])


def bounded(value, unit):
    text = str(value)
    return {"unit": unit, "lower": text, "upper": text}


def source_inputs(values):
    """Represent each independent reference component meter as a named pure-component molar stream."""
    components = values["components"]
    specs = {direction + " " + name: {"direction": direction, "unit": "mol",
                                      "basis_reference": "Independent numerical component meter, mol"}
             for direction in ("production", "injection") for name in components}
    study = {"protocol": "CUMULATIVE_RESERVOIR_HISTORY", "reservoir_reference": "gas-lab-tank reference",
             "source_reference": "Independent Decimal SRK reference.json", "data_use_reference": "Repository synthetic case",
             "bounds_reference": "Exact published reference decimals, no measured uncertainty",
             "date_basis_reference": "Synthetic UTC midnight", "pressure_datum_reference": "Well-mixed tank",
             "streams": specs, "rows": []}
    scenario = {"reference": "Independent gas-reference component amounts", "component_order": ["ethane", "methane"],
                "windows": []}
    cumulative = {name: Decimal(0) for name in specs}
    study["rows"].append({"id": "initial", "date": "2024-01-01", "source_reference": "Initial counters",
                          "cumulative": {name: bounded(0, "mol") for name in specs}})
    for index, row in enumerate(values["rows"]):
        key = "interval-" + str(index)
        chosen = {"end_row": key, "streams": {}, "pressure_bara": str(row["pressureBara"]), "sigma_bara": "0.5"}
        compositions = {}
        for direction, column in (("production", "producedMoles"), ("injection", "injectedMoles")):
            for component, amount in zip(components, row[column]):
                name = direction + " " + component
                cumulative[name] += amount
                compositions[name] = {"source_reference": "Numerical component meter", "averaging": "constant",
                                      "basis_reference": specs[name]["basis_reference"],
                                      "components": {component: bounded(1, "mol/mol")}}
                chosen["streams"][name] = {"total_moles": str(amount), "mole_fractions": {component: "1"}}
        study["rows"].append({"id": key, "date": str(date(2024, 1, 2) + timedelta(days=index)),
                             "source_reference": "Independent reference interval",
                             "cumulative": {name: bounded(amount, "mol") for name, amount in cumulative.items()},
                             "interval_compositions": compositions,
                             "pressure": {"kind": "field_average", "source_reference": "Synthetic well-mixed pressure",
                                          "datum_reference": "Well-mixed tank",
                                          "role": "holdout" if index == len(values["rows"])-1 else "calibration",
                                          "value": bounded(row["pressureBara"], "bara"), "sigma": bounded("0.5", "bar")}})
        scenario["windows"].append(chosen)
    scenario["source_sha256"] = identity(study)
    return study, scenario


def test_independent_gas_reference_replay_matching_and_restart(java):
    values = reference()
    bundle = model(java, values)
    serializer = java.JClass("org.apache.commons.lang3.SerializationUtils")
    before = bytes(serializer.serialize(bundle))
    study, scenario = source_inputs(values)
    started = perf_counter()
    built = build_component_history(study, scenario, bundle, 400, 200, [1, 0, 0],
                                    "Independent gas-only initial reservoir", java)
    bridge_seconds = perf_counter() - started
    history = built["history"]
    assert list(history.getComponents()) == ["ethane", "methane"]
    assert built["calibration_provenance"] == str(bundle.getProvenance())
    expected_volume = float(values["initialVolumeM3"])
    started = perf_counter()
    direct = history.replay(expected_volume)
    replay_seconds = perf_counter() - started
    pressure = list(direct.getPressuresBara())
    component_errors = []
    for i, row in enumerate(values["rows"]):
        assert abs(pressure[i+1] - float(row["pressureBara"])) <= 1e-6
        amounts = direct.getComponentMoles()[i+1]
        assert abs(amounts[0]-float(row["retainedMoles"][1])) <= 1e-4
        assert abs(amounts[1]-float(row["retainedMoles"][0])) <= 1e-4
        component_errors.extend([abs(amounts[0]-float(row["retainedMoles"][1])),
                                 abs(amounts[1]-float(row["retainedMoles"][0]))])
    started = perf_counter()
    fit = history.fitVolume(800, 1300)
    fit_seconds = perf_counter() - started
    assert abs(fit.getResult().getInitialVolumeM3()-expected_volume) <= 0.001
    assert not fit.isAtBound()
    with pytest.raises(Exception):
        history.replay(1)
    assert list(history.replay(expected_volume).getPressuresBara()) == pressure
    altered = deepcopy(study)
    altered["rows"][-1]["pressure"]["value"] = bounded(values["rows"][-1]["pressureBara"]+Decimal(5), "bara")
    changed = deepcopy(scenario)
    changed["windows"][-1]["pressure_bara"] = altered["rows"][-1]["pressure"]["value"]["lower"]
    changed["source_sha256"] = identity(altered)
    shifted = build_component_history(altered, changed, bundle, 400, 200, [1, 0, 0], "Holdout sensitivity", java)
    newfit = shifted["history"].fitVolume(800, 1300).getResult()
    assert newfit.getInitialVolumeM3() == fit.getResult().getInitialVolumeM3()
    assert newfit.getHoldoutChiSquare() > fit.getResult().getHoldoutChiSquare()
    assert bytes(serializer.serialize(bundle)) == before
    print(json.dumps({"initial_volume_m3": fit.getResult().getInitialVolumeM3(),
                      "maximum_pressure_error_bar": max(abs(pressure[i+1]-float(row["pressureBara"]))
                                                        for i,row in enumerate(values["rows"])),
                      "maximum_component_error_mol": max(component_errors),
                      "bridge_seconds": bridge_seconds, "replay_seconds": replay_seconds,
                      "fit_seconds": fit_seconds,
                      "calibration_chi_square": fit.getResult().getCalibrationChiSquare(),
                      "holdout_chi_square": fit.getResult().getHoldoutChiSquare()}))


@pytest.mark.parametrize("temperature,pressure,fractions", [
    (0, 200, [1, 0, 0]), (400, 0, [1, 0, 0]), (400, 200, [0.5, 0, 0]),
    (400, 200, [-0.1, 0, 1.1]), (400, 200, [1, 0]),
])
def test_java_rejects_invalid_initialization(java, temperature, pressure, fractions):
    values = reference()
    study, scenario = source_inputs(values)
    with pytest.raises(Exception):
        build_component_history(study, scenario, model(java, values), temperature, pressure, fractions,
                                "Invalid reservoir initialization control", java)


def test_absent_phase_and_owned_initialization(java):
    values = reference()
    study, scenario = source_inputs(values)
    bundle = model(java, values)
    fractions = [1, 0, 0]
    built = build_component_history(study, scenario, bundle, 400, 200, fractions, "Gas initialization", java)
    fractions[:] = [0, 1, 0]
    assert list(built["history"].getInitialPhaseVolumeFractions()) == [1, 0, 0]
    absent = build_component_history(study, scenario, bundle, 400, 200, [0, 1, 0],
                                    "Absent initial oil control", java)
    with pytest.raises(Exception):
        absent["history"].replay(float(values["initialVolumeM3"]))
