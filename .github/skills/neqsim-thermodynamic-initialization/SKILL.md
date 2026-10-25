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

For a column K-value Newton step that consumes `getdfugdn(...)` and `getdfugdt()`,
initialize the derivative-providing vapor state with `init(3)`. Reusing that state as a
nearby-state predictor does not replace the final EOS residual check. When publishing a
forced-root solver result, preserve the root policy on the material outlet systems so a
later caloric initialization cannot silently choose another root.

For fixed-T/P cubic-root selection and fugacity/Gibbs equilibrium checks, prefer `init(1)`. Temperature derivative caches such as `loc_AT` and `loc_ATT` support caloric properties; they are not by themselves evidence that a fixed-T/P flash-Gibbs comparison requires `init(2)`.

When a caller consumes temperature derivatives after `TPflash`, initialize them on
the resulting phase state with `init(2)`. Initializing before the flash is insufficient
when it replaces phases; the legacy cricondenbar iteration is a regression example.

An empty stream has no caloric temperature constraint: PH with zero total enthalpy
must retain its temperature template, while nonzero extensive enthalpy is invalid.
Do not run inverse-temperature Newton iterations on zero inventory or normalize a
flowing zero-enthalpy target by zero. `PHFlashZeroInventoryTest` covers preserved
templates, resumed-flow closure, pure-component dispatch and small nonzero flows.
When a column trial fails its EOS evaluation, retain the cause and report an
unconverged profile at the actual iteration; do not publish the invalid trial or
report iteration-cap exhaustion that did not occur.

For strict release-inventory VU accounting, repeated general VU calls can stagnate on
single-phase dense liquids even when caloric caches are current. Keep the strict balance
gates. A fixed-composition coupled V/U Jacobian needs the volume/energy cross derivatives
and an unchanged equilibrated phase; `ReleaseInventoryVuRefinementTest` qualifies the
bounded inventory-local correction. Do not apply that correction globally to separator
trajectories: phase-changing transient acceptance requires separate qualification.

For correlations that consume only mass density, initialize each requested phase with
`initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY)` after the flash. Refresh
finite caches as well as zero caches, preserve the configured density model, and reject
invalid outputs instead of substituting an EOS estimate. Keep this separate from full
transport initialization. When cloning fluids, preserve interfacial model selections
and parameters while binding independent models to the clone; discard copied GT
profiles until recalculation. See
[the interfacial-properties guide](../../../docs/physical_properties/interfacial_properties.md)
and `SurfaceTensionStateLifecycleTest` for regression cases.

Bind all cloned physical-property families to the new phase, including inactive
gas/oil/aqueous/solid families, and bind their calculation methods to the new family.
Rebinding only the active family during initialization retains historical phase
graphs during repeated role changes and can overflow column candidate serialization.
`PhysicalPropertiesCloneOwnershipTest` checks ownership before initialization and
bounded serialized state. This does not qualify concurrent HV mixing-rule state.

After phase removal, verify component inventory as well as normalized compositions:
`sum(beta[p] * x[p][i])` must reconstruct the feed. Do not repair a failed endpoint
only by assigning `x = z`; that can conceal a genuine phase instability. Compare
Gibbs energies at the same conserved feed and mole amount, recheck topology through
stability analysis, and require fugacity equality for a recovered split. The dry
SRK/PR condensate regression for issue #4202 is
`TPflashCondensateEndpointConservationTest`.

When creating an empty clone or extracting a phase, update physical storage slots
with `getPhases()[slot]`, rather than iterating mapped `getPhase(slot)` values:
phase removal can leave repeated indices, and `init(0)` restores the identity
mapping. A skipped inactive slot can then resurrect its unscaled inventory. Keep
one common overall component inventory in every slot and derive the scalar total
from that inventory once; phase-specific amounts are a different quantity. The
phase-removal regression for issue #4427 is `SystemThermoEmptySystemCloneTest`.

After a homogeneous reaction updates component amounts, derive the scalar total from
the exact amount vector and synchronize overall amounts and `z` across every stored
phase before `init_x_y()` and `init(1)`. `init_x_y()` updates active slots; it cannot
repair stale overall compositions in an inactive slot that a later flash reactivates.
Preserve species outside the reaction database. `GibbsReactorInventorySynchronizationTest`
and `GibbsReactorCO2ScenariosTest` cover the outlet-flash consumer; do not weaken the
flash material-balance gate to accept a non-normalized feed.

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
