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

A minimal example:

```java
Stream feed = new Stream("expander feed", thermoSystem);
TurboExpanderCompressor expComp = new TurboExpanderCompressor("tec", feed);
expComp.setDesignSpeed(6850.0);
expComp.setDesignUC(0.7);
expComp.setDesignQn(0.03328);
expComp.setCompressorDesignPolytropicHead(20.47);
// Optional: override reference curves
expComp.setQNEfficiencycurve(new double[] {0.8, 1.0, 1.2}, new double[] {0.95, 1.0, 0.96});
expComp.setQNHeadcurve(new double[] {0.8, 1.0, 1.2}, new double[] {0.92, 1.0, 1.05});
expComp.run();
double expanderPowerMW = expComp.getPowerExpander("MW");
```

The same update paths can be invoked during runtime if monitoring identifies drift in the reference
maps; supplying new curve points and re-running will propagate the new performance predictions.
