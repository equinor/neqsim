"""Compare native HIPPS/SAS behavior with an independent offline emulator."""
import json
import math
from dataclasses import dataclass, field

from steady import E, J, P, ROOT, build


REVISION = "stabilization-commissioning/DYN-C048-A"
SETPOINT_BARA = 29.0
SCAN_DELAY_S = 0.5
FILTER_TAU_S = 0.25
ESD_STROKE_S = 4.0
ESCALATION_DELAY_S = 5.0
IO_MAP = {
    "revision": REVISION,
    "scanPeriodSeconds": [0.25, 0.5],
    "points": [
        {"tag": "20-PT-001A", "direction": "process-to-SAS", "unit": "bara", "endpoint": "20-PSHH-001.A"},
        {"tag": "20-PT-001B", "direction": "process-to-SAS", "unit": "bara", "endpoint": "20-PSHH-001.B"},
        {"tag": "20-PT-001C", "direction": "process-to-SAS", "unit": "bara", "endpoint": "20-PSHH-001.C"},
        {"tag": "20-PSHH-001", "direction": "SAS-internal", "unit": "boolean", "endpoint": "HIPPS-101"},
        {"tag": "20-XV-101A-ZSC", "direction": "field-to-SAS", "unit": "%", "endpoint": "20-XV-101A"},
        {"tag": "20-XV-101B-ZSC", "direction": "field-to-SAS", "unit": "%", "endpoint": "20-XV-101B"},
        {"tag": "20-XV-101A-SOL", "direction": "SAS-to-field", "unit": "boolean", "endpoint": "20-XV-101A"},
        {"tag": "20-XV-101B-SOL", "direction": "SAS-to-field", "unit": "boolean", "endpoint": "20-XV-101B"},
        {"tag": "00-FG-001", "direction": "FGS-to-SAS", "unit": "boolean", "endpoint": "ESD-101"},
        {"tag": "10-ESDV-100-ZSC", "direction": "field-to-SAS", "unit": "%", "endpoint": "10-ESDV-100"},
    ],
}


class SignalConditioner:
    """Apply the declared transport delay and first-order input filter."""

    def __init__(self, dt, initial):
        ratio = SCAN_DELAY_S / dt
        if not math.isclose(ratio, round(ratio), abs_tol=1e-12):
            raise ValueError("Scan delay must be an integer number of scans")
        self.history = [list(initial)] * (int(round(ratio)) + 1)
        self.filtered = list(initial)
        self.alpha = dt / (FILTER_TAU_S + dt)

    def update(self, raw):
        self.history.append(list(raw))
        delayed = self.history.pop(0)
        self.filtered = [old + self.alpha * (new - old) for old, new in zip(self.filtered, delayed)]
        return list(self.filtered)


@dataclass
class OfflineSas:
    """Independent scan-based reference logic and linear final-element emulator."""

    trip_on_lost_quorum: bool
    positions: list = field(default_factory=lambda: [100.0, 100.0])
    closure_times: tuple = (2.0, 3.0)
    hipps_latched: bool = False
    esd_latched: bool = False
    degraded: bool = False
    pst_active: bool = False
    pst_elapsed: float = 0.0
    trip_time: float = None
    escalation_time: float = None
    esd_position: float = 100.0
    demand_seconds: float = 0.0
    time: float = 0.0

    def scan(self, values, valid, fg_demand):
        available = sum(valid)
        votes = sum(ok and value > SETPOINT_BARA for value, ok in zip(values, valid))
        self.degraded = available < 2
        live_demand = votes >= 2
        if not self.hipps_latched and (live_demand or (self.degraded and self.trip_on_lost_quorum)):
            self.hipps_latched = True
            self.trip_time = self.time
            self.pst_active = False
        if fg_demand:
            self.esd_latched = True
        if not live_demand:
            self.demand_seconds = 0.0
        return live_demand

    def advance(self, dt, live_demand, actuator_available):
        esd_active_at_scan_start = self.esd_latched
        if self.hipps_latched and live_demand:
            self.demand_seconds += dt
            if self.demand_seconds >= ESCALATION_DELAY_S and not self.esd_latched:
                self.esd_latched = True
                self.escalation_time = self.time + dt
        if self.hipps_latched:
            for index, available in enumerate(actuator_available):
                if available:
                    rate = 100.0 / self.closure_times[index]
                    self.positions[index] = max(0.0, self.positions[index] - rate * dt)
        elif self.pst_active:
            self.pst_elapsed += dt
            target = 85.0 if self.pst_elapsed < 2.5 else 100.0
            rate = 100.0 / self.closure_times[0]
            direction = 1.0 if target > self.positions[0] else -1.0
            self.positions[0] += direction * min(abs(target - self.positions[0]), rate * dt)
            if self.pst_elapsed >= 5.0:
                self.pst_active = False
        # Native HIPPS escalation activates ESD after this scan's ESD execute call;
        # F&G demands are already active at scan start. Preserve that event order.
        if esd_active_at_scan_start:
            self.esd_position = max(0.0, self.esd_position - 100.0 / ESD_STROKE_S * dt)
        self.time += dt

    def start_pst(self):
        if not self.hipps_latched:
            self.pst_active = True
            self.pst_elapsed = 0.0

    def reset(self, values, valid):
        clear = all(valid) and all(value <= SETPOINT_BARA for value in values)
        if not clear:
            return False
        self.hipps_latched = False
        self.degraded = False
        self.demand_seconds = 0.0
        return True

    def restart(self):
        if self.hipps_latched:
            return False
        self.positions = [100.0, 100.0]
        self.esd_latched = False
        self.esd_position = 100.0
        return True


def _make_native(feed, trip_on_lost_quorum, failed_final=False):
    logic = P.logic.hipps.HIPPSLogic("20-PSHH-001", P.logic.sis.VotingLogic.TWO_OUT_OF_THREE)
    detector = P.logic.sis.Detector
    sensors = []
    for suffix in "ABC":
        sensor = detector("20-PT-001" + suffix, detector.DetectorType.PRESSURE,
                          detector.AlarmLevel.HIGH_HIGH, SETPOINT_BARA, "bara")
        logic.addPressureSensor(sensor)
        sensors.append(sensor)
    valves = []
    for suffix in "AB":
        valve = E.valve.HIPPSValve("20-XV-101" + suffix, feed.clone("20-XV-101" + suffix + " carrier"))
        valve.setOutletPressure(feed.getPressure("bara"))
        valve.run()
        valve.setCalculateSteadyState(False)
        valve.setPhysicalStepIdempotencyEnabled(True)
        logic.addIsolationValve(valve)
        valves.append(valve)
    logic.setValveClosureTime(2.0)
    valves[1].setClosureTime(3.0)
    if failed_final:
        valves[0].setActuatorAvailable(False)
    logic.setTripOnLostVotingCapacity(trip_on_lost_quorum)

    esdv = E.valve.ESDValve("10-ESDV-100", feed.clone("10-ESDV-100 carrier"))
    esdv.setOutletPressure(feed.getPressure("bara"))
    esdv.setStrokeTime(ESD_STROKE_S)
    esdv.setPhysicalStepIdempotencyEnabled(True)
    esdv.run()
    esd = P.logic.esd.ESDLogic("ESD-101")
    esd.addAction(P.logic.action.TripValveAction(esdv), 0.0)
    logic.linkToEscalationLogic(esd, ESCALATION_DELAY_S)
    return logic, sensors, valves, esd, esdv


def _make_vessel(steady_feed, steady_return):
    feed = steady_feed.clone("CHV-100 outlet")
    mixer = E.mixer.Mixer("M-101 SAS replay")
    mixer.addStream(feed)
    mixer.addStream(steady_return.clone("V-213 condensate return"))
    mixer.run()
    vessel = E.separator.ThreePhaseSeparator("V-101 SAS replay", mixer.getOutletStream())
    vessel.setOrientation("horizontal")
    vessel.setInternalDiameter(3.0)
    vessel.setSeparatorLength(45.0 / (math.pi * 1.5**2))
    vessel.setLiquidLevel(0.57)
    vessel.run()
    vessel.setCalculateSteadyState(False)
    vessel.initializeTransientCalculation()
    return feed, mixer, vessel


def _restart_procedure(valves):
    group = P.logic.action.ParallelActionGroup("Reopen independent HIPPS final elements")
    for valve in valves:
        group.addAction(P.logic.action.SetValveTargetAction(valve, 100.0, 0.5))
    procedure = P.logic.procedure.OperatingProcedure(
        "OPS-HIPPS-RESTART-001", "A", "Operator-authorized HIPPS restart")
    procedure.addOperatorStep("REOPEN-20-XV-101AB", None, group, None, 8.0, None)
    procedure.activate()
    procedure.execute(0.0)
    procedure.authorizeOperatorStep("REOPEN-20-XV-101AB")
    procedure.execute(0.0)
    return procedure


def simulate(case, dt, steady_feed, steady_return):
    """Run one matched native/emulator scan sequence against the same physical inputs."""
    lost_policy = case == "lost_quorum_trip"
    failed_final = case == "failed_final"
    feed, mixer, vessel = _make_vessel(steady_feed, steady_return)
    logic, sensors, valves, esd, esdv = _make_native(feed, lost_policy, failed_final)
    emulator = OfflineSas(lost_policy)
    conditioner = SignalConditioner(dt, [vessel.getThermoSystem().getPressure("bara")] * 3)
    identifier = J("neqsim.process.dynamics.TransientStepIdentifier")
    base_rate = feed.getFlowRate("kg/sec")
    cutoff = 1e-9
    rows = []
    native_trip = native_escalation = native_first_seat = None
    reset_denied = reset_allowed = None
    restart_authorized = restart_complete = None
    procedure = None
    pst_started = pst_interrupted = None
    actuator_available = [not failed_final, True]

    short_cases = ("single_high", "lost_quorum_alarm", "lost_quorum_trip", "fg_demand")
    horizon = 12.0 if case in short_cases else 45.0
    for index in range(round(horizon / dt)):
        t = index * dt
        pressure = vessel.getThermoSystem().getPressure("bara")
        raw = [pressure - 0.03, pressure, pressure + 0.03]
        valid = [True, True, True]
        recovery_boundary_restored = (
            case == "reset_restart" and native_trip is not None and t >= native_trip + 4.0
        )
        blocked = (
            case in ("healthy", "failed_final", "pst_interrupt", "reset_restart")
            and t >= 1.0 and not recovery_boundary_restored
        )
        if case == "single_high" and t >= 1.0:
            raw[0] = 35.0
        if case in ("lost_quorum_alarm", "lost_quorum_trip") and t >= 1.0:
            valid[1:] = [False, False]
        fg_demand = case == "fg_demand" and t >= 1.0
        conditioned = conditioner.update(raw)
        if case == "reset_restart" and native_trip is not None:
            if reset_denied is None:
                reset_denied = {"time_s": t, "native": bool(logic.reset()),
                                "emulator": emulator.reset(conditioned, valid)}

        if case == "pst_interrupt" and pst_started is None and pressure >= 28.6:
            valves[0].performPartialStrokeTest(0.15)
            emulator.start_pst()
            pst_started = t
        for sensor, value, ok in zip(sensors, conditioned, valid):
            sensor.setFaulty(not ok)
            sensor.update(value)
        logic.update(*conditioned)
        if fg_demand:
            esd.activate()
        live_demand = emulator.scan(conditioned, valid, fg_demand)
        if logic.isTripped() and native_trip is None:
            native_trip = t
        if pst_started is not None and logic.isTripped() and pst_interrupted is None:
            pst_interrupted = t

        clear_for_reset = all(ok and value <= SETPOINT_BARA for value, ok in zip(conditioned, valid))
        if (case == "reset_restart" and native_trip is not None and t >= native_trip + 4.0
                and clear_for_reset and reset_allowed is None):
            native_ok = bool(logic.reset())
            emulator_ok = emulator.reset(conditioned, valid)
            reset_allowed = {"time_s": t, "native": native_ok, "emulator": emulator_ok}
            if native_ok and emulator_ok:
                procedure = _restart_procedure(valves)
                restart_authorized = t

        openings = [valve.getPercentValveOpening() / 100.0 for valve in valves]
        relative_cv = math.sqrt(2.0 / sum(1.0 / value**2 for value in openings)) if min(openings) > 0 else 0.0
        flow = max(cutoff, base_rate * relative_cv * esdv.getPercentValveOpening() / 100.0)
        vessel.setGasOutletFlowFraction(0.0 if blocked else 1.0)
        feed.setFlowRate(flow, "kg/sec")
        mixer.run()
        step_id = identifier.deterministicPhysicalStep("stabilization-sas-" + case + "-" + str(dt), index)
        esd.execute(dt)
        esdv.runTransient(dt, step_id)
        vessel.runTransient(dt, step_id)
        for valve in valves:
            valve.runTransient(dt, step_id)
        logic.execute(dt)
        emulator.advance(dt, live_demand, actuator_available)
        if procedure is not None and procedure.isActive():
            procedure.execute(dt)
        if logic.hasEscalated() and native_escalation is None:
            native_escalation = t + dt
        if min(valve.getPercentValveOpening() for valve in valves) <= 1e-12 and native_first_seat is None:
            native_first_seat = t + dt
        if procedure is not None and not procedure.isActive() and restart_complete is None:
            restart_complete = t + dt
            esd.reset()
            esdv.reset()
            emulator.restart()

        rows.append({
            "time_s": t + dt, "protected_bara": pressure, "raw_bara": raw,
            "conditioned_bara": conditioned, "valid": valid,
            "native_state": str(logic.getState()), "native_trip": bool(logic.isTripped()),
            "emulator_trip": emulator.hipps_latched,
            "native_xv_pct": [float(valve.getPercentValveOpening()) for valve in valves],
            "emulator_xv_pct": list(emulator.positions),
            "native_esd_pct": float(esdv.getPercentValveOpening()),
            "emulator_esd_pct": emulator.esd_position,
            "native_escalated": bool(logic.hasEscalated()),
            "emulator_escalated": emulator.escalation_time is not None,
            "fg_demand": fg_demand, "feed_kg_s": flow,
        })

    position_error = max(abs(native - offline) for row in rows
                         for native, offline in zip(row["native_xv_pct"], row["emulator_xv_pct"]))
    esd_error = max(abs(row["native_esd_pct"] - row["emulator_esd_pct"]) for row in rows)
    trip_mismatch_scans = sum(row["native_trip"] != row["emulator_trip"] for row in rows)
    alarm_order_native = [name for name, event in (("HIPPS", native_trip), ("ESD", native_escalation))
                          if event is not None]
    alarm_order_emulator = [name for name, event in (("HIPPS", emulator.trip_time), ("ESD", emulator.escalation_time))
                            if event is not None]
    return {
        "case": case, "dt_s": dt, "nativeTripSeconds": native_trip,
        "emulatorTripSeconds": emulator.trip_time, "nativeEscalationSeconds": native_escalation,
        "emulatorEscalationSeconds": emulator.escalation_time,
        "nativeFirstSeatSeconds": native_first_seat,
        "peakProtectedBara": max(row["protected_bara"] for row in rows),
        "maxFinalElementPositionDifferencePercentagePoints": position_error,
        "maxEsdPositionDifferencePercentagePoints": esd_error,
        "tripMismatchScans": trip_mismatch_scans,
        "alarmOrderNative": alarm_order_native, "alarmOrderEmulator": alarm_order_emulator,
        "resetDenied": reset_denied, "resetAllowed": reset_allowed,
        "restartAuthorizedSeconds": restart_authorized, "restartCompleteSeconds": restart_complete,
        "pstStartedSeconds": pst_started, "pstInterruptedSeconds": pst_interrupted,
        "finalNativePositionsPercent": rows[-1]["native_xv_pct"],
        "finalEmulatorPositionsPercent": rows[-1]["emulator_xv_pct"],
        "finalFeedKgPerSecond": rows[-1]["feed_kg_s"],
        "procedure": None if procedure is None else json.loads(str(procedure.snapshot().toJson())),
        "trace": rows,
    }


def execute():
    """Execute the public SAS/virtual-FAT qualification matrix."""
    _, units, _, _, _ = build()
    boundaries = (units["CHV-100"].getOutletStream(), units["RD-203"].getOutletStream())
    names = ("healthy", "single_high", "lost_quorum_alarm", "lost_quorum_trip",
             "failed_final", "pst_interrupt", "reset_restart", "fg_demand")
    cases = {name: simulate(name, 0.5, *boundaries) for name in names}
    cases["healthy_refined"] = simulate("healthy", 0.25, *boundaries)
    cases["deterministic_replay"] = simulate("healthy", 0.5, *boundaries)
    cases["deterministicReplayMatches"] = cases["healthy"] == cases["deterministic_replay"]
    cases["tripTimeStepSensitivitySeconds"] = abs(
        cases["healthy"]["nativeTripSeconds"] - cases["healthy_refined"]["nativeTripSeconds"])
    cases["ioMap"] = IO_MAP
    cases["modelRevision"] = REVISION
    cases["acceptanceTolerances"] = {
        "logicMismatchScans": 0,
        "tripTimeDifferenceSeconds": 0.0,
        "finalElementPositionDifferencePercentagePoints": 1e-9,
        "tripTimeStepSensitivitySeconds": 0.5,
    }
    cases["limitations"] = [
        "The offline emulator is deterministic reference logic, not a certified PLC or vendor DCS runtime.",
        "The V-101 boundary uses the documented normalized series-resistance surrogate; it is not a bidirectional hydraulic network.",
        "Reset/restart restores the normal outlet boundary; a project depressurization and flare network remain separate.",
        "The synthetic evidence is not a SIL assessment, SRS approval, site FAT, construction approval, or operating approval.",
    ]
    return cases


def validate(result):
    """Fail when declared native/emulator and process-response checks are not met."""
    compared = [value for value in result.values() if isinstance(value, dict) and "case" in value]
    assert all(case["tripMismatchScans"] == 0 for case in compared)
    assert all(case["maxFinalElementPositionDifferencePercentagePoints"] <= 1e-9 for case in compared)
    assert all(case["maxEsdPositionDifferencePercentagePoints"] <= 1e-9 for case in compared)
    assert all(case["alarmOrderNative"] == case["alarmOrderEmulator"] for case in compared)
    assert result["single_high"]["nativeTripSeconds"] is None
    assert result["lost_quorum_alarm"]["nativeTripSeconds"] is None
    assert result["lost_quorum_alarm"]["trace"][-1]["native_state"] == "FAILED"
    assert result["lost_quorum_trip"]["nativeTripSeconds"] is not None
    assert result["failed_final"]["nativeFirstSeatSeconds"] is not None
    assert result["failed_final"]["finalFeedKgPerSecond"] <= 1e-9
    assert result["pst_interrupt"]["pstStartedSeconds"] < result["pst_interrupt"]["pstInterruptedSeconds"]
    reset = result["reset_restart"]
    assert not reset["resetDenied"]["native"] and not reset["resetDenied"]["emulator"]
    assert reset["resetAllowed"]["native"] and reset["resetAllowed"]["emulator"]
    assert reset["restartAuthorizedSeconds"] < reset["restartCompleteSeconds"]
    assert reset["procedure"]["state"] == "COMPLETED"
    assert all(value == 100.0 for value in reset["finalNativePositionsPercent"])
    assert result["fg_demand"]["nativeTripSeconds"] is None
    assert result["fg_demand"]["trace"][-1]["native_esd_pct"] == 0.0
    assert result["tripTimeStepSensitivitySeconds"] <= 0.5
    assert result["deterministicReplayMatches"]


if __name__ == "__main__":
    result = execute()
    validate(result)
    output = ROOT / "sas.json"
    output.write_text(json.dumps(result, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, allow_nan=False))
