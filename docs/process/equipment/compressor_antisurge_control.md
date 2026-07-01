---
title: Compressor Anti-Surge and Coordinated Control
description: Guide to compressor anti-surge protection, speed/load control, recycle override, coordinated pressure-speed-recycle control, and anti-surge application design examples in NeqSim.
---

# Compressor Anti-Surge and Coordinated Control

This guide explains how NeqSim represents compressor operating-envelope protection and coordinated compressor control in the dynamic compressor map example.

The focus is educational and simulation-oriented. The models illustrate common control philosophy patterns used for centrifugal compressors; they are not a replacement for vendor-certified compressor controls, machinery protection systems, or site-specific control narratives.

## Control Objectives

A centrifugal compressor control scheme normally has two different objectives:

| Objective | Typical manipulated variable | Purpose |
|-----------|------------------------------|---------|
| Process performance | Compressor speed, inlet guide vanes, or another load-control handle | Meet discharge pressure, suction pressure, flow, or header demand efficiently |
| Compressor protection | Anti-surge recycle valve | Keep the operating point to the safe side of the surge control line |

The anti-surge action is protective. It should override normal process-performance control when the compressor approaches the surge line. Recycle flow consumes power and reduces forward throughput, so the normal operating strategy is to keep recycle closed unless protection or minimum-flow handling requires it.

## Operating Envelope

The compressor map defines:

- speed curves: head and efficiency as functions of actual inlet flow,
- a surge curve on the low-flow side,
- a stonewall or choke curve on the high-flow side,
- the current operating point from flow, head, speed, and gas state.

NeqSim reports distance-to-surge with `Compressor.getDistanceToSurge()`. Positive distance means the point is to the safe side of the surge curve; negative distance means the point has crossed into the surge region for the configured map.

For dynamic studies, the important trend is not only the instantaneous distance, but also how fast the operating point is moving toward the surge line. The newer anti-surge controller includes a predictive option that uses a filtered margin rate and a short look-ahead horizon:

$$
m_\mathrm{pred} = m + \dot{m}_f \Delta t_\mathrm{pred}
$$

where $m$ is current surge margin, $\dot{m}_f$ is the filtered margin rate, and $\Delta t_\mathrm{pred}$ is the prediction horizon.

## Coordinated Control Philosophy

The dynamic compressor map notebook demonstrates three levels of control behavior:

| Case | Behavior |
|------|----------|
| Fixed-speed turndown | Feed reduction moves the operating point left toward surge on one speed line. |
| Pressure and anti-surge control | Speed changes to meet a pressure target, while recycle opens when surge margin is low. |
| Coordinated pressure-speed-recycle control | Anti-surge protection has priority: speed integration is held back during recycle action, speed can run back, and recycle opens faster to preserve surge margin. |

The coordinated case is the closest representation of plant control philosophy in the example. The performance loop still manipulates speed, but it does not blindly increase speed while the anti-surge loop is opening recycle. This prevents the pressure controller from fighting the protective recycle action.

## Example Notebook

The executable example is:

- [Dynamic compressor good maps notebook](../../../examples/notebooks/process/dynamic_compressor_good_maps.ipynb)

The notebook demonstrates:

- explicit multi-speed compressor maps,
- surge and stonewall curves installed in the compressor chart,
- mapped speed-step and flow-turndown trajectories,
- an anti-surge recycle sweep,
- a dynamic pressure plus anti-surge response with speed control,
- a coordinated pressure-speed-recycle comparison,
- predictive anti-surge supervision using the Java `AntiSurgeController` benchmark,
- a practical handoff from a solved steady-state recycle model to a dynamic anti-surge run.

The coordinated comparison intentionally plots both an independent-loop case and a coordinated-loop case. In the executed example, the independent loops cross the surge line during deep turndown, while the coordinated case uses earlier recycle action and speed runback to keep a positive minimum surge margin.

## From Steady State to Dynamic Mode

Use steady state to initialize the compressor train before starting a transient anti-surge study. The topology should be the same in both modes: suction valve or boundary, suction volume, compressor with map, discharge cooling or volume as needed, splitter, recycle valve, recycle stream, and outlet valve or boundary. The mode change is a handoff from a converged initial condition to time stepping; it is not a different process model.

The recommended sequence is:

1. Build the compressor recycle topology and install the compressor map, surge curve, and stonewall curve.
2. Run the flowsheet with `process.run()` to converge pressures, flows, recycle state, and the compressor operating point.
3. Record or check the initial point with `getDistanceToSurge()`, compressor inlet flow, head, speed, recycle valve opening, and discharge pressure.
4. Set the dynamic timestep with `process.setTimeStep(...)`.
5. Switch supported dynamic equipment to `setCalculateSteadyState(false)`. Typical candidates are valves, separators or volumes, compressor, cooler, and splitter.
6. Keep algebraic convergence helpers such as `Recycle` in steady-state mode unless the specific class has transient state support. The recycle block should settle the loop at each timestep; the valve and equipment holdups carry the dynamics.
7. Attach the anti-surge controller to the recycle valve and run with `process.runTransient()`.

This distinction is important because steady-state anti-surge and dynamic anti-surge are different problems. `AntiSurgeRecycleCalculator` is the preferred steady-state helper for a charted compressor when you want a cooled recycle stream mixed back into suction and solved to a configured surge-control margin. The older `AntiSurgeCalculator` is an algebraic splitter-based helper that adjusts recycle split flow during `process.run()`. `AntiSurgeController` is a transient controller that changes the recycle valve opening during `runTransient()` based on the compressor surge margin, optional prediction, and actuator dynamics.

## Advanced Screening and Fault-Tolerant Logic

NeqSim also provides `AdvancedAntiSurgeControlSystem` for public, screening-level implementations of the more advanced anti-surge ideas often described in compressor-control literature. It is intentionally separate from the basic dynamic `AntiSurgeController` so a normal process model can keep the simple PI/recycle-valve loop, while studies that need more instrumentation and supervisory behavior can opt in.

The class includes:

| Feature | API entry point | Use |
|---------|-----------------|-----|
| Reduced anti-surge coordinates | `calculateReducedCoordinates(...)` and `distanceToReducedControlLine(...)` | Represent the operating point in pressure-normalized flow/head coordinates before comparing against a surge-control line. |
| Fan-law invariant map coordinates | `calculateInvariantMapPoint(...)` | Compare operating points across speed lines using reduced flow and reduced head. |
| Transmitter range, lag, and faults | `InstrumentSignal` | Model filtered transmitter readings with range validation and stuck, bias, drift, or invalid faults. |
| Sensor voting | `vote(...)` | Combine redundant signals by median, average, select-low, or select-high voting and report degraded status when signals disagree or fail validation. |
| Fault-tolerant supervisory decision | `evaluateFaultTolerant(...)` | Vote redundant surge-margin signals, fall back to a conservative valve opening when the vote is degraded, and force recycle open when no valid signal remains. |
| Recycle valve and piping-volume screening | `sizeRecycleSystem(...)` | Estimate required recycle flow, a screening valve coefficient, and whether recycle piping volume can respond within a target time. |
| Dual hot/cold recycle command split | `splitDualRecycleCommand(...)` | Keep the normal cold recycle path active for sustained control and open a fast hot recycle command during low-margin or fast-approach events. |
| Advanced control policies | `setAlgorithm(...)` with `PI`, `PREDICTIVE_PI`, `FUZZY_SCREENING`, or `MPC_SCREENING` | Compare classical PI, predicted-margin PI, rule-based fuzzy screening, and one-step screening MPC valve demand. |
| Surge oscillation screening | `stepSurgeOscillation(...)` | Run a lumped two-state oscillation indicator for educational controller tests. |

Example supervisory calculation:

```java
AdvancedAntiSurgeControlSystem supervisor = new AdvancedAntiSurgeControlSystem();
supervisor.setAlgorithm(AdvancedAntiSurgeControlSystem.ControlAlgorithm.MPC_SCREENING);
supervisor.setMarginSetPoint(0.10);
supervisor.setPredictionHorizon(3.0);

AdvancedAntiSurgeControlSystem.InstrumentSignal signalA =
	new AdvancedAntiSurgeControlSystem.InstrumentSignal("ASC margin A", -1.0, 1.0, 0.5);
AdvancedAntiSurgeControlSystem.InstrumentSignal signalB =
	new AdvancedAntiSurgeControlSystem.InstrumentSignal("ASC margin B", -1.0, 1.0, 0.5);
signalA.update(compressor.getDistanceToSurge(), dt);
signalB.update(compressor.getDistanceToSurge(), dt);

AdvancedAntiSurgeControlSystem.FaultTolerantDecision decision =
	supervisor.evaluateFaultTolerant(
		Arrays.asList(signalA, signalB),
		AdvancedAntiSurgeControlSystem.VotingMode.SELECT_LOW,
		marginRate,
		dt);
recycleValve.setPercentValveOpening(decision.getValveOpening());
```

The advanced class remains a simulation and screening layer. `getCertificationStatus()` returns `NOT_VENDOR_CERTIFIED` by design. Use it to test logic, demonstrate control philosophy, compare algorithms, and build defensible example studies; do not use it as a certified machinery-protection package.

## Application-Design Layer

`CompressorAntiSurgeApplication` wraps the lower-level anti-surge functions into an application-style simulation object. Use it when the study needs more than a single controller block: multiple compressor stages, shared suction or discharge headers, hot and cold recycle paths, startup and shutdown sequencing, commissioning checks, scan-cycle execution, and operator-facing diagnostics.

The class is intentionally deterministic and data-driven. It can be used with measured or simulated scan inputs, and stages may optionally reference real `Compressor` objects from a process model. The scan result reports stage decisions, header decisions, recycle-valve commands, diagnostics, recommendations, sequence state, and certification status.

Key concepts:

| Capability | API entry point | What it represents |
|------------|-----------------|--------------------|
| Multiple stages | `addStage(...)` | Stage-level anti-surge supervision for each compressor. |
| Shared headers | `addHeader(...)` | Header coordination using the most limiting connected compressor margin. |
| Hot/cold recycle split | `StageDecision.getValveCommand()` | Fast hot recycle for low-margin or fast-approach events, normal cold recycle for sustained control. |
| Startup sequencing | `startStartupSequence()` and `scan(...)` | Permissive check, recycle pre-open, pressurization, speed ramp, load acceptance, and normal control state progression. |
| Shutdown and trip behavior | `startShutdownSequence()` and `forceTripMode()` | Conservative recycle opening during shutdown or trip/coastdown simulation. |
| Commissioning checks | `runCommissioningChecks()` | Stage configuration, recycle response, valve stroke, transmitter redundancy, header coordination, and non-certified boundary statement. |
| Real-time/advisory scan | `scan(ScanInput, dt)` | One deterministic application scan cycle for digital twin, operator-training, or dynamic simulation studies. |
| Diagnostics | `ScanResult.getDiagnostics()` | Alarm/degraded/trip-demand codes with operator recommendations. |

Example application scan:

```java
CompressorAntiSurgeApplication application = new CompressorAntiSurgeApplication("export compression");
CompressorAntiSurgeApplication.StageApplication lpStage = application.addStage("LP");
CompressorAntiSurgeApplication.StageApplication hpStage = application.addStage("HP");

lpStage.setDesignBasis(11000.0, 9000.0, 30.0);
hpStage.setDesignBasis(9000.0, 8800.0, 40.0);
application.addHeader("shared suction", "LP", "HP");
application.setRunningMode();

CompressorAntiSurgeApplication.ScanInput scanInput = new CompressorAntiSurgeApplication.ScanInput();
scanInput.putStageInput("LP", new CompressorAntiSurgeApplication.StageScanInput(
	0.18, 0.0, 11000.0, 9000.0, 30.0));
scanInput.putStageInput("HP", new CompressorAntiSurgeApplication.StageScanInput(
	0.03, -0.01, 9000.0, 8800.0, 40.0));

CompressorAntiSurgeApplication.ScanResult result = application.scan(scanInput, 0.25);
CompressorAntiSurgeApplication.StageDecision hpDecision = result.getStageDecisions().get(1);
double hotRecycleOpening = hpDecision.getValveCommand().getHotValveOpening();
double coldRecycleOpening = hpDecision.getValveCommand().getColdValveOpening();
```

The application layer does not replace `AntiSurgeController` in a transient process model. A practical pattern is to use `CompressorAntiSurgeApplication.scan(...)` as the supervisory scan and then write the returned valve command to the actual recycle valve model. For simple dynamic examples, `AntiSurgeController` can still be attached directly to the recycle valve. For studies that need stage coordination, startup states, hot/cold recycle split, diagnostics, or commissioning evidence, the application layer provides the extra supervisory context.

Certification remains outside the open simulation model. `CompressorAntiSurgeApplication.getCertificationStatus()` returns `NOT_CERTIFIED_FOR_PROTECTION`; `runCommissioningChecks()` creates evidence useful for review and testing, but it does not certify the logic as a safety instrumented function or machinery protection package.

## Practical Modeling Notes

- Use vendor-certified compressor maps for design work. The notebook map is illustrative.
- Treat the surge control line as a margin above the physical surge curve, not the physical surge curve itself.
- Attach control action to the equipment that owns the dynamic state where practical. For example, anti-surge valve control belongs on the recycle valve, and speed/load control belongs on the compressor or the surrounding control layer.
- Use rate limits and actuator lag in dynamic examples. Instantaneous recycle opening hides the control problem.
- Use anti-windup or override logic when recycle opens. Otherwise the speed or pressure controller can accumulate demand that works against protection.
- Keep the distinction clear between process control and machinery protection. Process control optimizes operation; anti-surge protection preserves compressor operability.

## Related Documentation

- [Compressor equipment](compressors)
- [Compressor curves and performance maps](compressor_curves)
- [Dynamic simulation](../dynamic-simulation)
- [Control valves](control_valves)
- [Recycles](util/recycles)