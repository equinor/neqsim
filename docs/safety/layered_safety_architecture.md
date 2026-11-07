---
title: Layered Safety Logic Architecture
description: Current NeqSim HIPPS, voting, detector, ESD action, and valve-state APIs with an executable Java 8 quick start and engineering review boundaries.
---

NeqSim can exercise detector voting, HIPPS isolation, safety-instrumented
function logic, ESD action sequences, and transient final-element response. These
objects support simulation and verification studies. They do not establish a
Safety Integrity Level (SIL), probability of failure on demand, standards
compliance, independence, proof-test interval, or suitability for a real
installation.

## Maintained software layers

| Layer | Current NeqSim owner | Simulation responsibility |
|---|---|---|
| Measurement | `Detector` | Applies a configured high or low threshold to an explicit measured value and unit |
| Voting | `VotingLogic` | Evaluates the required number of tripped channels, such as two out of three |
| HIPPS logic | `HIPPSLogic` | Closes a configured `ThrottlingValve` and optionally activates backup logic after a sustained demand |
| Fire/gas SIF logic | `SafetyInstrumentedFunction` | Votes configured detectors and activates linked `ProcessLogic` instances |
| ESD sequence | `ESDLogic` and `LogicAction` implementations | Executes ordered actions after their configured relative delays |
| Final element | `ESDValve` | Tracks trip demand, actuator availability, stroke time, fail-safe position, and transient travel |

The configured detector unit and setpoint belong to the model case. NeqSim does
not select plant trip settings, voting architecture, SIL targets, or response
times. Those decisions require the applicable project basis, hazard analysis,
failure data, independence assessment, and accountable functional-safety
review.

## Execution ownership

- `HIPPSLogic.update(...)` updates the configured pressure detectors and
  evaluates their voting state.
- `HIPPSLogic.execute(dt)` advances the escalation timer. It does not calculate
  process pressure.
- `ESDLogic.execute(dt)` advances one ordered action sequence. Activating the
  sequence alone does not advance it.
- `TripValveAction` issues a trip demand to an `ESDValve`. Physical valve
  travel is advanced separately by the process transient step through
  `ESDValve.runTransient(dt, calculationId)`.
- Detector trips are latched by the current `Detector` implementation.
  Resetting logic bookkeeping does not prove that a process demand has cleared
  or that plant restart permissives are satisfied.

## Complete Java 8 quick start

This program checks the discrete demand path. Two of three pressure channels
cross an explicit 95 bara screening setpoint, the HIPPS isolation valve closes,
the sustained vote activates ESD after two seconds, and the ESD action places
its final element in the closing state. The example deliberately stops before a
physical process transient; a full scenario must also run the process model and
the ESD valve transient state with a stable calculation identity.

<!-- layered-safety-example: hipps-esd-quickstart -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.hipps.HIPPSLogic;
import neqsim.process.logic.sis.Detector;
import neqsim.process.logic.sis.Detector.AlarmLevel;
import neqsim.process.logic.sis.Detector.DetectorType;
import neqsim.process.logic.sis.VotingLogic;

/**
 * Exercises a 2oo3 HIPPS demand and timed escalation into an ESD trip action.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class LayeredSafetyQuickStart {
  private static final Logger logger = LogManager.getLogger(LayeredSafetyQuickStart.class);

  /** Prevents instantiation of this example utility. */
  private LayeredSafetyQuickStart() {}

  /**
   * Runs one bounded safety-logic scenario.
   *
   * @param args command-line arguments (unused)
   */
  public static void main(String[] args) {
    double pressureSetpointBara = 95.0;

    ThrottlingValve hippsIsolation = new ThrottlingValve("HIPPS isolation");
    hippsIsolation.setPercentValveOpening(100.0);

    ESDValve esdShutdown = new ESDValve("ESD shutdown");
    esdShutdown.setStrokeTime(2.0);

    ESDLogic esd = new ESDLogic("ESD level 1");
    esd.addAction(new TripValveAction(esdShutdown), 0.0);

    HIPPSLogic hipps = new HIPPSLogic("HIPPS-101", VotingLogic.TWO_OUT_OF_THREE);
    hipps.addPressureSensor(pressureDetector("PT-101A", pressureSetpointBara));
    hipps.addPressureSensor(pressureDetector("PT-101B", pressureSetpointBara));
    hipps.addPressureSensor(pressureDetector("PT-101C", pressureSetpointBara));
    hipps.setIsolationValve(hippsIsolation);
    hipps.linkToEscalationLogic(esd, 2.0);

    hipps.update(96.0, 96.0, 90.0);
    if (!hipps.isTripped() || hippsIsolation.getPercentValveOpening() != 0.0) {
      throw new IllegalStateException("Expected the 2oo3 vote to close HIPPS isolation");
    }

    hipps.execute(1.0);
    if (hipps.hasEscalated()) {
      throw new IllegalStateException("ESD escalated before the configured delay");
    }

    hipps.execute(1.0);
    if (!hipps.hasEscalated() || !esd.isActive()) {
      throw new IllegalStateException("Expected sustained HIPPS demand to activate ESD");
    }

    esd.execute(0.0);
    if (!esdShutdown.isClosing()) {
      throw new IllegalStateException("Expected the ESD action to demand valve closure");
    }

    logger.info(
        "HIPPS tripped: {}; ESD active: {}; ESD valve closing: {}",
        hipps.isTripped(),
        esd.isActive(),
        esdShutdown.isClosing());
  }

  /**
   * Creates one high-high pressure channel with an explicit absolute-pressure unit.
   *
   * @param name channel tag
   * @param setpointBara high-high threshold in absolute bar
   * @return configured pressure detector
   */
  private static Detector pressureDetector(String name, double setpointBara) {
    return new Detector(
        name, DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, setpointBara, "bara");
  }
}
```

The quick start verifies software state transitions only. It does not demonstrate
sensor independence, diagnostic coverage, process response, final-element
capacity, closure against differential pressure, relief adequacy, flare load,
or risk reduction.

## Engineering validation checklist

Before using these objects in a safety study:

1. Define every pressure as absolute or gauge and keep detector units explicit.
2. Derive trip settings and timing from the approved design basis; do not copy
   the example values.
3. Model the initiating event, protected inventory, valves, relief path, flare
   boundary, utilities, and failure modes that matter to the scenario.
4. Check mass and energy balance, pressure and temperature histories, valve
   travel, action timing, and the response at nearby operating points.
5. Exercise channel bypass, detected faults, unavailable actuators, delayed or
   failed final elements, and reset/restart permissives.
6. Keep software configuration evidence separate from SIL verification,
   functional-safety assessment, proof-test design, and operating approval.

## Related documentation

- [HIPPS safety logic](hipps_safety_logic.md)
- [SIS logic implementation](sis_logic_implementation.md)
- [ESD blowdown system](ESD_BLOWDOWN_SYSTEM.md)
- [Process-safety overview](../process/safety/README.md)
- [Integrated safety-chain tests](integration_safety_chain_tests.md)
