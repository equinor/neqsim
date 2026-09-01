---
name: neqsim-phase-envelope
description: "Generate, plot, interpret, validate, and troubleshoot NeqSim PT phase envelopes. USE WHEN: calculating phase envelopes, dew and bubble curves, cricondenbar, cricondentherm, critical points, retrograde regions, envelope segments, or fixing Michelsen continuation and singular-Jacobian failures caused by zero or trace components."
---

# NeqSim PT Phase Envelopes

## Overview

Use this skill to generate physically interpretable PT phase envelopes and to modify the
Michelsen continuation solver safely. Prefer the structured segment API, verify branch
identity from physics, and preserve the caller's thermodynamic system.

## Route the Task

- Use `@thermo.fluid` for envelope generation, plotting, properties, and solver defects.
- Chain to `neqsim-eos-regression` or `@pvt.simulation` when matching lab data or tuning EOS parameters.
- Chain to `neqsim-flow-assurance` for operating-path, hydrate, wax, or pipeline assessments.
- Chain to `neqsim-ccs-hydrogen` for CO2/H2 impurity envelopes.
- Chain to `neqsim-troubleshooting` when continuation does not converge or output is incomplete.

## Workflow

### 1. Verify the API and inputs

Read these sources before generating or changing code:

- `ThermodynamicOperations.calcPTphaseEnvelope(...)` and `getEnvelopeSegments()`
- `PTPhaseEnvelopeMichelsen` result getters and continuation settings
- `EnvelopeSegment` getters and `PhaseType`
- Neighboring tests in `PTPhaseEnvelopeMichelsenTest`, `PTPhaseEnvelopeRobustnessTest`, and
  `PTPhaseEnvelopeSegmentsTest`

Validate that temperature is in K, pressure is in bara, component amounts are finite and
non-negative, and the mixing rule is set. Choose the EOS from the fluid chemistry, not from
the desired plot shape.

### 2. Handle zero and trace components correctly

The Michelsen Jacobian contains terms divided by overall mole fraction $z_i$. Components with
zero or numerically negligible $z_i$ can therefore make the Jacobian singular.

- Current `PTPhaseEnvelopeMichelsen` excludes components with $z_i < 10^{-12}$ from a private
  clone after composition initialization.
- Do not remove components from the caller's `SystemInterface`.
- Do not discard physically meaningful trace impurities at or above the threshold merely to
  obtain convergence. Their impact can be important for CO2 quality, water behavior, and H2S.
- When changing the threshold or filtering logic, add a regression proving both finite envelope
  output and unchanged caller composition.

### 3. Generate the envelope

```java
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.calcPTphaseEnvelope(true, 1.0);

List<EnvelopeSegment> segments = ops.getEnvelopeSegments();
double[] cricondenbar = ops.get("cricondenbar");
double[] cricondentherm = ops.get("cricondentherm");
```

Use `calcPTphaseEnvelope(true, lowPressureBara)` when the start side and lower pressure
matter. Use the no-argument overload for the standard calculation.

### 4. Consume structured segments by default

Use `getEnvelopeSegments()` for plotting, JSON export, and AI-generated analysis. Each segment
is contiguous and contains no `NaN` branch separators.

```java
for (EnvelopeSegment segment : ops.getEnvelopeSegments()) {
  double[] temperaturesK = segment.getTemperatures();
  double[] pressuresBara = segment.getPressures();
  EnvelopeSegment.PhaseType storedType = segment.getPhaseType();
  // Plot each segment independently; do not connect separate segments.
}
```

The legacy `ops.get("dewT")`, `ops.get("dewP")`, `ops.get("bubT")`, and
`ops.get("bubP")` arrays remain supported. They may contain intentional `NaN` values that mark
branch breaks. Never reject an envelope solely because these flat arrays contain `NaN`; reject
infinities and require finite physical points.

### 5. Classify physical branches

For `calcPTphaseEnvelope(true, 1.0)`, stored dew/bubble labels can be swapped by the historical
Michelsen tracing order. This applies to legacy array names and can affect `EnvelopeSegment`
stored `PhaseType` labels. Do not infer physical identity from a getter or enum name alone.

Classify the physical dew side as the branch containing the cricondentherm, normally the branch
with the highest finite temperature. The other side is the physical bubble branch. Preserve
segment boundaries while grouping segments by physical side.

```python
finite_a = branch_a_t[np.isfinite(branch_a_t)]
finite_b = branch_b_t[np.isfinite(branch_b_t)]
if finite_a.max() > finite_b.max():
    dew_t, dew_p = branch_a_t, branch_a_p
    bubble_t, bubble_p = branch_b_t, branch_b_p
else:
    dew_t, dew_p = branch_b_t, branch_b_p
    bubble_t, bubble_p = branch_a_t, branch_a_p
```

If the branches have overlapping maxima or an unusual topology, use the reported
cricondentherm point and critical-point continuity instead of relying only on maximum
temperature.

### 6. Validate the result

Require all applicable checks:

1. At least one non-empty structured segment.
2. At least one finite point on each expected physical branch.
3. No infinite temperatures or pressures.
4. Positive pressures and physically plausible temperatures.
5. Cricondentherm equals the maximum finite envelope temperature within numerical tolerance.
6. Cricondenbar equals the maximum finite envelope pressure within numerical tolerance.
7. Bubble and dew branches meet continuously near the critical point.
8. The original fluid still has the same component count and composition.
9. Benchmark key points against lab, literature, or another trusted EOS implementation when
   results drive an engineering decision.

Do not require every flat-array value to be finite because `NaN` is the branch-break sentinel.

## Solver Change Protocol

When modifying `PTPhaseEnvelopeMichelsen` or `SysNewtonRhapsonPhaseEnvelope`:

1. Add a focused regression that fails before the code change.
2. Assert physical outputs, not only `assertDoesNotThrow`.
3. Cover zero fraction, near-zero fraction, ordinary gas, and a heavy/TBP mixture as relevant.
4. Verify the operation may use a filtered clone while the caller's system is unchanged.
5. Preserve two-pass continuation, critical-point handling, and segment construction.
6. Run the focused regression immediately after the first edit.
7. Run the full Michelsen and robustness test classes.
8. Run Java 8 compilation, Spotless, Checkstyle, and JavaDoc gates.

Focused Windows commands:

```powershell
.\mvnw.cmd test "-Dtest=PTPhaseEnvelopeMichelsenTest#testMethod"
.\mvnw.cmd test "-Dtest=PTPhaseEnvelopeMichelsenTest,PTPhaseEnvelopeRobustnessTest,PTPhaseEnvelopeSegmentsTest"
.\mvnw.cmd --file pomJava8.xml test "-Dtest=PTPhaseEnvelopeMichelsenTest#testMethod" "-Djacoco.skip=true"
.\mvnw.cmd spotless:apply
.\mvnw.cmd spotless:check
```

Use explicit Java 8 types. Do not use `var`, `List.of`, `String.repeat`, records, or other Java
9+ APIs. Use Log4j2 rather than `System.out` or `System.err`.

## Troubleshooting

| Symptom | Likely cause | Action |
|---|---|---|
| Singular Jacobian or division by $z_i$ | Zero/negligible component reached continuation equations | Confirm the current private-clone filter runs after `init(0)`; never mutate the caller |
| Envelope arrays contain `NaN` | Intentional break between disjoint traced segments | Use `getEnvelopeSegments()` or split flat arrays on `NaN` |
| Bubble and dew labels look reversed | Historical stored-label behavior with bubble-first tracing | Classify from cricondentherm and physical topology |
| Empty or very short branch | Poor start point, extreme composition, unsuitable EOS, or continuation failure | Check logs, lower start pressure, simplify a clone for diagnosis, and compare with neighboring robustness tests |
| Unrealistic critical point | Wrong EOS, uncharacterized heavy end, bad composition, or unit error | Validate inputs, characterize plus fractions, and benchmark independently |
| Trace impurity disappears | Fraction is below the numerical filter threshold | Decide whether it is physically relevant; if so, use a defensible non-negligible composition and document sensitivity |
| Envelope reports a cricondentherm far colder than a direct flash finds liquid at | Michelsen continuation truncated the dew branch | Verify with a flash scan (below) before trusting any envelope on a lean gas with a small heavy tail |
| `dewPointTemperatureFlash` returns the initial temperature guess unchanged | Degenerate incipient-liquid seed | Fixed for zero-fraction water (see below). Otherwise reseed the flash near the expected root |
| Point dew point sits above the cricondentherm | Flash converged on the low-temperature retrograde root | Seed the flash at the cricondentherm temperature and assert `T_dew <= T_cricondentherm` |

### Always Cross-Check a Lean Gas With a Flash Scan

On a lean gas with a small heavy tail the Michelsen continuation can **truncate the dew
branch and still return a plausible-looking envelope**. Observed on a 90.5 mol% methane
gas-cap gas with 0.25 mol% C7+: `calcPTphaseEnvelope(true, 1.0)` reported a
cricondentherm of −66 °C, while a direct `TPflash` at 6 °C and 45 bara found 0.9 mol%
liquid — the true cricondentherm was +41 °C. Nothing in the envelope output flagged the
truncation, and the error would have hidden the whole liquid-handling and slugging issue
for a subsea tie-back.

Cheap guard: bisect on the phase count at one or two pressures and compare.

```python
def dew_point_temperature(fluid, pressure_bara, t_high=90.0, t_low=-60.0):
    """Upper (retrograde) dew temperature by bisection on the phase count."""
    lo, hi = t_low, t_high
    for _ in range(40):
        mid = 0.5 * (lo + hi)
        w = fluid.clone()
        w.setTemperature(273.15 + mid)
        w.setPressure(pressure_bara)
        ThermodynamicOperations(w).TPflash()
        if int(w.getNumberOfPhases()) > 1:
            lo = mid
        else:
            hi = mid
    return 0.5 * (lo + hi)
```

If the scan and the envelope disagree by more than a few kelvin, trust the scan and
report the envelope as unreliable for that fluid.

### Point Dew-Point Flashes on Wet Gas

`ThermodynamicOperations.dewPointTemperatureFlash()` seeds an aqueous incipient
liquid whenever water carries moles, so on a wet gas it returns the **water** dew
point. For a hydrocarbon dew point, clone the fluid and
`removeComponent("water")` first.

Seed the flash at the cricondentherm temperature so it descends onto the upper
(physical) dew branch rather than a low-temperature retrograde root:

```java
double[] cct = envOps.get("cricondentherm"); // [T (K), P (bara)]
hcFluid.setPressure(pBara, "bara");
hcFluid.setTemperature(cct[0]);
hcFluid.init(0);
new ThermodynamicOperations(hcFluid).dewPointTemperatureFlash();
```

Zero-fraction water no longer changes the result: the aqueous seed is gated on
`ConstantDutyTemperatureFlash.hasSignificantWater`, so a component with
$z_i = 0$ behaves like an absent one.

## Output Convention

Report:

- EOS and mixing rule
- normalized composition and any filtered numerical-zero components
- critical point, cricondenbar, and cricondentherm with K/bara units
- physically classified dew and bubble segments
- operating points or paths overlaid on the envelope when relevant
- convergence or filtering warnings
- benchmark source and deviations for decision-critical work

## Known Limitations

- Stored branch labels can differ from physical branch identity for bubble-first tracing.
- Cubic-EOS envelope accuracy depends on heavy-end characterization and binary interaction
  parameters; numerical convergence does not prove physical accuracy.
- PT phase envelopes do not replace hydrate, wax, solid, electrolyte, or reactive-equilibrium
  boundaries. Add those analyses through their dedicated skills.
