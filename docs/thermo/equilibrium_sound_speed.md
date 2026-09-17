---
title: "Homogeneous-equilibrium acoustic speed"
description: "Calculate an explicit isentropic equilibrium acoustic derivative for single-phase and vapor-liquid fluids, with entropy, inventory, convergence and phase-boundary diagnostics."
---

# Homogeneous-equilibrium acoustic speed

`SystemInterface.calculateEquilibriumSoundSpeed()` returns the fluid-frame homogeneous-equilibrium
acoustic speed and diagnostics. It supports single-phase fluids and vapor-liquid equilibrium with
fixed total component inventory. It is intended for equilibrium decompression studies.

## Definitions and API boundaries

| Quantity | Definition and API |
|---|---|
| Equilibrium speed | `fluid.calculateEquilibriumSoundSpeed()` evaluates the derivative below. Phase amounts and phase compositions change to preserve equilibrium. |
| Phase-specific speed | `fluid.getPhase(i).getSoundSpeed()` is the acoustic property of that individual phase at its current composition. |
| Legacy system speed | `fluid.getSoundSpeed()` and its unit overload return the **molar-phase-fraction weighted average** of phase speeds. Their behaviour is unchanged. This average is not an equilibrium derivative or a frozen-phase mixture model. |
| Frozen mixture speed | Requires an explicitly chosen relaxation model. For example, Wood's mechanical-equilibrium relation combines phase compressibilities using volume fractions while suppressing interphase mass transfer. The new API does not calculate this quantity. |

The equilibrium definition is

$$c_{eq}^{2}=\left(\frac{\partial p}{\partial\rho}\right)_{s,z},\qquad\rho=\frac{m}{V_{EOS}}.$$

Here $p$ is absolute pressure in Pa, $\rho$ is mass density in kg/m³, $s$ is specific entropy
in J/(kg K), $z$ is total composition, and $c_{eq}$ is in m/s. `getMass("kg") / getVolume("m3")`
provides the EOS density used by this API. The physical-property getter `getDensity("kg/m3")`
may apply density corrections and is deliberately not used in this derivative. Turning physical
volume correction on does not switch the acoustic calculation to a corrected-density model.

The closure assumes common pressure, temperature and velocity, instantaneous phase equilibrium,
and no chemical reaction, slip or finite-rate nucleation. The distinction between complete
thermodynamic equilibrium and Wood's mechanical-equilibrium speed is also discussed by
[Bai et al. (2026), sections 2.1 and 4](https://arxiv.org/html/2601.18404v1).
This reference supports the model distinction; it is not an experimental CO₂ benchmark for this API.

## Java example: pure CO₂ decompression state

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.util.EquilibriumSoundSpeed;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

SystemInterface fluid = new SystemPrEos(298.15, 150.0);
fluid.addComponent("CO2", 1.0);
fluid.setMixingRule(2);
fluid.useVolumeCorrection(false);
new ThermodynamicOperations(fluid).TPflash();
fluid.init(3);
double entropy = fluid.getEntropy("J/kgK");
fluid.setPressure(40.0);
new ThermodynamicOperations(fluid).PSflash(entropy, "J/kgK");
fluid.init(3);
if (Math.abs(fluid.getEntropy("J/kgK") - entropy) > 1.0e-7) {
  throw new IllegalStateException("Decompression path did not preserve entropy");
}
EquilibriumSoundSpeed.Result result = fluid.calculateEquilibriumSoundSpeed();
if (!result.isConverged()) {
  throw new IllegalStateException(result.getStatus() + ": " + result.getMessage());
}
double equilibriumSpeed = result.getSoundSpeed();
double legacyAverage = fluid.getSoundSpeed();
```

With Peng–Robinson, mixing rule 2 and these synthetic inputs, the 40 bara state gives approximately
**57.08 m/s** for the equilibrium derivative and **408.76 m/s** for the legacy average. At the
150 bara initial state, the equilibrium and phase-specific speeds both give about **456.23 m/s**.
These are regression values from the selected EOS, not measured acoustic data.
The example is exercised by `EquilibriumSoundSpeedTest`.

The same methods are accessible through JPype / neqsim-python. The standalone Java entry point
`EquilibriumSoundSpeed.calculate(fluid, relativePressureStep)` is equivalent to the system method
with the same step argument.

## Input state and entropy roots

Supply an already flashed fluid with the intended temperature, pressure, phase inventory and
composition. The calculator takes the **current state's specific entropy** as its target; it cannot
infer an upstream decompression entropy that was not supplied by the caller. Check every upstream
PS flash before using its state. In particular, this API does not repair the general PSflash defect
tracked in [#3751](https://github.com/equinor/neqsim/issues/3751).

All work is performed on cloned fluids, including property initialization. The centre state is
re-equilibrated at its pressure and target entropy; its actual temperature and density are included
in the diagnostics. The caller's fluid, phase fractions, amount and thread-local warm-start setting
are preserved. Results are immutable, serializable snapshots and contain no mutable fluid handles.

- **Mixtures:** bracket specific entropy in temperature using fresh, cold TP flashes, then bisect.
  This route does not use mixture PSflash results. It accounts for phase-fraction and composition
  changes inside the temperature derivative.
- **Pure fluids:** try a checked PS flash, which can solve saturation quality. If closure fails,
  try the checked TP temperature root. A temperature root alone cannot resolve a pure-fluid
  saturation entropy jump; failure to close that jump is reported explicitly.
- Every accepted state must have finite positive density and temperature, the requested pressure,
  valid phase fractions and compositions, component closure, and interphase fugacity closure.

## Stencils, convergence and diagnostics

The initial relative pressure step defaults to `0.001`. The overload accepts `[1e-6, 0.05]`.
If $h$ is the pressure increment in Pa, the central estimate is

$$D_h=\frac{\rho(p+h,s,z)-\rho(p-h,s,z)}{2h},\qquad c_{eq}=\sqrt{1/D_h}.$$

The calculator compares $D_h$ and $D_{h/2}$ and accepts a finite positive derivative only when their
relative difference is at most **0.002**. It permits ten step halvings. This is a numerical
step-consistency criterion, not a guarantee of 0.2% physical accuracy.

If a neighbour changes the phase assemblage relative to the centre, a second-order forward or
backward stencil is used on the side matching the centre, with samples at $p$, $p\pm h$ and
$p\pm 2h$. Both compared stencils must have the same direction. If neither side provides a
consistent branch, the step is reduced. At phase entry, a one-sided value describes the selected
branch; a unique two-sided derivative need not exist.

| Diagnostic | Meaning |
|---|---|
| `isConverged()`, `getStatus()`, `getMessage()` | Explicit outcome. Failures return `NaN` from `getSoundSpeed()`. |
| `getSpecificEntropy()` | Target $s$, J/(kg K). |
| `getPressureStepPa()` | Actual final pressure increment, Pa. |
| `getRelativeStepError()` | Relative change of the density derivative at successive step sizes. |
| `getStencil()` | `CENTRAL`, `FORWARD`, `BACKWARD`, or `UNAVAILABLE`. |
| `isPhaseBoundaryEncountered()` | Any sampled stencil encountered a different phase assemblage, including a discarded coarse stencil. This is not a complete phase-boundary search. |
| `getFlashEvaluations()` | Total TP and PS flash calls, including rejected root trials. |
| `getSamples()` | Final stencil snapshots, centre first; then low/high for central, near/far for one-sided. On failure, the latest accepted snapshots may be incomplete. |

Each sample exposes pressure (Pa), temperature (K), EOS density (kg/m³), signed entropy residual
(J/(kg K)), component closure residual, interphase log fugacity residual, phase names and molar
fractions. Acceptance tolerances are `1e-7 J/(kg K)` for entropy, `1e-8` for each component inventory
error divided by input total moles (and for phase-fraction sum), and `1e-6` for the maximum
absolute interphase log fugacity ratio. Fugacity checking excludes overall mole fractions at or
below `1e-12`; the inventory check includes all components. Single-phase fugacity residual is zero.

Failure categories distinguish unsupported physics, failed equilibrium closure and lack of
derivative convergence. Inspect the diagnostic message before changing the pressure step.

## Validation and limitations

Regression coverage includes the single-phase analytical sound speed and dilute-gas limit, pure
CO₂ at 40 bara, 98/2 mol% CO₂/N₂ and CO₂/H₂ at 50, 40 and 20 bara, independently bracketed TP-root
references, step sensitivity, inventory scaling, physical-density correction independence,
phase-entry stencil direction, caller preservation and result serialization.

This establishes numerical and thermodynamic consistency for those PR cases. It does not qualify
all EOS, near-critical conditions, trace compositions, metastable branches or measured decompression
experiments. Three or more phases, liquid-liquid states, solids, hydrates, reactions and forced
phase modes are unsupported. A small step can amplify flash noise and a large one can cross a
phase boundary. For sensitive studies, repeat with multiple initial step sizes and inspect samples.

The routine is a diagnostic calculation requiring multiple flashes, not a cached transport property
or an inexpensive per-cell replacement in a transient solver. It makes no fracture-arrest
qualification claim.

## Acoustic speed versus decompression characteristic

$c_{eq}$ is measured relative to the fluid. In a one-dimensional flow with laboratory velocity $u$,
the acoustic characteristics are $u\pm c_{eq}$. Under the convention that $u$ is positive toward the
rupture, the magnitude of the upstream-running decompression characteristic is $w=c_{eq}-u$
when $c_{eq}>u$. The velocity must come from the flow solution with a consistent sign convention;
the acoustic API has no velocity input and does not calculate $w$.

Related guides: [reading fluid properties](reading_fluid_properties),
[flash calculations](flash_calculations_guide), and
[attainable metastability](attainable_metastability).
