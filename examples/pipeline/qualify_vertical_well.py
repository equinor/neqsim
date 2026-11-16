"""Replay the synthetic 2 km well in #5066 against an explicit compiled NeqSim classpath.

Requires JPype1. Outputs JSON Lines; a completed run ends with type=summary.
The supplied source revision is provenance supplied by the caller, not inferred from a jar name.
"""

import argparse
import json
import math
import os

import jpype


# ConservativeThermodynamicRecovery publishes endpoints with twice its 1e-9 volume tolerance.
HOLDUP_TOLERANCE = 2.0e-9

COMPONENTS = {
    "nitrogen": 0.5, "CO2": 1.0, "methane": 45.0, "ethane": 5.0,
    "propane": 5.0, "i-butane": 2.0, "n-butane": 3.0,
    "i-pentane": 2.0, "n-pentane": 2.0, "n-hexane": 5.0,
    "n-heptane": 8.0, "n-octane": 10.0, "n-nonane": 6.0, "nC10": 5.5,
}


def emit(record):
    """Flush one finite JSON record to the caller's output file."""
    print(json.dumps(record, allow_nan=False), flush=True)


def main():
    """Construct and audit a held-inlet or delayed-rate-step conservative transient."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--cells", type=int, default=20)
    parser.add_argument("--dt", type=float, default=0.5)
    parser.add_argument("--duration", type=float, default=600.0)
    parser.add_argument("--step-at", type=float, default=None)
    parser.add_argument("--sample-every", type=int, default=20)
    args = parser.parse_args()
    if args.cells < 2 or args.sample_every < 1:
        parser.error("cells must be >= 2 and sample-every >= 1")
    if not all(math.isfinite(x) and x > 0 for x in (args.dt, args.duration)):
        parser.error("dt and duration must be positive and finite")
    if args.step_at is not None and not 0 <= args.step_at < args.duration:
        parser.error("step-at must lie in [0, duration)")
    jpype.startJVM("-Xmx512m", "-XX:ActiveProcessorCount=2",
                  classpath=args.classpath.split(os.pathsep))
    neqsim = jpype.JPackage("neqsim")
    uuid = jpype.JClass("java.util.UUID")
    phase = neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase
    fluid = neqsim.thermo.system.SystemPrEos(353.15, 200.0)
    for name, amount in COMPONENTS.items():
        fluid.addComponent(name, amount)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    fluid.setTotalFlowRate(20000.0, "kg/hr")
    feed = neqsim.process.equipment.stream.Stream("well feed", fluid)
    feed.run()
    pipe = neqsim.process.equipment.pipeline.TwoFluidPipe("well", feed)
    pipe.setLength(2000.0)
    pipe.setDiameter(0.1)
    pipe.setRoughness(1.0e-5)
    pipe.setNumberOfSections(args.cells)
    pipe.setCellFaceElevationProfile(jpype.JArray(jpype.JDouble)(
        [2000.0 * i / args.cells for i in range(args.cells + 1)]))
    pipe.setOutletPressure(102.9531305, "bara")
    pipe.setSteadyStateMaxWallClockTime(20.0)
    pipe.setEnableAdaptiveTimestepping(True)
    pipe.setThermodynamicUpdateInterval(1)
    pipe.setIncludeMassTransfer(True)
    pipe.setEnableInterfacialPressure(True)
    pipe.setImplicitInterfacialPressureCoupling(True)
    pipe.run()
    if not pipe.isSteadyStateConverged():
        raise RuntimeError("Steady initialization did not converge")
    pipe.prepareConservativeEnergyTransientFromSteadyState()
    emit({"type": "configuration", **vars(args), "components_molar_parts": COMPONENTS,
          "java": str(jpype.JClass("java.lang.System").getProperty("java.version")),
          "pressure_boundary_bara": 102.9531305})
    maxima = {"mass_kg": 0.0, "phase_mass_kg": 0.0, "volume_relative": 0.0,
              "component_relative": 0.0,
              "energy_relative": 0.0, "eos_energy_relative": 0.0}
    count = 0
    while pipe.getSimulationTime() < args.duration - 1.0e-9:
        start = pipe.getSimulationTime()
        if args.step_at is not None and start >= args.step_at - 1.0e-9:
            if feed.getFlowRate("kg/hr") > 16000.0:
                feed.setFlowRate(15000.0, "kg/hr")
                feed.run()
        interval = min(args.dt, args.duration - start)
        if args.step_at is not None and start < args.step_at - 1.0e-9:
            interval = min(interval, args.step_at - start)
        pipe.runTransient(interval, uuid.randomUUID())
        if abs(pipe.getSimulationTime() - start - interval) > 1.0e-7:
            raise RuntimeError("Requested transient interval was not fully accepted")
        mass = pipe.getLastMassBalanceReport()
        components = pipe.getLastComponentConservationReport()
        energy = pipe.getLastConservativeEnergyReport()
        residuals = {"mass_kg": abs(mass.getResidualKg(phase.TOTAL)),
                     "phase_mass_kg": max(abs(mass.getResidualKg(p))
                                          for p in (phase.GAS, phase.OIL, phase.WATER)),
                     "volume_relative": energy.getMaxRelativeVolumeResidual(),
                     "component_relative": components.getMaximumRelativeInventoryResidual(),
                     "energy_relative": abs(energy.getRelativeResidual()),
                     "eos_energy_relative": energy.getMaxRelativeThermodynamicEnergyResidual()}
        for name, value in residuals.items():
            if not math.isfinite(value):
                raise RuntimeError("Nonfinite conservation residual: " + name)
            maxima[name] = max(maxima[name], value)
        holdup = list(pipe.getLiquidHoldupProfile())
        pressure = list(pipe.getPressureProfile())
        temperature = list(pipe.getTemperatureProfile())
        if not all(math.isfinite(x) and -HOLDUP_TOLERANCE <= x <= 1 + HOLDUP_TOLERANCE for x in holdup):
            raise RuntimeError(f"Invalid liquid holdup at {pipe.getSimulationTime()} s: {holdup}")
        if not all(math.isfinite(x) and x > 0 for x in pressure + temperature):
            raise RuntimeError("Invalid absolute pressure or temperature")
        count += 1
        if count % args.sample_every == 0 or pipe.getSimulationTime() >= args.duration - 1e-9:
            emit({"type": "sample", "time_s": pipe.getSimulationTime(),
                  "inlet_kg_h": feed.getFlowRate("kg/hr"),
                  "outlet_kg_h": pipe.getOutletStream().getFlowRate("kg/hr"),
                  "phase_outlet_kg_h": {str(p): 3600 * mass.getOutletMassKg(p) / interval
                                        for p in (phase.GAS, phase.OIL, phase.WATER)},
                  "liquid_holdup": holdup, "pressure_Pa": pressure,
                  "temperature_K": temperature, "residuals": residuals})
    emit({"type": "summary", "completed_time_s": pipe.getSimulationTime(),
          "maximum_residuals": maxima,
          "qualification": "Numerical replay; not experimental holdup validation"})


if __name__ == "__main__":
    main()
