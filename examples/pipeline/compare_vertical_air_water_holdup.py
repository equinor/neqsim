"""Screen the 50.8 mm Skopich air-water measurements in BOEM Figure 66(b)."""
import argparse
import json
import math
import os

import jpype


# Approximate plot readings, not instrument tolerances or exact tabular data.
# BOEM M15PC00007, Appendix IV, Fig. 66(b). Gas speed: (liquid holdup, pressure gradient Pa/m).
FIGURE_66 = {10: (.14, 1480.), 20: (.065, 800.), 25: (.055, 820.)}


def compare(n, jg, cells, length):
    """Match the flashed phase volume rates at the outlet reference pressure."""
    pressure, temperature = 1.01325, 293.15
    diameter, jl = .0508, .05
    # The two lengths are local numerical test sections, not the physical length of the laboratory column.
    area = math.pi * diameter**2 / 4
    air = jg * area * pressure * 1e5 / (8.314462618 * temperature)
    water = jl * area * 998.2 / .01801528
    for _ in range(12):
        fluid = n.thermo.system.SystemSrkCPAstatoil(temperature, pressure)
        fluid.addComponent("nitrogen", .79 * air)
        fluid.addComponent("oxygen", .21 * air)
        fluid.addComponent("water", water)
        fluid.setMixingRule(10)
        fluid.setMultiPhaseCheck(True)
        stream = n.process.equipment.stream.Stream("Air and water", fluid)
        stream.run()
        fluid = stream.getFluid()
        fluid.initProperties()
        gas = fluid.getPhase("gas").getFlowRate("m3/sec") / area
        liquid = fluid.getPhase("aqueous").getFlowRate("m3/sec") / area
        if max(abs(gas / jg - 1), abs(liquid / jl - 1)) < 1e-6:
            break
        air *= jg / gas
        water *= jl / liquid
    else:
        raise ValueError("Feed did not attain the prescribed local superficial velocities")
    pipe = n.process.equipment.pipeline.TwoFluidPipe("Vertical test section", stream)
    pipe.setLength(length)
    pipe.setDiameter(diameter)
    pipe.setRoughness(1e-5)
    pipe.setNumberOfSections(cells)
    pipe.setCellFaceElevationProfile([length * i / cells for i in range(cells + 1)])
    pipe.setOutletPressure(pressure, "bara")
    pipe.setSteadyStateMaxWallClockTime(60.)
    pipe.run()
    steady = pipe.getSteadyStateConvergenceReport()
    result = {"jg_m_s": gas, "jl_m_s": liquid, "cells": cells, "length_m": length, "reason": str(steady.getTerminationReason()),
              "converged": bool(pipe.isSteadyStateConverged())}
    if result["converged"]:
        h = list(pipe.getLiquidHoldupProfile())
        pressures = list(pipe.getPressureProfile())
        sections = list(pipe.getSectionSnapshots())
        gas_speeds = [(1 - section.getLiquidHoldup()) * section.getGasVelocity() for section in sections]
        liquid_speeds = [section.getWaterHoldup() * section.getWaterVelocity()
                         + section.getOilHoldup() * section.getOilVelocity() for section in sections]
        gravity = sum(9.81 * ((1 - s.getLiquidHoldup()) * s.getGasDensity()
                               + s.getLiquidHoldup() * s.getLiquidDensity()) for s in sections) / cells
        regimes = sorted({str(s.getFlowRegime()) for s in sections})
        result.update(gravity_from_phase_inventory_pa_m=gravity, regimes=regimes,
                      local_jg_range_m_s=[min(gas_speeds), max(gas_speeds)],
                      local_jl_range_m_s=[min(liquid_speeds), max(liquid_speeds)],
                      cell_gradient_pa_m=(pressures[0] - pressures[-1]) / (length * (cells - 1) / cells),
                      holdup=sum(h) / len(h), inlet_bara=pipe.getInletFacePressure("bara"),
                      outlet_bara=pipe.getOutletPressure("bara"),
                      gradient_pa_m=(pipe.getInletFacePressure("bara") - pipe.getOutletPressure("bara")) * 1e5 / length)
        observed_h, observed_dp = FIGURE_66[jg]
        result["figure_holdup_approx"] = observed_h
        result["figure_gradient_pa_m_approx"] = observed_dp
        result["holdup_difference_approx"] = result["holdup"] - observed_h
        result["pressure_gradient_difference_pa_m_approx"] = result["cell_gradient_pa_m"] - observed_dp
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--revision", required=True, help="Revision of the compiled NeqSim classes")
    args = parser.parse_args()
    jpype.startJVM("-XX:-UsePerfData", "-Xmx400m", "-XX:ActiveProcessorCount=2", convertStrings=True,
                   classpath=os.environ["NEQSIM_CLASSPATH"].split(os.pathsep))
    n = jpype.JPackage("neqsim")
    cases = [compare(n, rate, cells, length)
             for length in (.2, 2.) for cells in (10, 20) for rate in (10, 20, 25)]
    print(json.dumps({"basis": "Skopich et al. (2015), BOEM M15PC00007 Figure 66(b), local-section screen",
                      "revision": args.revision, "reference_T_K": 293.15, "reference_p_bara": 1.01325, "bore_m": .0508,
                      "roughness_m": 1e-5, "engineering_qualification": "HOLD", "cases": cases},
                     indent=2, allow_nan=False))
