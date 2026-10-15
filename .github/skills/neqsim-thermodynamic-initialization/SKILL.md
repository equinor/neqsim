---
name: neqsim-thermodynamic-initialization
description: "Select and audit NeqSim thermodynamic initialization levels for correctness and performance. USE WHEN: implementing or reviewing flashes, phase stability, EOS root selection, process-equipment thermodynamics, property access, or performance changes involving init(...) or initProperties()."
last_verified: "2026-10-14"
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

When a caller consumes temperature derivatives after `TPflash`, initialize them on
the resulting phase state with `init(2)`. Initializing before the flash is insufficient
when it replaces phases; the legacy cricondenbar iteration is a regression example.

For tests or solvers reading composition derivatives, call `init(3)` on the final
state after `initProperties()`. The latter refreshes level-2 thermodynamics and
physical properties; it does not certify that a cached composition Jacobian still
belongs to the current state. Keep derivative-consistency tolerances unchanged.

After phase removal, verify component inventory as well as normalized compositions:
`sum(beta[p] * x[p][i])` must reconstruct the feed. Do not repair a failed endpoint
only by assigning `x = z`; that can conceal a genuine phase instability. Compare
Gibbs energies at the same conserved feed and mole amount, recheck topology through
stability analysis, and require fugacity equality for a recovered split. The dry
SRK/PR condensate regression for issue #4202 is
`TPflashCondensateEndpointConservationTest`.

For a retained phase outside the active phase count, do not assume a fluid-only flash has synchronized its temperature, pressure, composition, or EOS state. Set the retained phase state explicitly and initialize it at the minimum level required before reading it. This is especially important for inactive solid phases used by freezing or precipitation searches.

When cloning components for separate phases, deep-copy mutable derivative work arrays before
`init(3)`: otherwise one phase overwrites another's Jacobian. Check analytical derivatives against
fixed-root composition differences when diagnosing Newton failures. Preserve `getPhaseType(i)`
(the configured EOS root), not merely `getPhase(i).getType()` (the resulting classification),
when copying or restoring a stability candidate. After phase removal, locate an active solid by
`PhaseType.SOLID`; logical index 3 need not still identify it.

For pure phases backed by independent fundamental EOS models, compare molar chemical potentials or Gibbs energies directly at the same temperature and pressure. Do not force the comparison through exponentiated fugacity coefficients when a native Gibbs value is available; large reference offsets can overflow `exp(ln phi)` even though the Gibbs residual is finite. Mixture solid-equilibrium paths may still require logarithmic fugacity or activity expressions.

## Flash state transfer and trial ownership

Use the package-private `FlashPhaseState` contract when changing TPflash or TPmultiflash
rollback/adoption. Keep these boundaries distinct:

- For compact rollback, retain logical-to-physical mapping, configured root selectors,
  beta, compositions and **each phase's own K-values**. Restore mapping before accessing
  phases, normalize beta and run `init(1)` once at the existing rollback boundary. Require
  unchanged T/P/feed/component order and retained physical slots; when phase shifts are
  disabled, verify the original selectors remain in those slots. Do not treat rebuilt
  fugacity caches as restored composition-derivative caches.
- For accepted cached phases, transfer complete independent phase clones. Verify matching
  feed/model and T/P in every destination physical slot: `setPhase()` retains destination
  T/P. Do not add normalization or initialization to a cache-preserving near-critical
  adoption merely to be safe. For an adoption that already rebuilds state, retain its
  normalization and single `init(1)` call. Keep the caller's acceptance checks and any
  required ordinary-fallback replay.
- Preserve the normal `clone()` capacity and ownership contract. Use compact
  `copyForStabilityTrial()` storage only in the detached, fixed-component lowest-Gibbs
  stability workspace. The qualified system path requires exact plain SRK/PR classes,
  classic mixing, analytic derivatives, two ordinary cubic storage slots, consistent
  component indices and valid distinct active mappings. Honor the flash operation's
  solid-check option as well as the system flag. Keep
  specialized models, subclasses and unsupported phase options on their normal virtual
  clone path. Never add components to a compact workspace or adopt its phases into a
  user fluid; do not introduce a shared cache across flash calls.

Run `FlashPhaseStateTest`, `TPflashPhaseLifecycleTest`, `StabilityTrialCopyTest` and
`TPflashConcurrentCloneIsolationTest` for changes to these contracts, together with
regressions for the affected fluid/model. Inspect
[the TPflash transfer and workspace contracts](../../../docs/thermodynamicoperations/TPflash_algorithm.md#root-selectors-and-newton-inventory-mapping)
before removing a replay or changing an initialization boundary. Extend model qualification
explicitly rather than inferring it from a shared base class.

For default CPA clone changes, verify independent phase association matrices and component
site-derivative rows with `PhaseSrkCPACloneIsolationTest`. Preserve cached values immediately
after cloning, and support derivative access before another association solve: clearing a
shared LU factorization requires rebuilding a private factorization from the copied Hessian
when no cached inverse is available. Do not insert `init(0)` into the regression merely to
rebuild aliased storage. Include Cp/Cv and attraction-temperature derivatives in concurrent
SRK/PR/default Statoil CPA checks. Keep model configuration unchanged; do not generalize those
results to arbitrary mixing rules or optional fully implicit, Anderson and Broyden CPA solvers.

## Review checklist

1. Identify every property read after the initialization call.
2. Map each property to the minimum required level from source code, not assumption.
3. Reject automated suggestions that raise the level without a demonstrated dependency.
4. For a proposed reduction, add focused tests proving unchanged requested quantities and convergence.
5. For hot paths, report iterations, flash calls, allocations, or benchmark results; avoid flaky wall-clock CI assertions.
6. Keep `initProperties()` separate from thermodynamic derivative levels.

## Audit guidance

Search production code for `init(2)` and `init(3)`. Prioritize calls inside iterative flashes, recycle loops, tray/column iteration, transient flow cells, and frequently executed unit operations. Do not bulk-replace them. Handle one bounded call path at a time, prove that downstream consumers need only a lower level, add regression coverage, and measure the benefit before opening a PR.
