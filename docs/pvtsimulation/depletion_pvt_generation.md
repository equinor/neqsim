---
title: "Native CCE, CVD and DLE laboratory PVT handoff"
description: "Closed-mass CCE evidence and bounded depletion-PVT generation with frozen original/tuned EOS, immutable native snapshots and fixed separator provenance."
---

# Native CCE, CVD and DLE laboratory PVT handoff

## Closed-mass CCE without an inferred saturation reference

Use `DepletionPvtHistoryGenerator.replayNative` with `Path.CCE` for a complete dry
closed-mass constant-composition expansion (also called CME). The selected original
or tuned EOS is restored independently; its complete component inventory remains
in the cell at every pressure. Gas is **not** removed. Both native equilibrium and
retained snapshots describe that same whole cell. `hasWithdrawal(row)` is false;
`getWithdrawalProducts(row)` throws because no laboratory withdrawal exists.

This route invokes `ConstantMassExpansion.runNativeExpansion`, which TP-flashes
the closed cell on the declared pressure path and returns immutable snapshots.
It does not search for saturation or populate the legacy CME relative-volume,
Y-factor, formation-factor or transport arrays. Inspect actual phase presence and
call `initProperties()` on restored states before using properties. Absolute cell
volumes are in m3; any reported relative volume must name an actual reference row.
Do not relabel that reference as a measured or predicted saturation point.

Supply the same immutable sample/model, complete experiment identity, temperature
in K, decreasing pressures in bara and fixed `PvtSeparatorPath` as the native
depletion route below. Pressure points must remain above the first separator.
`getRetainedProducts(row)` evaluates the whole cell through that train; these
products are a declared surface process, not fluids withdrawn during the CCE.
Named closed-cell component closure is checked at `1e-10 * max(1, initial mol)`.
Numerical closure error is not converted into invented CCE production. A failed
flash propagates its original cause and pressure context; no successful result or
partial snapshot array is returned.

Oil-only, gas-only and nondegenerate gas/oil CCE states are represented without
invented absent-phase properties. The qualified synthetic closed-mass gas/oil/gas
path preserves all material through phase appearance and disappearance. This is
different from gas-withdrawal CVD, whose later single-phase volume-restoration
tail is separately qualified below. Wet and critical-degenerate paths remain rejected.

`NativeCceHandoffTest` verifies provenance/ownership, both EOS selections, no-flow
and phase limits, failures, named cell/separator conservation, independent pure
SRK pressure-equation roots and six binary SRK coexistence states. The bounded
[independent reference](../../src/test/resources/neqsim/pvtsimulation/native-cce/README.md)
contains explicit inputs and outputs. These are analytical software checks, not
measured laboratory calibration/holdouts or an accepted laboratory-to-field chain.
The native CVD saturation-reference refinement below remains separate from closed-mass CCE.

## Native compositional handoff through phase appearance

Use `DepletionPvtHistoryGenerator.replayNative` when a dry CVD/DLE path includes
an absent reservoir phase. It reuses the existing solver and immutable
`DepletionSnapshot` records, returning actual equilibrium/retained EOS states and
separator products. It does not assign Bo/Bg/Rs/Rv/Bw or transport properties to
an absent phase, and does not produce a generalized black-oil history.

Supply independently frozen original/tuned models, the selected version
(`false` original / `true` tuned), complete experiment identity, temperature in K,
at least two decreasing pressures in **bara**, and an immutable
`PvtSeparatorPath`. Depletion pressures must exceed the first separator pressure.
Its final temperature/pressure define the product-volume basis; other bases are
retained explicitly rather than relabeled as 15 C volumes.

- DLE can begin with oil only and can remain a closed liquid-expansion path.
  Equilibrium is captured before gas removal where gas appears. Initial and
  retained oil are required; a gas-only DLE feed is rejected.
- CVD can begin with gas-only states before a sampled gas/oil boundary, then
  follow the volume-restored two-phase path. A sampled boundary is required.
  After liquid disappears, gas continues to be withdrawn below the saturation
  reference to restore the fixed cell volume. The retained gas composition is
  conserved during each single-phase withdrawal; absent oil has no assigned PVT.
- Wet/other-phase samples remain unsupported. Gas/oil composition vectors with
  L1 distance at most 1e-8 are rejected as an unqualified critical-degenerate
  split. This rejection bound is not a fit parameter or a near-critical accuracy
  claim.

`NativeResult` retains both immutable checkpoints, the selected EOS, experiment
identity, fixed separator basis and actual pressure-ordered snapshots. Restore a
snapshot, inspect `hasPhaseType("gas")` / `hasPhaseType("oil")`, then call
`initProperties()` before reading density or transport properties. Returned EOS
states are independent copies.

`getRetainedProducts(row)` processes the **whole retained cell** through the
separator train. `hasWithdrawal(row)` is false if no fluid was withdrawn; then
`getWithdrawalProducts(row)` throws. Otherwise it returns **interval**, not
cumulative, products. Reservoir phase absence and zero withdrawal are distinct.
A completed separator can physically have zero volume of one product phase;
this does not define a missing reservoir phase's formation factor.

Native chemical component closure is checked against the original named sample,
including sample-to-first-row withdrawals, at
`1e-10 * max(1, initial component mol)`. Separator component closure is checked
by `PvtSeparatorPath`. No conservation of surface-volume pseudo-components is
assumed. These are laboratory protocol outputs, not measured field production.
A DLE cell changes volume and cannot be relabeled as a fixed-pore-volume reservoir.

The following synthetic example is exercised by `NativeDepletionHandoffTest`.
Identical checkpoints demonstrate API use; they do not establish independently
calibrated laboratory accuracy. Real workflows preserve separate calibration and
whole-experiment holdouts, distinct from reservoir pressure/GOR/composition holdouts.

```java
SystemInterface original = new SystemSrkEos(323.15, 500);
original.addComponent("methane", 0.7);
original.addComponent("n-decane", 0.3);
original.setMixingRule("classic");
original.useVolumeCorrection(false);
original.init(0);
CalibratedPvtModel model = new CalibratedPvtModel(original, original.clone(),
    "synthetic native sample", "original SRK v1", "accepted SRK v1",
    "synthetic laboratory protocol, not measurements", new String[] {"CCE-1"});
PvtSeparatorPath separator = new PvtSeparatorPath(new double[] {10, 1.0133},
    new double[] {303.15, 288.71},
    "synthetic two-stage train, final 15.56 C / 101.33 kPa absolute");
DepletionPvtHistoryGenerator.NativeResult result =
    DepletionPvtHistoryGenerator.replayNative(model, true, MaterialBalancePvtHistory.Path.DLE,
        "whole synthetic DLE-1", 323.15, new double[] {500, 300, 200, 100, 50}, separator);
boolean initialWithdrawal = result.hasWithdrawal(0); // false: closed oil-only state
SystemInterface initialCell = result.getSnapshots()[0].getRetainedFluid();
double[] retainedProducts = result.getRetainedProducts(0).getVolumesM3();
```

DLE equilibrium, post-saturation and final stock-tank flash failures now propagate
their original cause and stage context. Failed runs cannot return a successful
native handoff. Saturation-scan failures also propagate; successful numerical
paths are unchanged.

Qualification covers phase appearance, closed-oil limits, selected original/tuned
EOS parameters, named component and separator closure, non-default product bases,
immutability/serialization and injected flash failures. A pure n-decane test
independently bisects the SRK pressure equation using the declared critical data,
Soave alpha and library EOS gas constant, with 1e-8 relative volume acceptance.
This is numerical EOS consistency, not measured laboratory-to-field accuracy,
wet/critical qualification or recoverable-reserves acceptance.

### Independent binary qualification and the CVD saturation reference

The [reproducible binary SRK audit](../../src/test/resources/neqsim/pvtsimulation/native-depletion/README.md)
adds independent phase-equilibrium, lever-rule and gas-withdrawal predictions for
nine synthetic DLE/CVD states. The historical legacy phase-count scan selects
359.241739 bara versus the independent upper dew of 359.245071 bara; its CVD
cell volume misses the frozen `1e-6` relative bound by `6.530e-6`.

Native `replayNative` now enables `ConstantVolumeDepletion.setRefineSaturationConditions(true)`.
The scan first selects a coarse upper gas/oil-to-gas bracket below 1000 bara.
A cloned two-phase warm state then feeds the existing constant-duty pressure
solver with an incipient liquid fraction of `1e-10`. Acceptance requires the
same coarse branch (`1e-5 bara` numerical allowance), normalized nonnegative
phase compositions and component reconstruction within `1e-8`, nontrivial
composition distance above `1e-8`, and maximum component log-fugacity mismatch
at most `1e-8`. The accepted pressure is reflashed with the original whole-cell
inventory before defining the CVD reference volume. No EOS input is fitted.
Missing/unsupported brackets and failed equilibrium gates throw; there is no
fallback to the scan or an unrelated lower-pressure dew root.

The full independent CVD check now passes: maximum relative retained-volume
error `4.730e-11`, inventory/withdrawal error `4.070e-11 mol`, phase mole-fraction
error `4.530e-14`, and component closure `5.552e-17 mol` for the one-mole
reference. The original `1e-6` volume/composition/inventory and `1e-10 mol`
closure bounds are unchanged. Three sample scales also have regression coverage.
DLE remains within `1.066e-13` relative volume error.

The direct CVD default and modified-black-oil `generate` route retain their
legacy scan and its precision limitation. The opt-in refinement supports dry,
nondegenerate upper dew boundaries; pure fluids, wet paths, oil bubble boundaries,
unbracketed/1000-bara domains remain outside
its qualified domain. These are analytical synthetic consistency checks, not
measured laboratory/field accuracy or full phase-boundary qualification.

## Bounded modified-black-oil conversion

`DepletionPvtHistoryGenerator.generate` evaluates pressure-aligned
`Rs`, `Rv`, `Bo`, `Bg` and `Bw` records from a frozen `CalibratedPvtModel`.
It runs the existing constant-volume-depletion or differential-liberation solver.
The retained composition changes as gas is withdrawn. Independent flashes of
the original composition are not used as depletion states.

This is a bounded modified-black-oil interpretation. It does not make the two
surface pseudo-components exact conserved chemical components. Keep the complete
result and inspect the conversion error before using its
`MaterialBalancePvtHistory` with measured reservoir production.

## Inputs and supported domain

Supply the calibration bundle, `Path.CVD` or `Path.DLE`, protocol/software
reference, reservoir temperature in K, and at least three strictly decreasing
pressures in **bara**. The accepted depletion sample must contain no water.
Each interpreted pressure must have both hydrocarbon oil and gas. An absent
phase's properties are rejected rather than filled by an arbitrary value or
extrapolated to an incipient phase. Consequently this version cannot generate a
complete above-saturation-to-depletion table. Near-critical degenerate
`Rs*Rv` splits are also rejected by the generalized-balance checks.

The fixed separator stages use pressure in bara and temperature in K. Pressures
must decrease and end at **1.01325 bara and 288.15 K**. Every reservoir pressure
must exceed the first separator pressure. Liquid advances to the next stage;
each stage's offgas is separately flashed to standard conditions. Both dry gas
and any condensate formed by that offgas are accounted for. All stages and
outputs must conserve every native EOS component.

`Bw` requires a separately accepted pure-water EOS and its provenance reference.
Its native checkpoint is retained in `Result.getWaterFluid()`. It must contain
water only and remain one liquid phase at reservoir and standard conditions.
`Bw` is the standard-to-reservoir density ratio. This reference does not model
brine, dissolved gas, water exchange with hydrocarbons or a water-containing
depletion experiment. Do not pass an unqualified water model merely to complete
the argument list.

## Evidence and acceptance

Both depletion solvers can opt into native recording with
`setRecordDepletionSnapshots(true)`. The default remains false to preserve their
normal memory and runtime cost. `getDepletionSnapshots()` returns immutable
per-pressure evidence. Restored fluids and amount arrays are independent copies.
CVD records its equilibrated volume-restored cell. DLE records equilibrium
before gas removal and retained liquid afterwards. Component withdrawals are
differences between consecutive retained inventories, including the original
sample for the first step. Solver results are unchanged when recording is used.

The separator calculation is shared with `PvtSeparatorPath`; see
[phase-selective production and surface accounting](reservoir_phase_production.md).
The modified-black-oil generate method retains its existing dry/two-hydrocarbon-phase restrictions.

The generator interprets oil and gas from each actual equilibrium snapshot
through the same surface process. Reservoir and surface volumes consistently
include the EOS volume correction. Formation factors are reservoir m3/Sm3,
`Rs` and `Rv` are Sm3/Sm3, and viscosities are Pa.s.

For each surface component, the reported relative residual is

$$r_k=\frac{S_0-S_{p,k}-S_{r,k}}{S_0}$$

Here $S_0$ is the separator-evaluated first retained cell, $S_{p,k}$ is cumulative
separator-evaluated experiment withdrawal after that point, and $S_{r,k}$ is the
remaining cell processed by the same path. Both surface oil and surface gas
must be defined at the origin. Native chemical-component conservation is
checked separately before this approximation error is evaluated.

The caller supplies a maximum relative error strictly between zero and one,
plus an engineering justification reference. The generator rejects a handoff
when either surface-component error exceeds it. There is no default field
acceptance tolerance. `Result` retains the bound, justification, actual maximum,
signed residual arrays, experiment withdrawals, snapshots and the accepted PVT
handoff. The bound is neither a confidence interval nor an estimate of PVT
measurement uncertainty.

The first retained cell defines zero cumulative experiment withdrawals in the
result. Earlier sample-to-first-grid withdrawal remains visible in snapshot
zero. These laboratory withdrawals are not measured field production. A DLE
cell changes volume, so its withdrawal history must not be treated as a
fixed-pore-volume reservoir depletion case.

## Qualification and remaining work

`DepletionPvtHistoryGeneratorTest` checks native component balances, existing
CVD/DLE solver agreement, changed retained composition, independent single-stage
phase flashes, independent water-density evaluation, separator-path effects,
input/output ownership, serialization and explicit rejection gates. These are
synthetic and numerical integration checks. They do not establish laboratory
or field accuracy, above-saturation extrapolation, critical closure or the
validity of the supplied EOS and acceptance tolerance.

The phase-flash interpretation follows Whitson and Torp (1983),
[SPE 10067-PA](https://doi.org/10.2118/10067-PA), available from the
[author's NTNU course archive](https://www.ipt.ntnu.no/~curtis/courses/PVT-Flow/2016-TPG4145/Handouts/CVD-Whitson-Torp.pdf).
The [Whitson modified-black-oil documentation](https://manual.pvt.whitson.com/methods/child_models/black_oil_tables/)
also explains the fixed surface-process and surface-density approximation.

See [reservoir material balance](reservoir_material_balance.md) for the measured
history workflow and [issue #4740](https://github.com/equinor/neqsim/issues/4740)
for the broader qualification contract.


## Gas-only CVD continuation

A CVD cell remains at its selected saturation volume after the final liquid
phase evaporates. At a lower pressure, withdraw the present gas in its actual
component proportions until the cell returns to that volume. In a single phase
at fixed temperature, pressure and composition, volume is extensive in moles;
therefore the remaining fraction is the target volume divided by pre-withdrawal
volume. The existing bounded gas-withdrawal loop performs this operation and
reflashes/checks the cell. Above the saturation reference pressure, single-phase
rows remain closed expansion states with zero withdrawal.

This correction also applies to direct legacy CVD; it retains that route's
selected scan reference and precision limitation. Native CVD uses the qualified
refined upper dew. It does not assign absent oil density, Bo or Rs. The modified
black-oil route still requires both phases and rejects these rows.

The dry methane/n-butane275K synthetic reference covers500/200/100/50/20/5/2bara,
including two-phase depletion and a gas-only tail. Independent SRK cubic roots,
fugacity equality, two-phase algebraic gas removal and single-phase extensivity
provide numerical references. This is model consistency, not measured lab/field
accuracy, critical behavior, water support or recoverable reserves.
