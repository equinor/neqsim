---
name: neqsim-thermodynamic-initialization
description: "Select and audit NeqSim thermodynamic initialization levels for correctness and performance. USE WHEN: implementing or reviewing flashes, phase stability, EOS root selection, process-equipment thermodynamics, property access, or performance changes involving init(...) or initProperties()."
---

# NeqSim thermodynamic initialization

Use this skill whenever implementing or reviewing flashes, phase stability, EOS root selection, process-equipment thermodynamics, property access, or performance changes involving `init(...)` or `initProperties()`.

## Core rule

Always use the lowest initialization level required by the quantities actually consumed:

| Level | Use for |
|---|---|
| `init(0)` | Composition, phase amounts, and basic bookkeeping without EOS properties |
| `init(1)` | Fixed-T/P EOS state, cubic roots, molar volume, Z, fugacity coefficients, chemical potentials, phase-equilibrium Gibbs comparisons, TPD/stability calculations, and TP-flash iterations |
| `init(2)` | Caloric properties and first temperature derivatives: enthalpy, entropy, Cp/Cv, Joule-Thomson coefficient, and related temperature derivatives |
| `init(3)` | Second composition derivatives, derivative matrices, and other explicitly verified level-3 quantities |
| `initProperties()` | Density models and transport/interfacial properties when those properties are requested |

Do not call `init(2)` or `init(3)` merely to be safe. Before increasing a level, trace the exact downstream getter and confirm its dependency in `Phase`, `PhaseEos`, the component implementation, or the relevant property model.

For fixed-T/P cubic-root selection and fugacity/Gibbs equilibrium checks, prefer `init(1)`. Temperature derivative caches such as `loc_AT` and `loc_ATT` support caloric properties; they are not by themselves evidence that a fixed-T/P flash-Gibbs comparison requires `init(2)`.

For a retained phase outside the active phase count, do not assume a fluid-only flash has synchronized its temperature, pressure, composition, or EOS state. Set the retained phase state explicitly and initialize it at the minimum level required before reading it. This is especially important for inactive solid phases used by freezing or precipitation searches.

For pure phases backed by independent fundamental EOS models, compare molar chemical potentials or Gibbs energies directly at the same temperature and pressure. Do not force the comparison through exponentiated fugacity coefficients when a native Gibbs value is available; large reference offsets can overflow `exp(ln phi)` even though the Gibbs residual is finite. Mixture solid-equilibrium paths may still require logarithmic fugacity or activity expressions.

## Review checklist

1. Identify every property read after the initialization call.
2. Map each property to the minimum required level from source code, not assumption.
3. Reject automated suggestions that raise the level without a demonstrated dependency.
4. For a proposed reduction, add focused tests proving unchanged requested quantities and convergence.
5. For hot paths, report iterations, flash calls, allocations, or benchmark results; avoid flaky wall-clock CI assertions.
6. Keep `initProperties()` separate from thermodynamic derivative levels.

## Audit guidance

Search production code for `init(2)` and `init(3)`. Prioritize calls inside iterative flashes, recycle loops, tray/column iteration, transient flow cells, and frequently executed unit operations. Do not bulk-replace them. Handle one bounded call path at a time, prove that downstream consumers need only a lower level, add regression coverage, and measure the benefit before opening a PR.
