"""Compare the settled finite header with the native steady dry-gas network at constant choked load."""

import json
import math
import os

import jpype

from v101_disposal import DisposalVolume, STATIC_BARA, TIP_RATED_KG_S, TIP_RATED_RISE_BAR


def compare(n, sources):
    temperature, molar_mass, gamma = 300., .01604, 1.3
    network_type = n.process.safety.depressurization.SteadyFlareGasNetwork
    no_loss = network_type.Pipe(.50, 0., .02)
    network = network_type("H-001", no_loss, "TIP-001", STATIC_BARA, STATIC_BARA + TIP_RATED_RISE_BAR,
                           TIP_RATED_KG_S, temperature, molar_mass, 1., gamma)
    feed = n.thermo.system.SystemPrEos(temperature, 8.)
    feed.addComponent("methane", 1.)
    feed.setMixingRule("classic")
    header = DisposalVolume(n, feed)
    valves = []
    demands = jpype.JClass("java.util.HashMap")()
    # The imposed constant flows are calculated independently from the ideal choked-nozzle equation.
    for tag, source_bara, area_m2 in sources:
        cd = .95
        critical_factor = (2 / (gamma + 1)) ** ((gamma + 1) / (gamma - 1))
        mass_flux = source_bara * 1e5 * math.sqrt(gamma * critical_factor * molar_mass / (8.3144621 * temperature))
        rate = cd * area_m2 * mass_flux
        network.addDevice(tag, source_bara, no_loss, area_m2, cd, no_loss, None)
        demands.put(tag, rate)
        fluid = feed.clone()
        fluid.setPressure(source_bara)
        fluid.setTotalFlowRate(rate, "kg/sec")
        valve_out = n.process.equipment.stream.Stream(tag + "-discharge", fluid)
        valve_out.run()
        valves.append(valve_out)
    steady = network.rate(demands)
    for _ in range(400):
        state = header.advance(valves, .1)
    for tag, source_bara, _ in sources:
        if state["header_bara"] / source_bara >= (2 / (gamma + 1)) ** (gamma / (gamma - 1)):
            raise ValueError("Constant-load comparison left the choked regime")
        if abs(steady.devices.get(tag).outletBara - state["header_bara"]) > 1e-4:
            raise ValueError(f"Unsettled or inconsistent flange pressure for {tag}")
    if abs(steady.totalKgS - state["tip_kg_s"]) > 1e-4:
        raise ValueError("Steady and settled dynamic flow differ")
    return {"sources": [s[0] for s in sources], "steady_header_bara": steady.headerBara,
            "dynamic_header_bara": state["header_bara"], "steady_kg_s": steady.totalKgS,
            "dynamic_tip_kg_s": state["tip_kg_s"], "duration_s": 40.}


if __name__ == "__main__":
    jpype.startJVM("-XX:-UsePerfData", "-Xmx400m", "-XX:ActiveProcessorCount=2", convertStrings=True,
                   classpath=os.environ["NEQSIM_CLASSPATH"].split(os.pathsep))
    n = jpype.JPackage("neqsim")
    sources = [("PSV-001", 8., .0025), ("BDV-001", 10., .001)]
    print(json.dumps([compare(n, sources[:1]), compare(n, sources)], indent=2, allow_nan=False))
