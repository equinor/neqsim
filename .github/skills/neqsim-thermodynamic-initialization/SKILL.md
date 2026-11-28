---
name: neqsim-thermodynamic-initialization
description: "Select and audit NeqSim thermodynamic initialization levels for correctness and performance. USE WHEN: implementing or reviewing flashes, phase stability, EOS root selection, process-equipment thermodynamics, property access, or performance changes involving init(...) or initProperties()."
last_verified: "2026-11-26"
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

For CPA caloric changes, compare residual enthalpy with the fixed-P/composition
temperature derivative of the weighted log fugacity coefficients, and compare Cp
with dH/dT at more than one step. Check A_T/A_TT and explicit association
derivatives separately; holding the radial distribution fixed (or differentiating
Delta/g) isolates the latter. A stored database temperature coefficient is not
automatically the derivative of the implemented property expression. See
`CPATemperatureConsistencyTest`, `ClassicTxTemperatureDerivativeTest` and
[CPA caloric consistency](../../../docs/thermo/mixing_rules_guide.md#65-caloric-consistency).
`SystemPrCPA` shares the PR-based CPA association solver with UMR-CPA, while
retaining generic PR-CPA database parameters and PR alpha. Verify the ordinary
PR limit for non-associating components and avoid activating UMR parameter
families through implementation reuse. `PrCpaImplementationTest` covers the
issue #4636 repair, site responses, flash closure and independent cloned state.
Recreate old Java-serialized PR-CPA states after the phase serialization UID
change; the old incomplete association state is not migrated implicitly.

When a caller consumes temperature derivatives after `TPflash`, initialize them on
the resulting phase state with `init(2)`. Initializing before the flash is insufficient
when it replaces phases; the legacy cricondenbar iteration is a regression example.

`Phase.getCp()`, `getCv()`, `getEnthalpy()` and `getEntropy()` call
`ensureCaloricDerivatives()`: a phase still at init level 1 (the state a TP flash leaves) is
re-initialised to level 2 for its current T, P, composition and beta before the value is
returned. Before this guard, a flash followed by `initPhysicalProperties()` (no `init(2)`)
returned caloric values from the previous level-2 state: aqueous SRK-CPA Cp 9.1 instead of
3.9 kJ/(kg·K), oil Cp up to 25 % off for SRK/PR/UMR (`CaloricPropertiesAfterTPflashTest`,
measured 2026-11-21). Code that needs caloric values for many phases should still call
`init(2)`/`initProperties()` once; the guard is a safety net, not a substitute, and it does not
touch init levels 0, 2 or 3 or subclasses that override these getters (GERG, Span-Wagner,
Leachman, Vega, IAPWS, GE, Pitzer, solid phases).

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

For an initialized zero-throughput boundary, preserve the existing intensive state
when clearing material. `init(0)` resets the phase map and root roles and must not
be used merely to repeat a zero-flow request. Restart from stored overall fractions,
then flash at the requested conditions. `SystemThermoZeroFlowStateTest` covers
repeated zero requests and SRK/PR/CPA gas, liquid and aqueous restart behavior.

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
7. Run the NeqSim SpotBugs rules (`devtools/spotbugs-neqsim-rules`, see `docs/development/CODE_HEALTH.md`): `NEQSIM_CALORIC_WITHOUT_INIT` (read H/S/Cp/Cv without `init(2)`+ in the same method), `NEQSIM_PHYSICAL_WITHOUT_INIT` (read density/viscosity/conductivity/diffusion/interfacial tension without initialising it) and `NEQSIM_REDUNDANT_INIT` (identical init repeated on an unchanged fluid). Existing hits are baselined in `.config/neqsim-rules-baseline.json`; new ones fail CI.
8. For one physical property use the targeted call `initPhysicalProperties("density"|"viscosity"|"conductivity")`; it is faster than `initProperties()`. Diffusion coefficients and interfacial tension need the full `initPhysicalProperties()`, `initProperties()` or `calcInterfaceProperties()`.
9. A phase left at init level 1 by a TP flash has its caloric derivatives refreshed lazily by `Phase.getEnthalpy/getEntropy/getCp/getCv`, but only as a safety net: call the proper init explicitly, which is cheaper and what the rule checks.

## Audit guidance

Search production code for `init(2)` and `init(3)`. Prioritize calls inside iterative flashes, recycle loops, tray/column iteration, transient flow cells, and frequently executed unit operations. Do not bulk-replace them. Handle one bounded call path at a time, prove that downstream consumers need only a lower level, add regression coverage, and measure the benefit before opening a PR.

### HV allocation trials

The retained invocation-local scratch proposals are rejected for production:
allocation fell, but controlled Java17 small-PR initialization regressed. The
current evaluator and its initialization contract remain unchanged. Use the
same frozen harness, independent baseline controls and numerical endpoints
when evaluating another performance change; allocation is not a latency gate.
See `src/test/resources/neqsim/thermo/mixingrule/hv-phase-scratch/README.md`.
Benchmark JSON receipts now use atomic replacement: a failed next write must
preserve the accepted prefix. This adds no automatic retry or resume authority.


## Enhanced stability trial-order qualification

For a low-pressure water/glycol assessment, distinguish an imposed-liquid bubble
pressure from cold TP phase discovery. Enhanced/automatic-LLE ordinary trials must
visit vapor then liquid when the homogeneous reference is liquid; otherwise no
ordinary trial runs. The corrected order is tested by `FlashStabilityTrialOrderTest`
and the 42-row `HuronVidalAqueousMegPressureAssessmentTest` (2026-11-26).
Legacy traversal/reset behavior remains outside this repair. Numerical identity
checks do not establish global stability or measured accuracy; retain Gibbs,
phase-root and inventory evidence. See the owning [TP algorithm guide](../../../docs/thermodynamicoperations/TPflash_algorithm.md#enhanced-ordinary-trial-order)
and [HV source assessment](../../../src/test/resources/neqsim/thermo/mixingrule/hv-meg-aqueous/README.md).
