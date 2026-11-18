"""Replay pressure protection on a three-phase subsea tie-back. All inputs are synthetic."""

import argparse
import json
import math
import os
from dataclasses import asdict, dataclass, replace
from pathlib import Path

import jpype

from tieback import make_pipe, stream
from tieback_host import HostResponse, INLET_SEAT_TIME_S, SEPARATOR_PSHH_BARA


@dataclass(frozen=True)
class Zone:
    name: str
    end_m: float
    design_bara: float
    allowable_bara: float


# Design and transient allowable pressures are independent project inputs.
ZONES = (Zone("fortified spool", 800., 130., 130.),
         Zone("LC1 flowline", 3650., 72., 73.),
         Zone("LC2 approach and riser", 5400., 80., 82.))
CASES = ("normal", "blocked", "psd_failed", "first_hipv_stuck",
         "all_isolation_failed", "false_high", "host_psd", "host_gas_blocked", "host_psv_only",
         "choke_failed_open")
PSD_TAG = "PST-101"
HIPPS_TAGS = ("PST-201A", "PST-201B", "PST-201C")
PSD_BARA = 70.0
HIPPS_BARA = 70.6
HOST_BARA = 58.5
DESIGN_RATE = 20.
SERIES_RESISTANCE = (80. - 69.55044648172829) / DESIGN_RATE**2


@dataclass(frozen=True)
class ProtectionBasis:
    """All trip pressures are absolute. Each stroke starts on a sampled demand."""

    psd_bara: float = PSD_BARA
    hipps_bara: float = HIPPS_BARA
    host_riser_bara: float = HOST_BARA
    tree_seat_s: float = 3.
    dhsv_stroke_s: float = 6.
    hipv_a_stroke_s: float = 2.
    hipv_b_stroke_s: float = 3.
    zones: tuple = ZONES

    def __post_init__(self):
        settings = (self.psd_bara, self.hipps_bara, self.host_riser_bara, self.tree_seat_s,
                    self.dhsv_stroke_s, self.hipv_a_stroke_s, self.hipv_b_stroke_s)
        if not all(math.isfinite(x) and x > 0 for x in settings):
            raise ValueError("Trip pressures and actuator times must be positive and finite")
        if not self.zones or len({z.name for z in self.zones}) != len(self.zones):
            raise ValueError("Give each pressure zone a distinct name")

    @classmethod
    def read(cls, path):
        if path is None:
            return cls()
        values = json.loads(Path(path).read_text(encoding="utf-8"))
        if "zones" in values:
            values["zones"] = tuple(Zone(**zone) for zone in values["zones"])
        return cls(**values)


def make_plant(n, mesh, separator_bara=54.4):
    """Build the steady operating lineup. Return the process and its live equipment."""
    p = n.process
    eq = p.equipment
    plant = p.processmodel.ProcessSystem()
    reservoir = stream(n, "RES-101", "Srk", 120., 20.)
    plant.add(reservoir)
    well = eq.reservoir.WellFlow("W-101")
    well.setInletStream(reservoir)
    well.setWellProductionIndex(reservoir.getFlowRate("MSm3/day") / (120.**2 - 80.**2))
    well.solveFlowFromOutletPressure(True)
    well.setOutletPressure(80., "bara")
    well.run()
    plant.add(well)

    def add_valve(kind, tag, inlet, pressure):
        valve = kind(tag, inlet)
        valve.setOutletPressure(pressure, "bara")
        valve.run()
        plant.add(valve)
        return valve

    dhsv = add_valve(eq.valve.ESDValve, "DHSV-101", well.getOutletStream(), 80.)
    dhsv.setStrokeTime(6.)
    tree = eq.subsea.SubseaTree("XT-101", dhsv.getOutletStream())
    tree.run()
    plant.add(tree)
    choke = add_valve(eq.valve.ThrottlingValve, "CHV-101", tree.getOutletStream(), 70.3)
    manifold = eq.subsea.SubseaManifold("MAN-101", 1)
    manifold.setProductionHeaderSizeInches(12.)
    manifold.addWellStream(choke.getOutletStream(), "W-101")
    manifold.run()
    plant.add(manifold)
    inlet = manifold.getProductionStream()
    hipvs = []
    for suffix in "AB":
        valve = add_valve(eq.valve.HIPPSValve, "HIPV-201" + suffix, inlet, inlet.getPressure("bara"))
        valve.setCalculateSteadyState(False)
        hipvs.append(valve)
        inlet = valve.getOutletStream()
    pipe = make_pipe(eq, inlet, mesh)
    # Match the installed well and restriction on this grid before switching
    # solvers. Even a tiny unmatched rate can activate a transient at handoff.
    for _ in range(15):
        rate = supply_rate(pipe.getInletFacePressure("bara"), DESIGN_RATE, SERIES_RESISTANCE,
                           1., (1., 1.), False, False)
        if abs(rate - inlet.getFlowRate("kg/sec")) < 1e-10:
            break
        pwh = math.sqrt(120.**2 - rate / DESIGN_RATE * (120.**2 - 80.**2))
        well.setOutletPressure(pwh, "bara")
        well.run()
        dhsv.setOutletPressure(pwh, "bara")
        for unit in (dhsv, tree, choke, manifold, *hipvs):
            unit.run()
        pipe.run()
        if not pipe.isSteadyStateConverged():
            raise ValueError("Coupled well and pipeline initialization did not converge")
    else:
        raise ValueError("Unmatched well rate at the transient inlet")
    plant.add(pipe)
    riser = pipe.getOutletStream()
    esv = add_valve(eq.valve.ESDValve, "ESV-301", riser, riser.getPressure("bara"))
    xsv = add_valve(eq.valve.ESDValve, "XSV-302", esv.getOutletStream(), riser.getPressure("bara"))
    host_choke = add_valve(eq.valve.ThrottlingValve, "CHV-301", xsv.getOutletStream(), separator_bara)
    separator = eq.separator.ThreePhaseSeparator("V-301", host_choke.getOutletStream())
    separator.setInternalDiameter(2.2)
    separator.setSeparatorLength(7.)
    separator.setLiquidLevel(.5)
    separator.run()
    plant.add(separator)
    for tag, source in [(s, inlet) for s in (PSD_TAG, *HIPPS_TAGS)] + [
            ("PST-301", riser), ("PT-302", separator.getGasOutStream())]:
        plant.add(p.measurementdevice.PressureTransmitter(tag, source))
    plant.add(p.measurementdevice.OilLevelTransmitter("LT-301", separator))
    plant.add(p.measurementdevice.WaterLevelTransmitter("LIT-302", separator))
    return plant, well, dhsv, tree, manifold, hipvs, pipe, separator


def zone_indices(zones, pipe):
    """Require a contiguous positive-length partition ending at the actual pipe outlet."""
    lengths = list(pipe.getSectionLengths())
    result = []
    start = idx = 0
    faces = [0.]
    for length in lengths:
        faces.append(faces[-1] + length)
    for zone in zones:
        while idx < len(lengths) and faces[idx] < zone.end_m - 1e-6:
            idx += 1
        if (not math.isclose(faces[idx], zone.end_m, abs_tol=1e-6) or idx == start
                or not 0 < zone.design_bara <= zone.allowable_bara < math.inf):
            raise ValueError("Each zone needs valid limits and a positive extent bounded by mesh faces")
        result.append(range(start, idx))
        start = idx
    if idx != len(lengths):
        raise ValueError("Pressure ratings must cover the entire route")
    return result


def supply_rate(line_p, q0, resistance, dhsv_open, hipv_open, tree_closed, choke_failed):
    """Solve the well IPR and series restrictions at the previous accepted line pressure."""
    if tree_closed or min(dhsv_open, *hipv_open) == 0. or line_p >= 120.:
        return 0.
    choke_resistance = .24 if choke_failed else .97
    overall = resistance * (choke_resistance + .01 / dhsv_open**2
                            + sum(.01 / opening**2 for opening in hipv_open))
    # q = PI * (Pr^2 - Pwh^2), Pwh = Pline + R*q^2. The residual is monotone.
    drawdown = 120.**2 - 80.**2
    low, high = 0., q0 * 120.**2 / drawdown
    for _ in range(42):
        mid = (low + high) / 2.
        pwh = line_p + overall * mid**2
        if mid > q0 * max(0., 120.**2 - pwh**2) / drawdown:
            high = mid
        else:
            low = mid
    return (low + high) / 2.


def run(n, case, dt=.25, duration=90., mesh=1, basis=ProtectionBasis()):
    if case not in CASES or mesh not in (1, 2) or dt not in (.125, .25, .5):
        raise ValueError("Unsupported scenario, time step or mesh")
    steps = round(duration / dt)
    if duration <= 2. or not math.isclose(steps * dt, duration, abs_tol=1e-9):
        raise ValueError("Duration must include the event and end on a scan")
    host_case = case in ("host_gas_blocked", "host_psv_only")
    plant, well, dhsv, tree, manifold, valves, pipe, sep = make_plant(
        n, mesh, separator_bara=48. if host_case else 54.4)
    zones = basis.zones
    groups = zone_indices(zones, pipe)
    dhsv.setStrokeTime(basis.dhsv_stroke_s)
    locations = list(pipe.getPositionProfile())
    p = n.process
    detector = p.logic.sis.Detector

    def pressure_switch(tag, setpoint):
        return detector(tag, detector.DetectorType.PRESSURE, detector.AlarmLevel.HIGH_HIGH,
                        setpoint, "bara")

    psd = pressure_switch(PSD_TAG, basis.psd_bara)
    host_psd = pressure_switch("PST-301", basis.host_riser_bara)
    hipps = p.logic.hipps.HIPPSLogic("HIPPS-201", p.logic.sis.VotingLogic.TWO_OUT_OF_THREE)
    for tag in HIPPS_TAGS:
        hipps.addPressureSensor(pressure_switch(tag, basis.hipps_bara))
    for valve in valves:
        hipps.addIsolationValve(valve)
    hipps.setValveClosureTime(basis.hipv_a_stroke_s)
    valves[1].setClosureTime(basis.hipv_b_stroke_s)
    psd_available = case not in ("psd_failed", "first_hipv_stuck", "all_isolation_failed")
    for index, valve in enumerate(valves):
        valve.setActuatorAvailable(not (case == "all_isolation_failed"
                                       or (case == "first_hipv_stuck" and index == 0)))

    q0 = pipe.getInletStream().getFlowRate("kg/sec")
    host_rates = [out.getFlowRate("kg/sec") for out in
                  (sep.getGasOutStream(), sep.getOilOutStream(), sep.getWaterOutStream())]
    if min(host_rates) <= 0 or abs(sum(host_rates) - q0) > 1e-6:
        raise ValueError("Steady three-phase host mass balance failed")
    # Freeze the quadratic choke/header resistance at the initial operating point.
    resistance = SERIES_RESISTANCE
    peaks = [{"peak_bara": -math.inf} for zone in zones]
    events, trace = {}, []
    max_mass_error = max_component_error = admitted = discharged = 0.
    seated_tree = False
    phase = p.equipment.pipeline.TwoFluidMassBalanceReport.Phase
    phases = (phase.GAS, phase.OIL, phase.WATER)
    steady = {"well_kg_s": q0, "separator_bara": sep.getPressure("bara"),
              "host_gas_oil_water_kg_s": host_rates}
    host = HostResponse(n, plant, sep) if host_case else None
    for i in range(steps + 1):
        t = i * dt
        line_p = pipe.getInletFacePressure("bara")
        riser_p = pipe.getOutletPressure("bara")
        pressures = [v / 1e5 for v in pipe.getPressureProfile()]
        if not all(math.isfinite(v) and v > 0 for v in [line_p, riser_p, *pressures]):
            raise ValueError(f"Invalid pressure at {t} s")
        zone_max = []
        for j, cells in enumerate(groups):
            points = [(pressures[k], locations[k]) for k in cells]
            if j == 0:
                points.append((line_p, 0.))
            if j == len(groups) - 1:
                points.append((riser_p, pipe.getLength()))
            peak, where = max(points)
            zone_max.append(peak)
            if peak > peaks[j]["peak_bara"]:
                peaks[j] = {"peak_bara": peak, "at_m": where, "at_s": t}

        readings = [line_p] * 3
        if case == "false_high" and t >= 2.:
            readings[0] = 125.
        psd.update(line_p)
        host_psd.update(riser_p)
        hipps.update(*readings)
        if psd.isTripped():
            events.setdefault("subsea_psd_demand_s", t)
            if psd_available and "subsea_psd_command_s" not in events:
                events["subsea_psd_command_s"] = t
                dhsv.trip()
        if psd_available and "subsea_psd_command_s" in events and not seated_tree:
            if t - events["subsea_psd_command_s"] >= basis.tree_seat_s:
                tree.emergencyShutdown()
                tree.run()
                manifold.run()
                if tree.getOutletStream().getFlowRate("kg/sec") != 0.:
                    raise ValueError("Subsea tree failed to stop production")
                seated_tree = True
                events["tree_seated_s"] = t
        if hipps.isTripped():
            events.setdefault("hipps_trip_s", t)
        openings = [valve.getPercentValveOpening() / 100. for valve in valves]
        for j, opening in enumerate(openings):
            if opening == 0.:
                events.setdefault(f"hipv_{'ab'[j]}_seated_s", t)
        if host_psd.isTripped():
            events.setdefault("host_psd_demand_s", t)
        # The host trip has its own initiation case. An external separator trip
        # seats the inlet valve at 2 s; the pressure taps remain upstream of it.
        blocked = t >= 2. and case in ("blocked", "psd_failed", "first_hipv_stuck",
                                        "all_isolation_failed", "host_psd")
        if host is not None:
            if host.pressure() >= SEPARATOR_PSHH_BARA:
                events.setdefault("separator_pshh_s", t)
            if case == "host_gas_blocked" and "separator_pshh_s" in events:
                blocked = t >= events["separator_pshh_s"] + INLET_SEAT_TIME_S
            if not blocked and riser_p <= host.pressure():
                raise ValueError(f"Platform backpressure control has no forward head at {t} s")
        if blocked and "outlet_blocked_s" not in events:
            pipe.closeOutlet()
            events["outlet_blocked_s"] = t
            if case == "host_psd":
                events["external_host_trip_s"] = t
        if case == "host_psd" and host_psd.isTripped() and "remote_psd_s" not in events:
            events["remote_psd_s"] = t
            if "subsea_psd_command_s" not in events:
                events["subsea_psd_command_s"] = t
                dhsv.trip()

        dhsv_open = dhsv.getPercentValveOpening() / 100.
        rate = supply_rate(line_p, DESIGN_RATE, resistance, dhsv_open, openings, seated_tree,
                           case == "choke_failed_open" and t >= 2.)
        trace.append({"s": t, "subsea_bara": line_p, "riser_bara": riser_p,
                      "zone_peaks_bara": zone_max, "hipps_readings_bara": readings,
                      "hipv_open_fraction": openings, "dhsv_open_fraction": dhsv_open,
                      "tree_open": not seated_tree, "requested_inlet_kg_s": rate})
        if host is not None:
            trace[-1]["separator_bara"] = host.pressure()
            trace[-1]["oil_level_m"] = sep.getOilLevel()
            trace[-1]["water_interface_m"] = sep.getWaterLevel()
        if i == steps:
            break
        step = jpype.JClass("java.util.UUID")(0, i + 1)
        if rate == 0.:
            pipe.closeInlet()
        else:
            pipe.setInletMassFlow(rate)
            pipe.setInletBoundaryCondition(pipe.BoundaryCondition.CONSTANT_FLOW)
        pipe.runTransient(dt, step)
        balance = pipe.getLastMassBalanceReport()
        mass_in = sum(balance.getInletMassKg(phase) for phase in phases)
        mass_out = sum(balance.getOutletMassKg(phase) for phase in phases)
        if (not math.isclose(mass_in, rate * dt, abs_tol=1e-7)
                or (blocked and abs(mass_out) > 1e-7)):
            raise ValueError(f"Boundary transfer mismatch at {t} s: {mass_in}, {rate*dt}, {mass_out}")
        if host is not None:
            if mass_out < -1e-8 or (not blocked and pipe.isTransientOutletBackflowClamped()):
                raise ValueError(f"Reverse flow outside the host handoff basis at {t} s")
            host.advance(t, dt, step, pipe.getOutletStream(), mass_out, blocked)
        admitted += mass_in
        discharged += mass_out
        residuals = [abs(balance.getResidualKg(phase)) for phase in phases]
        component = pipe.getLastComponentConservationReport()
        species_errors = [abs(x) for x in component.getInventoryResidualKg()]
        if (not component.isConverged()
                or not all(math.isfinite(x) and x <= 1e-6 for x in residuals + species_errors)):
            raise ValueError(f"Inventory closure failed at {t + dt} s")
        max_mass_error = max(max_mass_error, *residuals)
        max_component_error = max(max_component_error, *species_errors)
        if not dhsv.isEnergized():
            dhsv.runTransient(dt, step)
        for valve in valves:
            valve.runController(dt, step)
        hipps.execute(dt)

    for zone, peak in zip(zones, peaks):
        peak.update(asdict(zone))
        peak["margin_bar"] = zone.allowable_bara - peak["peak_bara"]
        peak["rated_for_source_at_common_datum"] = zone.design_bara >= 120.
    return {"case": case, "dt_s": dt, "mesh_cells": len(locations), "duration_s": duration,
            "basis": asdict(basis), "qualification": "TRANSIENT_SCREENING",
            "transient_pressure_limiter_count": int(pipe.getTransientPressureLimitCount()),
            "steady": steady, "dynamic_host": host.result() if host else None, "zones": peaks, "events": events, "admitted_kg": admitted,
            "host_transfer_kg": discharged, "phase_residual_kg": max_mass_error,
            "component_residual_kg": max_component_error, "trace": trace}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", choices=CASES, required=True)
    parser.add_argument("--dt", type=float, default=.25)
    parser.add_argument("--duration", type=float, default=90.)
    parser.add_argument("--mesh", type=int, choices=(1, 2), default=1)
    parser.add_argument("--basis", type=Path, help="JSON override of ProtectionBasis fields")
    parser.add_argument("--fully-rated", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    jpype.startJVM("-Xmx768m", "-XX:ActiveProcessorCount=2", convertStrings=True,
                   classpath=os.environ["NEQSIM_CLASSPATH"].split(os.pathsep))
    basis = ProtectionBasis.read(args.basis)
    if args.fully_rated:
        basis = replace(basis, zones=tuple(Zone(z.name, z.end_m, 130., 130.) for z in basis.zones))
    result = run(jpype.JPackage("neqsim"), args.case, args.dt, args.duration, args.mesh, basis)
    payload = json.dumps(result, indent=2, allow_nan=False)
    with args.output.open("x", encoding="utf-8") as output:
        output.write(payload + "\n")


if __name__ == "__main__":
    main()
