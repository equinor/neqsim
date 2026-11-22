"""Compare two V-101 pressure models with identical initial masses and boundary withdrawals."""
import csv
import json
import math

import numpy as np

from steady import J, P, ROOT, build


PHASES = ("gas", "oil", "aqueous")


def phase_masses(fluid):
    """Return phase masses in kg (or kg/s for a NeqSim stream)."""
    return np.array([
        fluid.getPhase(name).getNumberOfMolesInPhase() * fluid.getPhase(name).getMolarMass()
        for name in PHASES
    ])


def simulate(dt):
    _, units, _, _, _ = build()
    vessel = units["V-101"]
    vessel.setOrientation("horizontal")
    vessel.setInternalDiameter(3.0)
    vessel.setSeparatorLength(45.0 / (math.pi * 1.5 ** 2))
    vessel.setLiquidLevel(0.57)
    vessel.run()
    vessel.setCalculateSteadyState(False)
    vessel.initializeTransientCalculation()

    fluid = vessel.getThermoSystem()
    initial_mass = phase_masses(fluid)
    inventory = initial_mass.copy()
    volume = np.array([fluid.getPhase(name).getVolume("m3") for name in PHASES])
    liquid_density = initial_mass[1:] / volume[1:]
    p0 = fluid.getPressure("bara")
    feed = units["CHV-100"].getOutletStream()
    feed_rate = feed.getFlowRate("kg/sec")
    return_rate = units["RD-203"].getOutletStream().getFlowRate("kg/sec")
    mixed = units["M-101"]
    outlets = (vessel.getGasOutStream(), vessel.getOilOutStream(), vessel.getWaterOutStream())
    base_draw = np.array([stream.getFlowRate("kg/sec") for stream in outlets])
    vote = P.logic.sis.VotingLogic.TWO_OUT_OF_THREE
    uuid = J("java.util.UUID")
    since = [None, None]
    first_out = [None, None]
    mass_error = 0.0
    rows = []
    for step in range(round(65 / dt) + 1):
        t = step * dt
        native_mass = phase_masses(vessel.getThermoSystem())
        mass_error = max(mass_error, abs(native_mass.sum() - inventory.sum()))
        gas_volume = volume.sum() - np.sum(inventory[1:] / liquid_density)
        frozen_pressure = p0 * inventory[0] / initial_mass[0] * volume[0] / gas_volume
        pressures = (vessel.getThermoSystem().getPressure("bara"), frozen_pressure)
        for i, pressure in enumerate(pressures):
            demand = vote.evaluate(sum(pressure + offset >= 29.0 for offset in (-0.03, 0, 0.03)))
            if demand and since[i] is None:
                since[i] = t
            if not demand:
                since[i] = None
            if demand and t - since[i] >= 1 and first_out[i] is None:
                first_out[i] = t

        # The native vote drives both models' physical boundaries. Retain the
        # converged V-213 return as a specified external flow during this short replay.
        age = 0 if first_out[0] is None else t - first_out[0]
        inlet_rate = max(1e-9, feed_rate * max(0, 1 - age / 4))
        draw = base_draw * np.array([float(t < 1), max(0, 1 - age / 3), 1])
        rows.append([t, *pressures, inlet_rate, return_rate, *draw, native_mass.sum(), inventory.sum()])
        if step == round(65 / dt):
            break
        feed.setFlowRate(float(inlet_rate), "kg/sec")
        mixed.run()
        phase_in = phase_masses(mixed.getOutletStream().getFluid())
        if abs(phase_in.sum() - inlet_rate - return_rate) > 1e-7:
            raise ValueError("Mismatch between the declared and flashed inlet rates")
        for stream, rate, set_fraction in zip(outlets, draw, (
            vessel.setGasOutletFlowFraction, vessel.setOilOutletFlowFraction,
            vessel.setWaterOutletFlowFraction,
        )):
            set_fraction(float(rate > 0))
            if rate > 0:
                stream.setFlowRate(float(rate), "kg/sec")
        inventory += dt * (phase_in - draw)
        vessel.runTransient(dt, uuid.randomUUID())

    with (ROOT / f"matched-v101-{dt}.csv").open("w", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["t_s", "native_bara", "frozen_bara", "feed_kg_s", "return_kg_s",
                         "gas_out_kg_s", "oil_out_kg_s", "water_out_kg_s",
                         "native_mass_kg", "frozen_mass_kg"])
        writer.writerows(rows)
    if any(t is None for t in first_out) or mass_error > 1e-5:
        raise ValueError(f"Replay failed: first-outs {first_out}, total mass discrepancy {mass_error} kg")
    return {"step_s": dt, "native_trip_after_block_s": first_out[0] - 1,
            "frozen_trip_after_block_s": first_out[1] - 1, "max_mass_difference_kg": mass_error,
            "initial_phase_mass_kg": initial_mass.tolist(), "initial_phase_volume_m3": volume.tolist()}


if __name__ == "__main__":
    results = [simulate(dt) for dt in (0.5, 0.25)]
    for key in ("native_trip_after_block_s", "frozen_trip_after_block_s"):
        if abs(results[0][key] - results[1][key]) > 1:
            raise ValueError(f"Trip time changes by more than one second on refinement: {key}")
    (ROOT / "matched-v101.json").write_text(json.dumps(results, indent=2))
    print(results)
