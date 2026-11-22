---
title: "Adsorbers: Legacy SimpleAdsorber Boundary"
description: "Source-verified status and migration routes for NeqSim's unqualified legacy MDEA-loading prototype."
---

The `SimpleAdsorber` class is a legacy, currently unqualified MDEA-loading prototype. It is not
NeqSim's solid-adsorption model, and it does not have executable evidence for use as a gas-treating
absorber. Do not use it for new engineering work.

## Choose a maintained model

| Engineering question | Model family | Maintained guide |
| --- | --- | --- |
| Fixed-bed adsorption, breakthrough, mercury removal, or PSA/TSA/VSA cycles | `AdsorptionBed`, `MercuryRemovalBed`, or `PressureSwingAdsorptionBed` | [Adsorption beds](adsorption_bed) |
| Gas-liquid absorption or stripping | `AbsorptionColumn`, `RateBasedPackedColumn`, `SimpleAmineAbsorber`, or `SimpleTEGAbsorber` | [Absorbers and strippers](absorbers) |
| Historical MDEA-loading prototype inspection | `SimpleAdsorber` | This legacy boundary only |

The maintained model's assumptions and executable tests remain authoritative. Select a model from
its own guide instead of translating the old `SimpleAdsorber` snippets.

## What the current implementation does

The two-argument constructor stores the same inlet object in two inlet slots and creates two
outlet clones. Outlet 0 remains a clone of the feed. Outlet 1 starts from another feed clone, then
adds MDEA and water and initializes electrolyte reaction handling.

During `run(UUID)`, outlet 0 is cloned from the inlet again. The calculation iterates the MDEA and
water inventory in outlet 1 toward a CO2-to-amine loading target. It does not remove CO2 from
outlet 0. Consequently:

- outlet 0 must not be labelled treated or sweet gas;
- outlet 1 is not an independently supplied solvent stream;
- the two outlets must not be interpreted as a conventional gas product and rich-solvent product;
- `getMassBalance(String)` is not evidence of a conventional absorber material balance because
  both inlet slots refer to the same feed object.

The source currently exposes `getOutletStream(int)`. The older `getOutStream(int)` accessor is
deprecated. The only public loading-target setter is the misspelled legacy method
`setAproachToEquilibrium(double)`; it must not be described as a gas-removal-efficiency
specification.

## Inactive configuration fields

The class stores values for number of stages, theoretical stages, stage efficiency, HTU, and NTU,
but `run(UUID)` does not read those fields. Their setters therefore do not configure an active
stage or transfer-unit calculation in the current implementation.

Although `getMechanicalDesign()` returns an `AdsorberMechanicalDesign`, no enabled
`SimpleAdsorber` regression qualifies a diameter, height, weight, or cost result. Do not use that
path for engineering sizing.

## Validation status

The repository's only `SimpleAdsorberTest.testRun` method is disabled with an explicit
"until ... SimpleAdsorber is fixed" reason. Its process execution is commented out. The class also
writes directly to the console internally. For those reasons, this page intentionally contains no
runnable `SimpleAdsorber` example and makes no numerical performance claim.

The maintained solid-adsorption replacement has enabled construction, geometry, steady-state,
transient, validation, and reporting tests in `AdsorptionBedTest`. The maintained gas-liquid
models and their evidence are listed in the absorber guide.

## Source and test evidence

- [SimpleAdsorber source](../../../src/main/java/neqsim/process/equipment/adsorber/SimpleAdsorber.java)
- [Disabled SimpleAdsorber regression](../../../src/test/java/neqsim/process/equipment/adsorber/SimpleAdsorberTest.java)
- [AdsorptionBed source](../../../src/main/java/neqsim/process/equipment/adsorber/AdsorptionBed.java)
- [Enabled AdsorptionBed regressions](../../../src/test/java/neqsim/process/equipment/adsorber/AdsorptionBedTest.java)

## Related documentation

- [Adsorption beds](adsorption_bed) — fixed-bed adsorption and cyclic operation
- [Absorbers and strippers](absorbers) — gas-liquid mass-transfer model selection
- [Equipment catalog](equipment_catalog) — current concrete equipment inventory
- [Chemical reactions](../../chemicalreactions/) — reaction and equilibrium boundaries
