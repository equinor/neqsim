---
title: "Experimental pressure-driven pig mechanics"
description: "Event-resolved pig breakaway, stopping, restart and reversal with analytical mechanical work ledgers and explicit hydraulic-coupling limits."
---

# Experimental pressure-driven pig mechanics

`neqsim.process.equipment.pipeline.PigMotion` supplies the mechanical part of a
rigid pig model. It advances position and velocity under a **prescribed pressure
difference**, gravity, and explicitly supplied static and kinetic friction forces.
It resolves a stop inside a time step before deciding whether the pig remains
stationary or reverses. No friction coefficient is fitted or supplied by default.

This is an experimental building block for the [flowline roadmap](https://github.com/equinor/neqsim/issues/2907).
It is **not yet coupled to TwoFluidPipe** and cannot predict fluid-driven pig
arrival, upstream pressure buildup, bypass, swept liquid, receiver surges or a
safe operating pressure. A prescribed pressure schedule is an input assumption,
not a solution of the pipeline hydraulics. Mechanical restart of the pig is
distinct from the shutdown/cooldown/restart of a multiphase flowline.

## Model and units

The axial coordinate increases in the direction the user chooses. Inclination
is positive uphill. All inputs are SI; pressure differences are **Pa, not bar**.

$$F=A\Delta p-mg\sin\theta$$

For a moving pig:

$$m\frac{dv}{dt}=F-F_k\operatorname{sign}(v),\qquad\frac{dx}{dt}=v$$

Here $m$ is pig mass in kg, $A$ is the effective pressure area in m²,
$\Delta p=p_{upstream}-p_{downstream}$ in Pa, $\theta$ is inclination in radians,
$x$ is axial position in m, and $v$ is velocity in m/s.
$g=9.80665$ m/s². $F_s$ and $F_k$ are the supplied static and kinetic resistance
forces in N, with $F_s\geq F_k\geq0$.
At rest the pig remains stationary when $|F|\leq F_s$, including equality.
Otherwise it starts in the direction of $F$. A moving pig uses $F_k$, even when
the driving force is below the static breakaway threshold.

For each constant-input interval the acceleration is constant until velocity
reaches zero. The solver integrates exactly to that event and resolves the
remaining interval with the appropriate resting or reverse-motion branch.
There is no velocity clipping or artificial near-zero velocity threshold.
Divide the schedule at every imposed input change. Inclination in `simulate`
is a prescribed time history; spatial terrain/diameter crossings and receiver
contact are not applied automatically by `simulate`.

For a known axial coordinate, `advanceToPosition` returns the first crossing
within a constant-input interval and the same immutable mechanical work ledger
truncated at that event. The search treats the pre-stop and post-reversal legs
separately, so a receiver, launcher, sensor or terrain coordinate crossed before
a later reversal is not hidden by the final position. `isReached()` distinguishes
contact from expiry of the requested search horizon; `getEventTimeSeconds()` and
`getStep()` expose the event time and analytical ledger. The method locates a
position only. It does not impose impact, receipt, a diameter change or new
terrain forces; the caller owns that event response.

## Executable start, stop and restart example

The following method-body fragment uses `PigMotion` from the package above.
`PigMotionTest.documentedStartStopRestartSchedule` compiles and executes this
exact fragment, compares its text with this guide, and checks the endpoints.

```java
PigMotion pig = new PigMotion(100.0, 0.01, 100.0, 50.0);
PigMotion.Trajectory result = pig.simulate(0.0, 0.0,
    new double[] {2.0, 10.0, 1.0},
    new double[] {20000.0, 0.0, 25000.0},
    new double[] {0.0, 0.0, 0.0});
```

The input pig mass is 100 kg, effective pressure area 0.01 m², breakaway force
100 N and moving resistance 50 N. These are synthetic example parameters.
The three horizontal intervals apply +20,000 Pa for 2 s, zero differential
pressure for 10 s, then +25,000 Pa for 1 s.

| Elapsed time (s) | Position (m) | Velocity (m/s) | Interpretation |
| ---: | ---: | ---: | --- |
| 0 | 0 | 0 | Initial rest |
| 2 | 3 | 3 | Acceleration at 1.5 m/s² |
| 12 | 12 | 0 | Stops at elapsed 8 s, then remains stationary |
| 13 | 13 | 2 | Restarts at 2 m/s² |

`getTimesSeconds`, `getPositionsM` and `getVelocitiesMps` return defensive
arrays containing the initial state and interval endpoints. `getSteps` returns
immutable interval ledgers. Java/JPype callers can use the same constructor and
`advance`/`simulate` signatures; no separate Python implementation is needed.

## Work ledger and numerical evidence

Each step exposes pressure work, gravity work, nonnegative friction heat,
kinetic energy change, travelled distance, moving time and the first stop time.
`getStopTimeSeconds` is relative to the interval start; -1 means no
moving-to-rest event. An initially stationary pig does not itself count as a stop event.

$$\Delta K=A\Delta p\,\Delta x-mg\sin\theta\,\Delta x-F_k\int|v|\,dt$$

The friction term uses **total travelled distance**, including both legs of a
reversal, rather than the absolute net displacement. `getEnergyResidualJ`
returns kinetic energy change minus pressure and gravity work plus friction
heat. Its roundoff-level value verifies this mechanical model only; it is not
a fluid or combined fluid/pig energy balance. Friction heat is reported but
not deposited into a fluid or pipe wall.

Tests cover analytical acceleration and stopping distance, threshold equality,
reverse motion, vertical gravity balance, frictionless motion, zero-duration
identity, first position contact on both legs of a reversal, absent events,
invalid/overflow inputs, serialization and deterministic replay.
A fixed 200-case force/velocity/inclination matrix compares one 7 s interval
with 70 intervals of 0.1 s and checks sign symmetry and the work balance.
This is analytical and numerical verification, not validation against pigging
experiments or a commercial simulator.

## Public sensor-timing evidence

`PigMotionPositionEventTest.limaPublicSensorTimingBenchmark` reproduces the
published event arithmetic from Lima et al., *Sensors* 17 (2017) 2119,
[doi:10.3390/s17092119](https://doi.org/10.3390/s17092119). The article and its
figures are available under CC BY 4.0. The reported pressure-sensor spacing is
5.90 m and the pressure-event separation is 272 samples at 50 ms, or 13.6 s.
The executable test advances 272 prescribed 50 ms intervals, verifies that the
sensor position is first reached at sample 272, and compares the endpoint with
one 13.6 s interval and the first-position-event API to retain the existing
partition-invariance contract. Its
sample detector permits only `1e-12` m of accumulated floating-point roundoff,
more than ten orders of magnitude below one sample's travel.

The derived supervisory speed is 5.90 m / 13.6 s = 0.4338 m/s, reported as
0.43 m/s. The paper's odometer values are 5.70 m in 12.53 s, or 0.4549 m/s,
reported as 0.45 m/s. The test retains the publication's two-decimal reporting
and its stated 4.44% difference between 0.43 and 0.45 m/s. The 50 ms sample
period is the timing resolution; it is not a fitted mechanics parameter.

This evidence validates sampled-trajectory arithmetic and timing interpretation
only. A frictionless prescribed-velocity case is used deliberately because the
publication does not provide a redistributable pressure-force/bypass time
series suitable for fitting this model. The test therefore does **not** qualify
pressure-force closure, seal friction, gas or liquid bypass, swept-liquid slug,
receiver surge, or coupled two-fluid pigging.

## Integration and remaining qualification

- Preserve this stateless calculation during rejected hydraulic stages; publish
  state only after the coupled step is accepted through the existing transaction
  mechanism. Returned model and trajectory objects are serializable.
- Use `advanceToPosition` to split a constant-input stage at a known axial
  boundary. Apply launcher/receiver contact, terrain, diameter or fitting physics
  explicitly after the returned event; the mechanics API does not invent them.
- A future moving-boundary model must use the same pig displacement on the
  upstream and downstream fluid control volumes, and balance pressure work,
  mass, species, momentum, friction heat and bypass consistently. Effective
  pressure area alone does not prescribe a displacement area or bypass law.
- Add conservative launcher/receiver and geometry events, compressible/choked
  gas bypass and liquid bypass, seal leakage, stall/release, liquid-slug
  transport and residual-film inventory before claiming coupled pigging.
- Supply measured friction and seal/area data with pressure, temperature,
  lubrication and wear applicability. Constant friction does not represent
  bends, fittings, contact deformation, variable seal loads or gelled-fluid yield.
- Continue physical line shutdown/cooldown/restart and slugging qualification
  through [#3298](https://github.com/equinor/neqsim/issues/3298), canonical SURF
  integration through [#4228](https://github.com/equinor/neqsim/issues/4228), and
  post-pig MEG/water films through [#4281](https://github.com/equinor/neqsim/issues/4281).
- Agent routing: inspect the Java API with `inspectApi`, then construct the
  model from Java/JPype when explicitly requested. This registration is discovery
  evidence only. `runPipeline` does not execute a coupled pigging simulation,
  and no supported `runCapability` operation is declared for this submodel.

Public sources for subsequent coupled-model qualification include Hendrix et al.,
[Experiments and modeling of by-pass pigging under low-pressure conditions](https://www.sciencedirect.com/science/article/pii/S0959152418302774),
and [Transient pigging dynamics in gas pipelines: Models, experiments, and simulations](https://www.sciencedirect.com/science/article/pii/S0029801821005618).
No experimental dataset from these publications is included or claimed passed
by the current mechanics tests.

## Coupled ideal-gas control-volume model

`IdealGasPiggingModel` couples the event-resolved mechanics to two well-mixed,
calorically perfect gas inventories. The pig pressure area is also the swept
pipe area. A positive displacement expands the upstream control volume and
contracts the downstream volume by the same amount. Beginning-of-substep
pressures drive both the mechanical force and the equal-and-opposite gas
boundary work. `IdealGasReleaseModel` supplies bidirectional, compressible
short-orifice bypass and the critical-pressure transition. No one-way flow
clip or fitted pressure trace is used.

The accepted ledger exposes total mass and combined gas/pig/potential-energy
residuals, pressure and boundary work, gravity work, friction heat, contact
impact, signed and absolute bypass mass, choked time, internal-step count and
the largest accepted donor-mass and pig-travel fractions. State is immutable;
callers publish `getFinalState()` only after accepting the complete step.

The 62 m length and 52 mm bore in the executable example are public geometry
reported by Hendrix et al. All pressures, friction, pig mass, bypass diameter,
dead volumes and discharge coefficient below are explicitly synthetic. The
example is therefore conservation and refinement evidence, not experimental
validation of that campaign.

<!-- COUPLED_EXAMPLE -->
```java
SystemInterface gas = new SystemSrkEos(293.15, 5.0);
gas.addComponent("methane", 1.0);
gas.setMixingRule(2);
PigMotion pig = new PigMotion(10.0, Math.PI * 0.052 * 0.052 / 4.0, 25.0, 15.0);
IdealGasPiggingModel model = new IdealGasPiggingModel(pig, gas, 62.0, 0.05, 0.05, 0.003, 0.7, 0.0,
    0.01, 0.01, 0.05);
IdealGasPiggingModel.State state = model.initialise(0.0, 0.0, 3.0e5, 1.5e5, 293.15);
IdealGasPiggingModel.Step result = model.advance(state, 2.0);
```

The model resolves launcher retention/release and receiver contact, transfers
contact kinetic energy into an explicit impact-dissipation ledger, and stops
the trajectory after receipt. Its boundary locator now reuses the public
first-position-event API, retaining the same reversal-safe analytical mechanics
path used by standalone callers. It adaptively subdivides a requested interval to
respect user-declared maximum time, donor-mass fraction and pig-travel fraction.
These are numerical safeguards; they do not represent leakage, seal wear or a
vendor pig specification.

The scope remains narrow. Two uniform gas chambers do not represent distributed
pressure waves, pipe-wall heat transfer, line friction, bends, liquid bypass,
swept-liquid slugs, residual film, receiver liquid capacity, multiphase
`TwoFluidPipe` moving boundaries, gelled-oil restart or pig trains. The Hendrix
pressure/trajectory trace is not redistributed here, so the public dimensions
must not be reported as experimental trajectory qualification. Those missing
physics and an independently reusable validation trace are the concrete split
boundary for the next qualification stage.
