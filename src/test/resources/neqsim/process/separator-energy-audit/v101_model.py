"""Public synthetic V101 model for the separator cumulative-energy regression.

Native NeqSim process physics; no drawing export or external task dependency.
"""
from __future__ import annotations

import math
import os
from pathlib import Path
import sys


def repository_root():
    """Find this checkout's documented JVM helper, with an explicit override."""
    override = os.environ.get("NEQSIM_PROJECT_ROOT")
    candidates = [Path(override)] if override else Path(__file__).resolve().parents
    for candidate in candidates:
        if (candidate / "devtools/neqsim_dev_setup.py").is_file():
            return candidate.resolve()
    raise RuntimeError("Run inside a NeqSim checkout with devtools/neqsim_dev_setup.py")


ROOT = repository_root()
sys.path.insert(0, str(ROOT / "devtools"))
from neqsim_dev_setup import neqsim_init

NS = neqsim_init(project_root=ROOT, verbose=False)
J = NS.JClass

COMPOSITION = {
    "methane": 0.780,
    "ethane": 0.080,
    "propane": 0.050,
    "n-butane": 0.025,
    "i-butane": 0.015,
    "n-pentane": 0.025,
    "n-hexane": 0.015,
    "CO2": 0.008,
    "nitrogen": 0.002,
}


DEFAULTS = {
    "feed_mass_kg_h": 20000.0,
    "feed_temperature_C": 35.0,
    "upstream_pressure_bara": 45.0,
    "pressure_setpoint_bara": 40.0,
    "gas_sink_pressure_bara": 35.0,
    "liquid_sink_pressure_bara": 20.0,
    "diameter_m": 1.0,
    "length_m": 3.0,
    "initial_level_fraction": 0.5,
    "initial_valve_opening_pct": 50.0,
    "pressure_kp_pct_per_bar": 16.0,
    "pressure_ti_s": 20.0,
    "level_kp_pct_per_pct": 4.0,
    "level_ti_s": 60.0,
    "control_valve_travel_s": 3.0,
    "esdv_stroke_s": 1.0,
}


def parameters(overrides=None):
    """Return one explicit, independently copyable model parameter set."""
    result = dict(DEFAULTS)
    if overrides:
        unknown = set(overrides) - set(result)
        if unknown:
            raise ValueError("Unknown model parameters: " + ", ".join(sorted(unknown)))
        result.update(overrides)
    return result


def build_model(overrides=None, activate=True):
    """Build the tagged process, solve its steady state and optionally activate.

    ``activate=False`` is suitable for a scenario runner which performs its own
    steady initialization. Call ``configure_after_steady`` from its logic factory.
    """
    p = parameters(overrides)
    fluid = J("neqsim.thermo.system.SystemSrkEos")(
        p["feed_temperature_C"] + 273.15, p["upstream_pressure_bara"]
    )
    for name, fraction in COMPOSITION.items():
        fluid.addComponent(name, fraction)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    feed = J("neqsim.process.equipment.stream.Stream")("20-FEED-001", fluid)
    feed.setFlowRate(p["feed_mass_kg_h"], "kg/hr")
    inlet = J("neqsim.process.equipment.valve.ESDValve")("20-ESDV-001", feed)
    inlet.setOutletPressure(p["pressure_setpoint_bara"], "bara")
    inlet.getOutletStream().setName("20-FEED-V101")
    sep = J("neqsim.process.equipment.separator.Separator")("20-V-101", inlet.getOutletStream())
    sep.setInternalDiameter(p["diameter_m"])
    sep.setSeparatorLength(p["length_m"])
    sep.setOrientation("horizontal")
    sep.getGasOutStream().setName("20-S-02")
    sep.getLiquidOutStream().setName("20-S-07")
    gas = J("neqsim.process.equipment.valve.ThrottlingValve")("20-PCV-001", sep.getGasOutStream())
    gas.setOutletPressure(p["gas_sink_pressure_bara"], "bara")
    gas.getOutletStream().setName("20-S-02-TO-K101")
    liquid = J("neqsim.process.equipment.valve.ThrottlingValve")("20-LCV-001", sep.getLiquidOutStream())
    liquid.setOutletPressure(p["liquid_sink_pressure_bara"], "bara")
    liquid.getOutletStream().setName("20-S-09")
    coupling = J("neqsim.process.equipment.util.SetPoint")(
        "V101-INLET-PRESSURE-COUPLING", inlet, "pressure", sep.getGasOutStream()
    )
    process = J("neqsim.process.processmodel.ProcessSystem")()
    process.setName("GAS-DEMO-201 V101 dynamic design slice")
    for unit in [feed, coupling, inlet, sep, gas, liquid]:
        process.add(unit)
    process.run()
    for valve in [inlet, gas, liquid]:
        valve.autoSize(1.0, p["initial_valve_opening_pct"])
    process.run()
    if not process.solved():
        raise RuntimeError("Native steady-state model did not solve")
    result = describe_model(process, p)
    if activate:
        result = configure_after_steady(process, p)
    return result


def describe_model(process, overrides=None):
    """Resolve public model objects by stable tags, including copied processes."""
    p = parameters(overrides)
    tags = ["20-FEED-001", "20-ESDV-001", "20-V-101", "20-PCV-001", "20-LCV-001"]
    return {"process": process, "units": {tag: process.getUnit(tag) for tag in tags},
            "parameters": p, "measurements": {}, "controllers": {}}


def configure_after_steady(process, overrides=None):
    """Configure the same physical state and PI loops after a steady run.

    This method mutates only the supplied process, which must be an isolated
    scenario copy. It returns resolved objects for metrics and binding.
    """
    model = describe_model(process, overrides)
    p = model["parameters"]
    u = model["units"]
    sep = u["20-V-101"]
    for tag in ["20-ESDV-001", "20-V-101", "20-PCV-001", "20-LCV-001"]:
        u[tag].setCalculateSteadyState(False)
    sep.setInternalDiameter(p["diameter_m"])
    sep.setSeparatorLength(p["length_m"])
    sep.setLiquidLevel(p["initial_level_fraction"])
    sep.initializeTransientCalculation()
    inlet = u["20-ESDV-001"]
    inlet.setStrokeTime(p["esdv_stroke_s"])
    inlet.setFailSafePosition(0.0)
    inlet.energize()
    inlet.setPercentValveOpening(p["initial_valve_opening_pct"])
    pressure = process.getMeasurementDevice("20-PT-001")
    if pressure is None:
        pressure = J("neqsim.process.measurementdevice.PressureTransmitter")(
            "20-PT-001", sep.getGasOutStream()
        )
        process.add(pressure)
    pressure.setUnit("bara")
    pressure.setMinimumValue(0.0)
    pressure.setMaximumValue(60.0)
    level = process.getMeasurementDevice("20-LT-001")
    if level is None:
        level = J("neqsim.process.measurementdevice.LevelTransmitter")("20-LT-001", sep)
        process.add(level)
    pressure.setTransientSamplingEnabled(True)
    level.setTransientSamplingEnabled(True)
    pc = u["20-PCV-001"].getController()
    if pc is None:
        pc = J("neqsim.process.controllerdevice.ControllerDeviceBaseClass")("20-PIC-001")
        process.add(pc)
    elif pc.getEventLog().size() != 0:
        raise ValueError("Post-steady configuration requires a pristine source/copy, not a previously stepped controller")
    pc.setTransmitter(pressure)
    pc.setControllerSetPoint(p["pressure_setpoint_bara"], "bara")
    pc.setReverseActing(False)
    pc.setControllerParameters(p["pressure_kp_pct_per_bar"], p["pressure_ti_s"], 0.0)
    pc.setOutputLimits(0.0, 100.0)
    lc = u["20-LCV-001"].getController()
    if lc is None:
        lc = J("neqsim.process.controllerdevice.ControllerDeviceBaseClass")("20-LIC-001")
        process.add(lc)
    elif lc.getEventLog().size() != 0:
        raise ValueError("Post-steady configuration requires a pristine source/copy, not a previously stepped controller")
    lc.setTransmitter(level)
    lc.setControllerSetPoint(p["initial_level_fraction"])
    lc.setReverseActing(False)
    lc.setControllerParameters(p["level_kp_pct_per_pct"], p["level_ti_s"], 0.0)
    lc.setOutputLimits(0.0, 100.0)
    travel = J("neqsim.process.equipment.valve.ValveTravelModel").LINEAR_RATE_LIMIT
    for tag, control in [("20-PCV-001", pc), ("20-LCV-001", lc)]:
        valve = u[tag]
        valve.setPercentValveOpening(p["initial_valve_opening_pct"])
        valve.setController(control)
        valve.setTravelModel(travel)
        valve.setTravelTime(p["control_valve_travel_s"])
        valve.setPhysicalStepIdempotencyEnabled(True)
    process.setParallelTransientEnabled(False)
    model["measurements"] = {"20-PT-001": pressure, "20-LT-001": level}
    model["controllers"] = {"20-PIC-001": pc, "20-LIC-001": lc}
    return model


def inventory(model):
    """Extract native extensive vessel state in kg, mol and J."""
    fluid = model["units"]["20-V-101"].getThermoSystem()
    return {
        "mass_kg": float(fluid.getMass("kg")),
        "energy_J": float(fluid.getInternalEnergy()),
        "component_moles": {name: float(fluid.getComponent(name).getNumberOfmoles())
                            for name in COMPOSITION},
    }


def boundary_enthalpy_W(fluid):
    """Return an actual stream energy flux, with exact zero at zero molar flow.

    NeqSim stream fluids use their extensive enthalpy as an energy rate. A
    zero-flow fluid can retain cached intensive/enthalpy properties for later
    recovery; that cache does not constitute physical energy crossing a closed
    material boundary. Nonzero-flow nonfinite values remain errors.
    """
    molar_flow = float(fluid.getTotalNumberOfMoles())
    if not math.isfinite(molar_flow) or molar_flow < 0.0:
        raise ValueError("Boundary molar flow must be finite and nonnegative")
    if molar_flow == 0.0:
        return 0.0
    enthalpy_flux = float(fluid.getEnthalpy())
    if not math.isfinite(enthalpy_flux):
        raise ValueError("Nonzero boundary flow has a nonfinite enthalpy flux")
    return enthalpy_flux


def sample(model):
    """Extract actual state and valve positions without changing the model."""
    u = model["units"]
    sep = u["20-V-101"]
    return {
        "time_s": float(model["process"].getTime()),
        "pressure_bara": float(sep.getPressure()),
        "temperature_C": float(sep.getThermoSystem().getTemperature("C")),
        "level_fraction": float(sep.getLiquidLevel()),
        "inlet_opening_pct": float(u["20-ESDV-001"].getPercentValveOpening()),
        "pcv_opening_pct": float(u["20-PCV-001"].getPercentValveOpening()),
        "lcv_opening_pct": float(u["20-LCV-001"].getPercentValveOpening()),
        "inlet_flow_kg_s": float(u["20-ESDV-001"].getOutletStream().getFlowRate("kg/sec")),
        "gas_flow_kg_s": float(u["20-PCV-001"].getOutletStream().getFlowRate("kg/sec")),
        "liquid_flow_kg_s": float(u["20-LCV-001"].getOutletStream().getFlowRate("kg/sec")),
        "mass_kg": inventory(model)["mass_kg"],
    }
