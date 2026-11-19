"""Compare the native vessel blowdown solver against the published S12 pressure/temperature history."""

import argparse
import json
import math
import os

import jpype


def calculate(n, dt):
    fluid = n.thermo.system.SystemPrEos(293.15, 118.5)
    for name, fraction in (("methane", .665), ("ethane", .035), ("propane", .30)):
        fluid.addComponent(name, fraction)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    diameter, effective_length = 1.130, 2.49
    volume = math.pi * diameter**2 * effective_length / 4
    area = math.pi * diameter * effective_length + math.pi * diameter**2 / 2
    simulator = n.process.safety.depressurization.DepressurizationSimulator
    model = simulator(fluid, volume, .010, .84, 101325.)
    model.setWithdrawalMode(simulator.WithdrawalMode.VAPOUR)
    model.setWall(area * .059 * 7850., area, 470., 50.)
    model.setTimeStep(dt).setMaxTime(1500.).setStopPressure(101325.)
    r = model.run()
    rows = [{"s": t, "bara": p, "bulk_K": temp, "wall_K": wall}
            for t, p, temp, wall in zip(r.time, r.pressureBara, r.temperatureK, r.wallTempK)]
    minimum = min(rows, key=lambda row: row["bulk_K"])
    return {"dt_s": dt, "volume_m3": volume, "final": rows[-1], "minimum_bulk": minimum,
            "vu_fallbacks": r.vuFlashFallbackCount, "vapour_withdrawal_used": r.vapourWithdrawalUsed,
            "pressure_nonincreasing": r.pressureMonotonicNonIncreasing, "mass_nonincreasing": r.massMonotonicNonIncreasing,
            "rows": rows}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dt", type=float, choices=[1., 2.], required=True)
    args = parser.parse_args()
    jpype.startJVM("-XX:-UsePerfData", "-Xmx400m", "-XX:ActiveProcessorCount=2", convertStrings=True,
                   classpath=os.environ["NEQSIM_CLASSPATH"].split(os.pathsep))
    print(json.dumps(calculate(jpype.JPackage("neqsim"), args.dt), allow_nan=False))
