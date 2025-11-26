# TurboExpanderCompressor model

This note summarizes the mathematical basis of the coupled expander/compressor model, how reference
curves are applied (and can be replaced), and provides a usage walkthrough for configuring and
running the unit in a process simulation.

## Mathematical basis

The expander and compressor share a shaft speed that is iteratively adjusted until the expander
power balances the compressor power plus bearing losses using a Newton-Raphson loop. Key steps in
the iteration are:

- Calculate the isentropic enthalpy drop across the expander from an isentropic flash at the target
  outlet pressure (`h_s = (h_in - h_out) * 1000`).【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L182-L214】
- Compute the tip speed \(U = \pi D N / 60\) and jet velocity \(C = \sqrt{2 h_s}\), form the
  velocity ratio \(uc = U / (C \cdot designUC)\), and evaluate an efficiency correction factor from
  the UC reference curve.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L193-L213】
- Optionally correct expander efficiency for off-design flow by evaluating a Q/N efficiency spline
  when a design expander Q/N is provided.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L199-L213】
- Multiply the design isentropic efficiency by the UC and Q/N correction factors to obtain the
  actual expander efficiency and shaft power (`W_expander = m * h_s * eta_s`).【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L208-L224】
- Compute compressor Q/N and evaluate separate spline corrections for polytropic efficiency and
  head. The head scales with \((N/N_design)^2\) and the efficiency scales linearly with the Q/N
  correction.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L215-L223】
- Form compressor shaft power as \(W_{comp} = \dot{m} H_p / \eta_p\) and include quadratic bearing
  losses to solve the speed update \(f(N) = W_{expander} - (W_{comp} + W_{bearing}) = 0\).【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L221-L256】

The iteration continues until the power mismatch is negligible or iteration limits are reached. The
final speed is applied to transient expander and compressor objects to populate outlet streams and
result properties.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L273-L306】

## Reference curves and updates

Three types of reference curves tune performance away from the design point:

- **UC/efficiency**: a constrained parabola through the peak at (uc=1, efficiency=1) that
  optionally can be re-fit with `setUCcurve(ucValues, efficiencyValues)` if alternate test data are
  available.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L503-L539】
- **Q/N efficiency**: a monotonic cubic Hermite spline built from paired Q/N and efficiency arrays
  via `setQNEfficiencycurve`. Values are extrapolated linearly outside the provided range, allowing
  off-map operation while preserving trend continuity.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L541-L619】
- **Q/N head**: a similar spline created with `setQNHeadcurve` that scales polytropic head at off
  design flows. Like the efficiency spline it preserves monotonicity and extrapolates linearly
  beyond the data range.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L621-L700】

Curve coefficients are stored on the equipment instance, so they can be replaced at runtime to test
alternative reference maps or updated dynamically from external performance monitoring tools.

## Using the model

1. **Construct the unit and streams.** Clone feeds for the expander and compressor outputs when
   instantiating the equipment.
2. **Set design parameters.** Provide impeller diameter, design speed, efficiencies, design Q/N, and
   optional expander design Q/N if expander flow corrections are needed. The defaults mirror the
   embedded design values but can be overridden through the available setters.
3. **Load reference curves (optional).** If site-specific head or efficiency curves exist, call
   `setUCcurve`, `setQNEfficiencycurve`, and `setQNHeadcurve` with measured points before running the
   unit.
4. **Run the model.** Call `run(UUID id)` (or the no-argument overload) to iterate speed matching and
   populate result fields and outlet streams. Retrieve shaft powers with `getPowerExpander(unit)` and
   `getPowerCompressor(unit)` or inspect efficiencies, head, and Q/N ratios through the getters.

A realistic setup that mirrors common plant data collection and map-updating workflows:

```java
TurboExpanderCompressor turboExpanderComp = new TurboExpanderCompressor(
    "TurboExpanderCompressor", jt_tex_splitter.getSplitStream(0));
turboExpanderComp.setUCcurve(
    new double[] {0.9964751359624449, 0.7590835113213541, 0.984295619176559, 0.8827799803397821,
        0.9552460269880922, 1.0},
    new double[] {0.984090909090909, 0.796590909090909, 0.9931818181818183, 0.9363636363636364,
        0.9943181818181818, 1.0});
turboExpanderComp.setQNEfficiencycurve(
    new double[] {0.5, 0.7, 0.85, 1.0, 1.2, 1.4, 1.6},
    new double[] {0.88, 0.91, 0.95, 1.0, 0.97, 0.85, 0.6});
turboExpanderComp.setQNHeadcurve(
    new double[] {0.5, 0.8, 1.0, 1.2, 1.4, 1.6},
    new double[] {1.1, 1.05, 1.0, 0.9, 0.7, 0.4});
turboExpanderComp.setImpellerDiameter(0.424);
turboExpanderComp.setDesignSpeed(6850.0);
turboExpanderComp.setExpanderDesignIsentropicEfficiency(0.88);
turboExpanderComp.setDesignUC(0.7);
turboExpanderComp.setDesignQn(0.03328);
turboExpanderComp.setExpanderOutPressure(inp.expander_out_pressure);
turboExpanderComp.setCompressorDesignPolytropicEfficiency(0.81);
turboExpanderComp.setCompressorDesignPolytropicHead(20.47);
turboExpanderComp.setMaximumIGVArea(1.637e4);

// Run the coupled model and retrieve power with unit conversion
turboExpanderComp.run();
double expanderPowerMW = turboExpanderComp.getPowerExpander("MW");
double compressorPowerMW = turboExpanderComp.getPowerCompressor("MW");
```

Parameter intent:

- **UC/efficiency curve**: normalizes the velocity ratio \(uc = U / (C \cdot designUC)\) to an
  efficiency multiplier via a constrained parabola fitted to the supplied points.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L193-L224】【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L503-L539】
- **Q/N efficiency curve**: cubic Hermite spline that scales expander and compressor efficiencies
  against flow coefficient deviations \(Q/N\).【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L195-L223】【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L541-L619】
- **Q/N head curve**: spline used to scale the compressor polytropic head for off-design flows
  before applying the \((N/N_{design})^2\) speed law.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L215-L223】【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L621-L700】
- **Impeller diameter & design speed**: set the peripheral velocity \(U\) at design, anchoring UC
  corrections and the Newton iteration for speed matching.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L182-L224】
- **Design efficiencies and heads**: base values multiplied by the curve factors to compute actual
  efficiency/head during each iteration.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L195-L224】
- **Design Q/N values**: reference flow coefficients for both expander (`setDesignExpanderQn`) and
  compressor (`setDesignQn`) used when interpolating the spline multipliers.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L195-L223】【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L704-L759】
- **Expander outlet pressure**: target pressure for the isentropic flash that produces the enthalpy
  drop \(h_s\).【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L182-L214】
- **IGV geometry**: `setMaximumIGVArea` and optional `setIgvAreaIncreaseFactor` bound the inlet guide
  vane throat; `IGVopening` is updated automatically per run, but can be preset for sensitivity
  studies.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L71-L79】【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L804-L808】【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L871-L892】【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L989-L992】

The same update paths can be invoked during runtime if monitoring identifies drift in the reference
maps; supplying new curve points and re-running will propagate the new performance predictions.

### IGV handling

IGV opening is computed from the last stage enthalpy drop, mass flow, and volumetric flow each time
`run` completes. The helper `evaluateIGV` infers density, estimates a nozzle velocity from half the
stage enthalpy drop, and derives the area needed to pass the flow; the requested opening is the area
ratio relative to the active throat area. If the required area exceeds the installed IGV area, an
optional enlargement factor (`setIgvAreaIncreaseFactor`) increases the available area and records
that the expanded setting is being used. The calculated fraction is capped at 1.0 and exposed via
`calcIGVOpening`, with the actual open area in mm² available through `calcIGVOpenArea` or
`getCurrentIGVArea`.【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L779-L808】【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L871-L899】【F:src/main/java/neqsim/process/equipment/expander/TurboExpanderCompressor.java†L973-L1000】
