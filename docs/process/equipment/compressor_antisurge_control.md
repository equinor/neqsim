---
title: Compressor Anti-Surge and Coordinated Control
description: Guide to compressor anti-surge protection, speed/load control, recycle override, and coordinated pressure-speed-recycle control examples in NeqSim.
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

This distinction is important because steady-state anti-surge and dynamic anti-surge are different problems. `AntiSurgeCalculator` is an algebraic steady-state helper that adjusts recycle split flow during `process.run()`. `AntiSurgeController` is a transient controller that changes the recycle valve opening during `runTransient()` based on the compressor surge margin, optional prediction, and actuator dynamics.

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