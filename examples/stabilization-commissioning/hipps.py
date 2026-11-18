"""Blocked V-101 outlet, live HIPPS voting, two series actuators and ESD backup."""
import csv
import json
import math

from steady import E, J, P, ROOT, build


def simulate(case, dt, steady_feed, steady_return):
    """Replay a native VU vessel with one explicit pressure scan per physical step."""
    feed = steady_feed.clone("CHV-100 outlet")
    mixer = E.mixer.Mixer("M-101")
    mixer.addStream(feed)
    mixer.addStream(steady_return.clone("V-213 condensate return"))
    mixer.run()
    vessel = E.separator.ThreePhaseSeparator("V-101", mixer.getOutletStream())
    vessel.setOrientation("horizontal")
    vessel.setInternalDiameter(3.0)
    vessel.setSeparatorLength(45.0 / (math.pi * 1.5**2))
    vessel.setLiquidLevel(0.57)
    vessel.run()
    vessel.setCalculateSteadyState(False)
    vessel.initializeTransientCalculation()

    logic = P.logic.hipps.HIPPSLogic("HIPPS-101", P.logic.sis.VotingLogic.TWO_OUT_OF_THREE)
    detector = P.logic.sis.Detector
    for suffix in "ABC":
        logic.addPressureSensor(detector("PT-101" + suffix, detector.DetectorType.PRESSURE,
                                         detector.AlarmLevel.HIGH_HIGH, 29.0, "bara"))
    valves = [E.valve.HIPPSValve("XV-101" + suffix) for suffix in "AB"]
    for valve in valves:
        valve.setCalculateSteadyState(False)
        logic.addIsolationValve(valve)
    logic.setValveClosureTime(2.0)
    valves[1].setClosureTime(3.0)
    if case in ("first_stuck", "both_stuck"):
        valves[0].setActuatorAvailable(False)
    if case == "both_stuck":
        valves[1].setActuatorAvailable(False)
    esdv = E.valve.ESDValve("ESDV-100", feed.clone("ESDV inlet carrier"))
    esdv.setOutletPressure(feed.getPressure("bara"))
    esdv.setStrokeTime(4.0)
    esdv.run()
    esd = P.logic.esd.ESDLogic("ESD-101")
    esd.addAction(P.logic.action.TripValveAction(esdv), 0.0)
    logic.linkToEscalationLogic(esd, 5.0)

    base_rate = feed.getFlowRate("kg/sec")
    cutoff = 1e-9  # kg/s; numerical carrier for the inlet flash
    first_trip = first_seat = escalation = None
    rows = []
    uuid = J("java.util.UUID")
    for i in range(round(45.0 / dt) + 1):
        t = i * dt
        p = vessel.getThermoSystem().getPressure("bara")
        readings = [p - 0.03, p, p + 0.03]
        if case == "single_high" and t >= 1:
            readings[0] = 35.0
        logic.update(*readings)
        if logic.isTripped() and first_trip is None:
            first_trip = t
        opening = [valve.getPercentValveOpening() / 100 for valve in valves]
        if min(opening) == 0 and first_seat is None:
            first_seat = t
        # Equal full-open resistances in series, each with Cv proportional to
        # stem travel. This reduced hydraulic boundary is normalized to the steady feed.
        relative_cv = math.sqrt(2 / sum(1 / x**2 for x in opening)) if min(opening) > 0 else 0
        esd_opening = esdv.getPercentValveOpening() / 100
        flow = max(cutoff, base_rate * relative_cv * esd_opening)
        rows.append([t, p, *readings, int(logic.isTripped()), *opening,
                     *(v.getTargetPercentValveOpening() / 100 for v in valves), esd_opening, flow])
        if i == round(45.0 / dt):
            break
        vessel.setGasOutletFlowFraction(0.0 if case != "single_high" and t >= 1 else 1.0)
        feed.setFlowRate(flow, "kg/sec")
        mixer.run()
        step_id = uuid.randomUUID()
        esd.execute(dt)
        esdv.runTransient(dt, step_id)
        vessel.runTransient(dt, step_id)
        for valve in valves:
            valve.runController(dt, step_id)
        logic.execute(dt)
        if logic.hasEscalated() and escalation is None:
            escalation = t + dt

    with (ROOT / f"hipps-{case}-{dt}.csv").open("w", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["t_s", "protected_bara", "PT_A_bara", "PT_B_bara", "PT_C_bara",
                         "hipps_trip", "XV_A_fraction", "XV_B_fraction", "XV_A_command", "XV_B_command",
                         "ESD_fraction", "feed_kg_s"])
        writer.writerows(rows)
    return {"dt_s": dt, "trip_s": first_trip, "first_seat_s": first_seat, "escalation_s": escalation,
            "peak_bara": max(row[1] for row in rows), "final_feed_kg_s": rows[-1][-1]}


if __name__ == "__main__":
    _, units, _, _, _ = build()
    boundaries = (units["CHV-100"].getOutletStream(), units["RD-203"].getOutletStream())
    results = {case: simulate(case, 0.5, *boundaries) for case in ("healthy", "single_high", "first_stuck", "both_stuck")}
    results["healthy_refined"] = simulate("healthy", 0.25, *boundaries)
    for case in ("healthy", "healthy_refined", "first_stuck"):
        run = results[case]
        travel = 3.0 if case == "first_stuck" else 2.0
        assert run["first_seat_s"] - run["trip_s"] == travel
        assert run["final_feed_kg_s"] <= 1e-9
    assert results["single_high"]["trip_s"] is None
    stuck = results["both_stuck"]
    assert stuck["first_seat_s"] is None and stuck["escalation_s"] == stuck["trip_s"] + 5
    assert stuck["final_feed_kg_s"] <= 1e-9 and stuck["peak_bara"] > results["healthy"]["peak_bara"]
    assert abs(results["healthy"]["trip_s"] - results["healthy_refined"]["trip_s"]) <= 0.5
    (ROOT / "hipps.json").write_text(json.dumps(results, indent=2, allow_nan=False))
    print(json.dumps(results, indent=2))
