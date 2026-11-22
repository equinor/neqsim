---
title: "NRC-2998: Shared flow-rate unit registry and explicit physical bases"
description: Proposal for consistent flow-rate conversions across NeqSim's unit, system, phase, and component APIs.
---

# NRC-2998: Shared flow-rate unit registry and explicit physical bases

- Status: PROPOSED
- Owners: @EvenSol (thermodynamics, process, and repository fallback); independent maintainers and a thermodynamics domain reviewer required
- Created: 2026-09-27
- Target release: UNPLANNED; behavior changes require a major contract boundary

## Context and problem

[Issue #2998](https://github.com/equinor/neqsim/issues/2998) identifies five
independent conversion implementations: `RateUnit.getConversionFactor`,
`SystemThermo.getFlowRate`, `Phase.getFlowRate`, `Component.getFlowRate`, and
`Component.getTotalFlowRate`. `SystemThermo.setTotalFlowRate` also selects a
density before constructing `RateUnit`. Unit aliases, numerical factors, and
physical bases can therefore disagree. A methane example in #2998 sets
`100 gallons/min` and reads approximately `641204 gallons/min` back. Some
accepted setter aliases have no getter path.

[PR #3801](https://github.com/equinor/neqsim/pull/3801) improves the scalar
unit-conversion contracts and preserves compatibility, but retains these flow
dispatches and the legacy numerical factors. It is a foundation, not an
implementation of #2998. A single `RateUnit` density argument cannot express
both the actual and ideal-liquid density required by a cross-basis conversion.

The existing endpoints have different physical meanings. In particular,
`Component.getFlowRate("m3/sec")` uses `getVoli() / 1e5`, a component volume
contribution; it cannot simply substitute the component's pure-fluid density.
The `SystemThermo` actual-volume getter uses system density, while the setter
currently takes phase 0 density. These choices need an explicit contract for
multiphase systems before sharing a converter.

## Decision

Introduce one Java 8 compatible, immutable registry of flow-rate unit
definitions. Each entry declares canonical spelling, aliases, physical basis,
time scale, and volume scale. Resolve both source and target units from that
registry; generate supported-unit diagnostics from it. Keep quantity factors
separate from property providers. Candidate bases are `MOLAR`, `MASS`,
`ACTUAL_VOLUME`, `IDEAL_LIQUID_VOLUME`, `STANDARD_GAS_VOLUME`, and
`NORMAL_GAS_VOLUME`. Normal gas and standard gas must have separate reference
states: NeqSim currently declares 273.15 K and 288.15 K respectively. Specify
absolute reference pressure alongside each temperature and document whether
ideal or real-gas volume is intended.

A conversion context supplies molar mass and lazily supplies only properties
used by the requested source and target bases. The context distinguishes a
whole system, one phase, and a component. A whole-system actual-volume
contract must address multiphase volume; a component context must retain its
volume-contribution meaning or introduce an explicitly named replacement.
Do not infer one density from another basis or initialize unrelated physical
properties for mass and molar conversions. Invalid required properties must
produce a clear error instead of `NaN` or infinity. Zero flow must remain
convertible without requiring a density when no physical division is needed.

Migrate `setTotalFlowRate` and the four getters only after agreeing on a
compatibility map for every currently accepted endpoint and unit. Use the
shared registry for common conversions, with context-specific volume
providers. New aliases may be introduced where their physical meaning is
unambiguous; do not silently claim that every system unit also has a valid
component conversion. Keep the existing `RateUnit` constructor as a
documented compatibility adapter for a release cycle, with a new explicit
context API for cross-basis conversions.

## Public contracts affected

- `RateUnit` and `SystemThermo.setTotalFlowRate`;
- `SystemThermo.getFlowRate`, `Phase.getFlowRate`, `Component.getFlowRate`,
  `Component.getTotalFlowRate`, and stream delegation;
- supported unit names, exception diagnostics, and numerical results from
  `gallons/min`, `barrel/day` / `bbl/day`, `Nlitre/*`, and volume conversions.

The current gallon setter selects ideal-liquid density, whereas the getter
uses flowing density and a different factor. The barrel path has a fixed
mass-to-volume factor without an explicit density. No numerical replacement
for either family is approved by this proposal alone. The independent
review must choose and document its reference state and whether existing
names denote actual or reference liquid volume. Prefer a new explicit unit
name when one old spelling cannot preserve both interpretations.

## Engineering and safety boundary

This is a units and calculation-contract decision, not qualification of
fluid properties for a project. Conversion of a volumetric flow requires
the corresponding density or reference state, composition, pressure, and
temperature. Multiphase and component calculations must conserve the
relevant mole and mass flow, but a component's volume contribution need not
equal its pure-component volume. The implementation must state which
physical-property initialization it performs and its behavior for empty
fluids, mixtures, repeated evaluations, cloning, and serialization.

## Compatibility and migration

Separate the work into reviewable stages:

1. Inventory every accepted spelling and independently expected conversion
   for each endpoint. Preserve existing results in a characterization suite,
   including known erroneous ones marked as such.
2. Add the registry and explicit conversion context without changing the
   meaning of existing public methods. Keep the compatibility adapter and
   document the context requirement.
3. After NRC acceptance, implement agreed behavior changes at the chosen
   major-version boundary with migration notes, compatibility tests, and
   release notes. Report the old and new results for `gallons/min` as a
   composition-dependent change, not a universal correction factor.

Migration examples shall show how to request actual volume, ideal-liquid
volume, standard gas volume, and normal gas volume explicitly, and how to
reproduce a historical result when necessary. Deprecation support follows
`docs/development/api-lifecycle.md` for normal and LTS releases.

## Validation and qualification evidence

- Independently calculate molar, mass, and volume examples using defined
  constants, including a US liquid gallon of 0.003785411784 m3 and a US
  petroleum barrel of 0.158987294928 m3. Do not use the new forward and
  inverse conversions as each other's only oracle.
- Test every registry alias, all supported endpoint/unit combinations,
  source-to-target and target-to-source conversions, and generated errors.
  Exercise both actual and reference liquid density in one conversion.
- Check standard gas at 288.15 K and normal gas at 273.15 K against their
  declared absolute reference pressure; verify 0 C is not treated as 15 C.
- Test a single gas phase, a liquid phase, and a flashed gas-liquid system;
  compare system volume with an independently calculated phase-volume sum.
- Check component mole/mass additivity and the existing component partial
  volume semantics. Do not assume pure-component density is equivalent.
- Test zero rate, negative rate where permitted, null/unknown units,
  non-finite and non-positive required properties, repeated calls, cloning,
  serialization, and Java 8 compilation.
- Verify Java and Python-facing stream/system use, documentation examples,
  Spotless, documentation search, and the full relevant CI matrix.

The source of the current `641204` result is an issue reproducer, not an
independent validation datum. Preserve the runnable reproducer and record
its NeqSim version and actual density values in the implementation PR.

## Alternatives considered

### Extend PR #3801 directly

PR #3801 has a bounded conversion-strategy scope and a passing CI head.
Adding the cross-subsystem density and numerical-behavior change before the
design is accepted would make its existing compatibility review unreliable.
Keep #3801 as a prerequisite and link a dedicated implementation PR to this
accepted NRC. A separately reviewed additive registry can be based on #3801
after merge.

### Delegate every getter to the present `RateUnit`

Rejected: one mutable density argument is insufficient for conversions
between actual and ideal-liquid volume, and the component volume getter has
different semantics.

### Patch only the gallon factor and alias lists

Rejected as a complete resolution: it leaves five dispatches and the
system/phase/component contexts free to drift again. A narrow emergency
correctness fix can be independently reviewed with a targeted migration note.

## Rollback strategy

Before release, keep #3801's compatible conversion APIs and defer this
cross-subsystem behavior change if the NRC is not accepted. After release,
retain a documented compatibility path through the supported migration
window and pin historical test baselines to the version and physical basis.
If new results fail independent validation, revert the new behavior at the
contract boundary rather than silently restoring legacy factors in the
shared registry.

## Decision record

- 2026-09-27: PROPOSED after review of issue #2998 and PR #3801 at
  `ac110f19e25cc66b8f6f5d3656a9f9bb7f3906db`. The reviewers must resolve
  gallon/barrel basis, component volume semantics, and multiphase actual
  volume before the implementation changes existing numerical results.
- Acceptance requires the subsystem owner, independent thermodynamics
  reviewer, two maintainer approvals for the incompatible public behavior,
  release/versioning decision, and documented migration and test plan.
