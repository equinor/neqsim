---
title: "CompressorShaft: Multiple Compressor Bodies on One Shaft"
description: "Model a multi-body compressor string driven by a single gas turbine or motor at one common speed with CompressorShaft. Covers the degrees-of-freedom rule, iterating the common speed to the final discharge (intermediate pressures float), the process-integrated CompressorShaftCalculator that converges the shaft speed inside process.run(), shared pressure nodes, fixed-speed drivers, single-body use, and coexistence with anti-surge control."
---

# CompressorShaft: Multiple Compressor Bodies on One Shaft

`neqsim.process.equipment.compressor.CompressorShaft` groups several
`Compressor` bodies that sit on **one driver shaft** (a single gas turbine or
motor, often through a gearbox) so they all turn at the **same speed**. A
2- or 3-body recompression string on one turbine is the classic case.

Use it whenever more than one compressor body shares a driver. Do **not** let
each body solve its own speed independently — that produces different speeds for
machines that are physically locked together.

## Degrees of Freedom (the rule that matters)

A shared shaft has exactly **one** mechanical degree of freedom — the common
speed — and exactly **one** controlled target: the string's **final discharge
pressure**. Every intermediate inter-body pressure is a *result*, not a spec,
and must be allowed to **float** off the charts.

| Approach | DOF accounting | Verdict |
|----------|----------------|---------|
| Fix every stage outlet pressure **and** set a common speed | over-constrained (N pressures + 1 speed vs 1 real DOF) | Non-physical |
| Adjust ONE common speed to hit the final discharge; intermediates float | 1 variable, 1 target | Correct |

Violating this rule (fixing all interstage pressures while also imposing a
common speed) forces the charts to satisfy more constraints than the machine
has freedoms, which is not physical.

## Iterating the Common Speed

`solveSpeed` puts every body into fixed-speed, chart-forward mode and solves the
common speed with a bracketed **false-position (Illinois) secant** until the
reference (usually last) body's discharge matches the target. The surrounding
flowsheet is re-solved between speed guesses through a caller-supplied `Runnable`,
so inter-body streams, suction scrubbers and mixers update each iteration.

```java
CompressorShaft shaft = new CompressorShaft("23-KA recompression shaft (single GT)");
shaft.addCompressor(rc1); // LP body (lowest suction)
shaft.addCompressor(rc2);
shaft.addCompressor(rc3); // HP body = reference
shaft.setSpeedBounds(8000.0, 16000.0);
shaft.setMaxIterations(20);
shaft.setPressureTolerance(0.3); // bar

// One common speed -> 49 bara at the HP discharge; intermediates float:
shaft.solveSpeed(rc3, 49.0, "bara", new Runnable() {
  @Override
  public void run() {
    process.run();
  }
});

double rpm = shaft.getSpeed();
double shaftPowerW = shaft.getTotalPower();
```

Discharge pressure rises monotonically with speed on a centrifugal map, so the
solver brackets the root at the speed bounds and then converges superlinearly.
Each iteration re-solves the whole flowsheet, so it is the dominant cost: keep
the iteration budget modest (`setMaxIterations`) and the tolerance loose
(`setPressureTolerance`). For a large multi-area plant, run the shaft solve as an
**opt-in** step (each shaft costs several full-field solves), and solve each
train's shaft in turn.

### Fixed common speed (no iteration)

If you already know the shaft speed, apply it directly. Each body then produces
its discharge from its chart at that speed (intermediate pressures float):

```java
shaft.setSpeed(11799.0); // applies to every body, fixed-speed chart-forward mode
process.run();
```

## Process-Integrated Speed Control: `CompressorShaftCalculator`

`solveSpeed` re-solves the **whole flowsheet** several times through the
caller-supplied `Runnable`, so on a large multi-area plant it costs *N* full-field
solves per shaft. `neqsim.process.equipment.util.CompressorShaftCalculator` is the
process-integrated alternative: like `AntiSurgeCalculator`, it is a `Calculator`
that you `add` to the `ProcessSystem`, and it converges the common speed
**inside** the normal `process.run()` iteration. On every internal pass it takes
one damped secant step on the common speed toward the reference body's target
discharge, so the shaft speed converges **together with** the recycle loops in a
single `run()` — no separate full-field re-solves.

Prefer this over `solveSpeed` when the shaft sits in a plant that already
iterates recycles, or when you want the shaft to re-converge automatically every
time you re-run the process.

```java
CompressorShaft shaft = new CompressorShaft("23-KA train");
shaft.addCompressor(rc1);
shaft.addCompressor(rc2);
shaft.addCompressor(rc3); // reference (last body)

// Wire the calculator: reference body rc3, target 49 bara discharge.
// Pass null as the reference to default to the last body added to the shaft.
CompressorShaftCalculator shaftCalc =
    new CompressorShaftCalculator("23-KA shaft speed", shaft, rc3, 49.0, "bara");
shaftCalc.setSpeedBounds(8000.0, 16000.0); // clamp the search
shaftCalc.setMaxStepFraction(0.10);        // max 10% speed change per pass (damping)

process.add(shaftCalc); // add AFTER the compressor bodies and their anti-surge loops
process.run();          // shaft speed converges with the recycles in one run()

double rpm = shaftCalc.getSpeed();
```

How it converges: higher speed raises discharge on a centrifugal map, so the
calculator drives the reference outlet-pressure error to zero. The first pass (or
a stalled secant) takes a proportional bump; subsequent passes take a secant step
on the *speed → error* relation. Every step is damped by `maxStepFraction` and
clamped to `[minSpeed, maxSpeed]` so it stays stable inside the recycle
iteration. Each pass puts every body into fixed-speed, chart-forward mode
(`setSolveSpeed(false)`, common `setSpeed`, `setUseCompressorChart(true)`,
`setUsePolytropicCalc(true)`) — the intermediate inter-body pressures float, as
required by the degrees-of-freedom rule above.

**Ordering:** add the `CompressorShaftCalculator` **after** the compressor bodies
and their anti-surge calculators/recycles so the shaft speed is stepped once the
body states and recycle flows for that pass are current.

<table>
<caption>solveSpeed vs CompressorShaftCalculator</caption>
<tr><th>Aspect</th><th><code>shaft.solveSpeed(...)</code></th><th><code>CompressorShaftCalculator</code></th></tr>
<tr><td>Where it runs</td><td>Stand-alone, re-runs the flowsheet via a callback</td><td>Inside <code>process.run()</code> as a <code>Calculator</code></td></tr>
<tr><td>Cost</td><td><em>N</em> full-field solves per shaft</td><td>One <code>run()</code> — converges with the recycles</td></tr>
<tr><td>Root finder</td><td>Bracketed false-position (Illinois)</td><td>Damped secant, one step per internal pass</td></tr>
<tr><td>Best for</td><td>One-off / opt-in shaft solve</td><td>Plants that already iterate; auto re-convergence</td></tr>
</table>

## When the Target Pressure Is Not Reachable

A single common speed (and, even more so, a constant-speed driver) gives the
string **one** knob, but the performance charts impose **hard limits**. So a
requested final discharge pressure may simply be **impossible**, and the solver
must not pretend otherwise. There are two physically distinct cases — handle them
differently:

<table>
<caption>Infeasible target pressure: two cases</caption>
<tr><th>Case</th><th>Cause</th><th>Nature</th><th>Correct response</th></tr>
<tr><td><strong>Pressure too high</strong></td><td>Target above the head at the <strong>max-speed curve</strong>, above the <strong>driver power</strong> limit, or beyond <strong>stonewall</strong></td><td>Genuinely infeasible — no operating point exists</td><td>Saturate at the max-speed curve, <strong>flag the point infeasible</strong>, report the max achievable pressure, keep the run alive</td></tr>
<tr><td><strong>Pressure too low</strong></td><td>Even at <strong>min speed</strong> the string overshoots (too much head)</td><td>Recoverable — a real machine bleeds off the surplus</td><td>Apply a <strong>pressure-control</strong> action (downstream/upstream choke or ASV recycle); point stays feasible</td></tr>
</table>

**Do not** iterate harder past the chart (extrapolating the fan-law invents a
non-physical head), and **do not** throw an exception that aborts a whole
multi-area / multi-year run over a single infeasible duty. The professional
pattern (as used by [eCalc](https://github.com/equinor/ecalc), which itself uses
NeqSim for the thermodynamics) is: **saturate to the physical limit → classify
recoverable vs unrecoverable → return a feasibility flag with a reason and the
max achievable value → continue**. eCalc solves speed between its min- and
max-speed curves; a request **below** min-speed capability triggers a configurable
pressure-control strategy (downstream choke, upstream choke, or ASV recirculation)
to shed the surplus head, while a request **above** max-speed capability or over
max power marks that timestep **invalid** with a reason and the run reports which
points failed. Reserve a hard exception for *misconfiguration* (no chart, no
bodies, inverted bounds) — not for a well-posed but impossible pressure.

### Current NeqSim behaviour and how to detect it

Both solvers **saturate** rather than crash: `CompressorShaft.solveSpeed`, when
the target is not bracketed within `[minSpeed, maxSpeed]`, logs a warning and
returns the nearest speed bound; `CompressorShaftCalculator` clamps each internal
step to the same bounds. Because the *return value* alone does not tell you the
duty was impossible, check feasibility explicitly after the solve:

```java
double rpm = shaftCalc.getSpeed();

for (Compressor body : shaft.getCompressors()) {
  double maxSpeedCurve = body.getCompressorChart().getMaxSpeedCurve();
  boolean pinnedAtCeiling = rpm >= maxSpeedCurve - 1.0e-6;   // pressure-too-high symptom
  boolean overStonewall = body.isStoneWall();                // ran past max flow
  boolean inSurge = body.isSurge();                          // ran below min flow
  // Compare body.getPower() against the driver rating for the over-power case.
}

double poutAchieved = reference.getOutletStream().getPressure("bara");
boolean pressureMet = Math.abs(poutAchieved - target) < tolerance;
// pressureMet == false while rpm is pinned at the max-speed curve  ==>  target infeasible.
```

`Compressor` also exposes `getDistanceToSurge()`, `getDistanceToStoneWall()`, and
`getOperatingPoint()` (a JSON feasibility picture), and a multi-area plant surfaces
`feasible` / `hardLimitExceeded` through its capacity `getUtilizationSnapshot()`.
Gate an optimizer or `evaluate()` trial on *both* speed saturation **and** the
residual discharge-pressure error, so an impossible duty is rejected as infeasible
instead of accepted at the saturated speed.

## Shared Pressure Nodes

When another unit ties into an interstage — for example a 2nd-stage separator
gas that joins the recompressor between bodies — model it as a **pressure
equality**, not a second spec: slave that unit's pressure to the floating
interstage discharge, or drop a small adapting valve there. A `Mixer` at the
join already resolves to the lowest inlet pressure, so a small let-down is
automatic. This removes a degree of freedom rather than adding one.

### The Mixer pressure-mismatch flag

The flip side of "a `Mixer` resolves to the lowest inlet pressure" is that a
compressor which **failed to reach its target** discharge quietly drags the whole
mix down — the lost pressure just disappears downstream. Taking the lowest inlet
pressure is the correct physics, but you should be *told* it happened. `Mixer`
therefore raises a flag whenever its active inlets arrive at materially different
pressures:

```java
mixer.run();
if (mixer.isPressureMismatch()) {
  // The outlet dropped to the lowest inlet; a higher inlet was throttled down.
  double spread = mixer.getInletPressureSpread(); // bar between highest and lowest active inlet
  double lost   = mixer.getMaxInletPressure() - mixer.getMinInletPressure();
  // Investigate the upstream unit that did not reach its target pressure.
}
```

The flag is recomputed on every `run()` and a warning is logged when it trips.
The trip threshold defaults to 0.5 bar and is configurable with
`setPressureMismatchTolerance(bar)`; `getMaxInletPressure()` / `getMinInletPressure()`
report the spread's endpoints (the minimum equals the outlet pressure). Use this
as the downstream companion to the shaft feasibility check above: the shaft flag
tells you the machine could not make the pressure, and the mixer flag tells you
where that shortfall silently propagated into the flowsheet.

## Fixed-/Single-Speed Drivers

If the driver is a constant-speed motor (line frequency, no variable-speed
drive) the speed is **not** a degree of freedom — do **not** iterate it. Use
`runAtFixedSpeed`:

```java
shaft.runAtFixedSpeed(3550.0, new Runnable() {
  @Override
  public void run() {
    process.run();
  }
});
```

The discharge floats off the chart at the locked speed, and any pressure spec is
met by anti-surge recycle, suction throttling, or inlet guide vanes — never by
moving speed.

## Single-Body Shafts

A `CompressorShaft` with one compressor is valid and useful: `solveSpeed` just
finds that one machine's speed for its discharge target. So export,
re-injection and gas-lift machines (one body each on their own shaft) use the
same API — separate shafts keep separate speeds.

## Coexistence with Anti-Surge

The per-body anti-surge loops (recycle splitter, anti-surge valve, `Recycle`,
and the anti-surge calculator) stay attached and keep protecting each body; they
adjust *recycle flow*, while the shaft sets *speed*. Apply the shaft solve
**after** the charts and anti-surge are active. See
[Compressor Anti-Surge and Coordinated Control](compressor_antisurge_control.md)
and [Compressor Performance Curves](compressor_curves.md).

## API Summary

<table>
<caption>CompressorShaft public methods</caption>
<tr><th>Method</th><th>Purpose</th></tr>
<tr><td><code>addCompressor(Compressor)</code></td><td>Add a body in flow order.</td></tr>
<tr><td><code>solveSpeed(reference, targetP, unit, runnable)</code></td><td>Iterate the one common speed to the reference body's target discharge; intermediates float.</td></tr>
<tr><td><code>runAtFixedSpeed(rpm, runnable)</code></td><td>Constant-speed driver: lock the speed, discharge floats off the chart.</td></tr>
<tr><td><code>setSpeed(rpm)</code> / <code>getSpeed()</code></td><td>Apply / read the common shaft speed.</td></tr>
<tr><td><code>setSpeedBounds(min, max)</code></td><td>Speed search bounds for <code>solveSpeed</code>.</td></tr>
<tr><td><code>setMaxIterations(n)</code> / <code>setPressureTolerance(bar)</code></td><td>Root-finder budget and convergence tolerance.</td></tr>
<tr><td><code>getTotalPower()</code></td><td>Sum of the body shaft powers (W).</td></tr>
<tr><td><code>getCompressors()</code> / <code>getName()</code></td><td>Members / shaft name.</td></tr>
</table>

<table>
<caption>CompressorShaftCalculator public methods</caption>
<tr><th>Method</th><th>Purpose</th></tr>
<tr><td><code>CompressorShaftCalculator(name, shaft, reference, targetP, unit)</code></td><td>Wire the calculator to a shaft; <code>reference == null</code> uses the last body added.</td></tr>
<tr><td><code>setSpeedBounds(min, max)</code></td><td>Clamp the common-speed search (rpm).</td></tr>
<tr><td><code>setMaxStepFraction(fraction)</code></td><td>Max fractional speed change per internal pass (damping, e.g. 0.10).</td></tr>
<tr><td><code>getSpeed()</code></td><td>Read the converged common shaft speed (rpm).</td></tr>
<tr><td><code>process.add(shaftCalc)</code></td><td>Register so it steps the speed on every <code>run()</code> pass (add after the bodies/anti-surge).</td></tr>
</table>

## Related Documentation

- [Compressors](compressors.md)
- [Compressor Performance Curves](compressor_curves.md)
- [Compressor Anti-Surge and Coordinated Control](compressor_antisurge_control.md)
