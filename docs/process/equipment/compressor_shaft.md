---
title: "CompressorShaft: Multiple Compressor Bodies on One Shaft"
description: "Model a multi-body compressor string driven by a single gas turbine or motor at one common speed with CompressorShaft. Covers the degrees-of-freedom rule, iterating the common speed to the final discharge (intermediate pressures float), shared pressure nodes, fixed-speed drivers, single-body use, and coexistence with anti-surge control."
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

## Shared Pressure Nodes

When another unit ties into an interstage — for example a 2nd-stage separator
gas that joins the recompressor between bodies — model it as a **pressure
equality**, not a second spec: slave that unit's pressure to the floating
interstage discharge, or drop a small adapting valve there. A `Mixer` at the
join already resolves to the lowest inlet pressure, so a small let-down is
automatic. This removes a degree of freedom rather than adding one.

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

## Related Documentation

- [Compressors](compressors.md)
- [Compressor Performance Curves](compressor_curves.md)
- [Compressor Anti-Surge and Coordinated Control](compressor_antisurge_control.md)
