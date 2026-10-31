---
title: "Generate pressure-path PVT from CVD and DLE"
description: "Bounded modified-black-oil generation from a frozen laboratory EOS, native depletion snapshots, a fixed multistage separator process and a separate accepted water EOS."
---

# Generate pressure-path PVT from CVD and DLE

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
