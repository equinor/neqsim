---
title: Safety Systems Package
description: Current NeqSim safety-equipment, ESD, HIPPS, relief, and transient-simulation API boundaries.
---

NeqSim provides stream-connected safety equipment, sequenced process logic, scenario runners,
and screening utilities. These layers serve different purposes and should not be presented as
one universal safety-design API.

Safety calculations are engineering evidence, not an approval. A project must still establish
credible scenarios, design conditions, standards editions, safeguard independence, uncertainty,
acceptance criteria, and accountable review.

## Choose the correct layer

| Need | Current API | Boundary |
| --- | --- | --- |
| Model pressure-responsive or fail-safe equipment | `SafetyValve`, `RuptureDisk`, `ESDValve`, `BlowdownValve` | Constructors accept a `StreamInterface`, not a vessel object |
| Sequence shutdown actions | `ESDLogic` with `TripValveAction`, `ActivateBlowdownAction`, and related actions | Call `activate()` and advance both logic and equipment with the same time-step basis |
| Model HIPPS voting and final action | `HIPPSLogic`, `Detector`, and `VotingLogic` | Sensor quality, bypasses, proof testing, SIL claims, and independence require separate evidence |
| Produce structured transient evidence | `EmergencyShutdownTestRunner`, `DynamicSafetyScenarioRunner`, and `ClosedLoopSafetyFunction` | Define monitored tags, criteria, calculation identity, and result retention explicitly |
| Screen relief load and area | `ReliefValveSizing` and the scenario definitions on `SafetyValve` | Static sizing is separate from the valve's dynamic opening and reseating behavior |
| Hand off consequence inputs | Release, dispersion, open-drain, flare, and CFD source-term utilities | Layout, weather, leak frequency, escalation, and QRA conclusions remain external qualification tasks |

## Units and state ownership

| API | Input meaning |
| --- | --- |
| `SafetyValve.setPressureSpec(double)` | Absolute set pressure in bara |
| `SafetyValve.setFullOpenPressure(double)` | Absolute fully-open pressure in bara |
| `SafetyValve.setBlowdown(double)` | Reseating margin as percent below set pressure |
| `RuptureDisk.setBurstPressure(double)` | Absolute burst pressure in bara |
| `RuptureDisk.setFullOpenPressure(double)` | Absolute fully-open pressure in bara |
| `ESDValve.setStrokeTime(double)` | Closure time in seconds |
| `BlowdownValve.setOpeningTime(double)` | Opening time in seconds |
| `runTransient(double, UUID)` | Time increment in seconds plus a stable calculation identity |

The valve classes inherit unit-aware outlet-pressure methods from `ThrottlingValve`, for example
`setOutletPressure(1.5, "bara")`. The device-specific set, burst, and full-open setters above do
not accept a unit string. Convert project gauge-pressure values to absolute pressure before using
those setters.

`RuptureDisk.reset()` exists to reset a simulation case. It does not imply that a ruptured physical
disk can reseat or be returned to service.

## Executable Java 8 quick start

This example configures pressure-protection devices and executes a fail-close/fail-open ESD
sequence. It demonstrates API behavior; it does not size a relief device or validate a safety
function.

```java
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.RuptureDisk;
import neqsim.process.equipment.valve.SafetyValve;
import neqsim.process.logic.action.ActivateBlowdownAction;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class ProcessSafetyOverviewQuickStart {
  private static final Logger logger =
      LogManager.getLogger(ProcessSafetyOverviewQuickStart.class);

  private ProcessSafetyOverviewQuickStart() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 80.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("safety feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    SafetyValve safetyValve = new SafetyValve("PSV-100", feed);
    safetyValve.setPressureSpec(75.0);
    safetyValve.setFullOpenPressure(82.5);
    safetyValve.setBlowdown(7.0);
    safetyValve.setOutletPressure(1.5, "bara");

    RuptureDisk ruptureDisk = new RuptureDisk("RD-100", feed);
    ruptureDisk.setBurstPressure(85.0);
    ruptureDisk.setFullOpenPressure(89.25);
    ruptureDisk.setOutletPressure(1.5, "bara");

    ESDValve inletIsolation = new ESDValve("ESD-XV-100", feed);
    inletIsolation.setStrokeTime(2.0);
    inletIsolation.setCv(500.0);
    inletIsolation.setOutletPressure(75.0, "bara");
    inletIsolation.setCalculateSteadyState(false);
    inletIsolation.energize();

    BlowdownValve blowdownValve = new BlowdownValve("BDV-100", feed);
    blowdownValve.setOpeningTime(2.0);
    blowdownValve.setCv(100.0);
    blowdownValve.setOutletPressure(1.5, "bara");
    blowdownValve.setCalculateSteadyState(false);

    ESDLogic esdLogic = new ESDLogic("ESD level 1");
    esdLogic.addAction(new TripValveAction(inletIsolation), 0.0);
    esdLogic.addAction(new ActivateBlowdownAction(blowdownValve), 0.0);
    esdLogic.activate();

    UUID calculationId = UUID.randomUUID();
    double timeStepSeconds = 0.5;
    for (int step = 0; step < 20 && !esdLogic.isComplete(); step++) {
      esdLogic.execute(timeStepSeconds);
      inletIsolation.runTransient(timeStepSeconds, calculationId);
      blowdownValve.runTransient(timeStepSeconds, calculationId);
    }

    if (!esdLogic.isComplete()
        || inletIsolation.getPercentValveOpening() > 1.0
        || blowdownValve.getPercentValveOpening() < 90.0
        || Math.abs(safetyValve.getBlowdownPressure() - 69.75) > 1.0e-9
        || ruptureDisk.getBurstPressure() != 85.0) {
      throw new IllegalStateException("Safety-equipment sequence did not complete");
    }

    logger.info(
        "ESD complete: inlet opening {}%, blowdown opening {}%",
        inletIsolation.getPercentValveOpening(),
        blowdownValve.getPercentValveOpening());
  }
}
```

The regression test
`src/test/java/neqsim/process/safety/ProcessSafetyOverviewDocumentationTest.java` executes this
sequence and protects the documented constructors, units, state transitions, and completion
criteria.

## Transient execution contract

1. Initialize and run the upstream thermodynamic stream or steady-state process.
2. Set stateful safety equipment to transient mode where its automatic travel logic is required.
3. Activate the ESD or HIPPS logic explicitly; construction alone does not trigger it.
4. Advance logic and equipment using a justified time step and a stable `UUID` for the calculation.
5. Capture pressure, temperature, inventory, valve position, relief flow, flare load, MDMT or
   hydrate margin, and acceptance-criterion results in structured evidence.
6. Check conservation, numerical convergence, event ordering, time-step sensitivity, and the
   final safe state before interpreting the scenario.

A valve connected to a stream is not a complete depressuring model. The protected inventory,
equipment holdup, heat transfer, flare-header backpressure, downstream equipment, control logic,
and scenario boundary conditions must also be represented.

## Relief and fire screening boundary

Use [Relief-Valve Sizing Screening](../../safety/relief_valve_sizing_api.md) for the maintained
gas, liquid, two-phase, and wetted-fire sizing interfaces. Do not attach an undocumented heat
input to an arbitrary vessel or infer an API 526 letter from `SafetyValve`; the dynamic equipment
class does not expose those sizing methods.

Use [Integrated Facility Safety Response](integrated-facility-safety-response.md) when relief,
blowdown, flare, compressor trip, process limits, MDMT, and hydrate margin must be reviewed as one
scenario. Static relief sizing and dynamic transient response answer different questions and
should both retain method, unit, input-basis, and qualification metadata.

## Safety lifecycle boundary

NeqSim can calculate and retain evidence, but it does not:

- decide which initiating events or safeguards are credible;
- certify IEC 61511 independence, SIL capability, proof-test coverage, or bypass policy;
- replace API, ISO, NORSOK, company, vendor, or authority requirements;
- approve relief loads, flare-network capacity, depressuring time, MDMT, hazardous-area extent,
  fire protection, QRA, HAZOP, LOPA, or an SRS; or
- make a model fit for design or operation without project-specific verification and accountable
  review.

Avoid fixed ESD-level meanings or universal depressuring-time targets. Define them in the project
basis and trace each acceptance criterion to the applicable controlled source.

## Related documentation

- [Safety documentation index](../../safety/README.md)
- [Relief-Valve Sizing Screening](../../safety/relief_valve_sizing_api.md)
- [ESD Dynamic Testing Workflow](../../safety/esd_testing_workflow.md)
- [HIPPS overview](../../safety/HIPPS_SUMMARY.md)
- [Closed-loop SIF verification](closed-loop-sif-verification.md)
- [SIF reliability and degraded modes](sif-reliability-and-degraded-modes.md)
- [HAZOP/LOPA to draft SRS handoff](hazop-lopa-srs-handoff.md)
- [Integrated facility safety response](integrated-facility-safety-response.md)
- [Safety change revalidation and benchmarks](safety-change-revalidation-and-benchmarks.md)
- [Release and dispersion scenarios](release-dispersion-scenarios.md)
- [Valve equipment guide](../equipment/valves.md)
- [Process package overview](../README.md)
