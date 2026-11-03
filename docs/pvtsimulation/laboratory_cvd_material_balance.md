---
title: "Laboratory CVD component accounting"
description: "Screen complete saturated-reference CVD observations without an EOS, preserve phase limits and qualify arithmetic against the primary Whitson-Torp NS-1 table."
---

# Laboratory CVD component accounting

`CvdMaterialBalance` reconstructs retained gas, liquid and named component amounts
from a complete laboratory gas-condensate constant-volume-depletion experiment.
It runs before EOS calibration and can be frozen as optional evidence in
`CalibratedPvtModel`. It does not fit or flash a fluid. Existing CVD simulation
paths do not invoke this operation. Existing checkpoint constructors retain their
behavior with no accounting report attached.

## Required physical protocol and units

The first observation is the initial saturated **all-vapor** sample at constant
temperature. Its liquid volume and cumulative production are exactly zero.
Subsequent absolute pressures strictly decrease. At each pressure, equilibrium
vapor is withdrawn at that pressure until the initial saturated cell volume is
restored. Withdrawn gas has that endpoint equilibrium vapor composition.

| Input | Required basis |
| --- | --- |
| Temperature | Constant K for the entire experiment |
| Pressure | Absolute Pa, including the reference dew pressure |
| Gas Z | Equilibrium vapor compressibility at that row |
| Liquid volume fraction $S_k$ | Liquid volume / initial saturated cell volume |
| Cumulative production $N_k$ | Withdrawn mol / initial mol, starting at zero |
| Vapor composition $y_{jk}$ | Mole fractions in the same unique named-component order; already sums to one |
| Optional final liquid analysis | Separately identified composition and justified absolute comparison bounds |

A standard gas volume is not a molar fraction without its stated conversion.
Surface standard conditions and separator yield are not needed for this
one-initial-mole accounting. They must still accompany any later surface PVT
conversion. An arbitrary starting cell, initial liquid, aqueous phase, variable
cell temperature, unreported sampling withdrawals, mixed produced gas of a
different composition, CCE or DLE is outside this gate's protocol.

## Equations and explicit liquid limits

On a one-initial-mole basis, with reference pressure $p_0$ and compressibility $Z_0$:

$$n_{g,k}=\frac{p_k}{p_0}\frac{Z_0}{Z_k}(1-S_k),\qquad n_{t,k}=1-N_k,\qquad n_{l,k}=n_{t,k}-n_{g,k}$$

For each named component $j$, accumulate every withdrawal in its original order:

$$n_{p,j,k}=\sum_{i=1}^{k}(N_i-N_{i-1})y_{ji},\qquad n_{r,j,k}=y_{j0}-n_{p,j,k}$$

$$n_{l,j,k}=n_{r,j,k}-n_{g,k}y_{jk},\qquad x_{jk}=\frac{n_{l,j,k}}{n_{l,k}}$$

The report retains signed inventories, component closure, raw inputs and all row
identities. Primitive-array accessors expose cumulative produced and retained
component amounts in `getComponentNames()` order, alongside stage withdrawals
and retained vapor/liquid/total amounts; returned arrays are defensive copies. The caller freezes an absolute inventory balance tolerance and a
minimum resolved liquid amount before comparison. Both are mol per initial mol;
they are numerical controls, not fitted measurement errors. Compositions must
already sum to one within $10^{-10}$; no normalization occurs. This structural
input bound does not replace the caller's inventory tolerance: the sums of
produced, remaining, vapor and liquid components must each close independently
against their corresponding total amounts. A shared composition bias cannot
pass by canceling only in the liquid residual. All four signed closure
diagnostics are exported.

| Liquid state | Composition availability |
| --- | --- |
| `ABSENT` | Zero liquid volume and inventory within the balance tolerance; composition unavailable |
| `RESOLVED` | Compatible component inventories, positive liquid volume and amount above the declared floor |
| `UNRESOLVED` | Positive liquid volume with too little liquid amount to resolve composition; report fails |
| `INCONSISTENT` | Negative/incompatible inventories or contradictory volume/amount; signed diagnostics retained |

`getLiquidMoleFractions()` throws for every state except `RESOLVED`; JSON uses
`null` for unavailable compositions. No extrapolated absent-phase property is
created. This phase-resolution rule does not qualify critical thermodynamics.
Malformed inputs, unsupported reference bases or unrepresentable arithmetic
throw before a report can pass. Physically contradictory data remain inspectable
in a failed report. `requireCompatible()` rejects a failed handoff.

## Executable analytic example

The two-component example is a rational accounting check, not an experimental
EOS calibration. At both depletion rows the retained liquid is 0.4 mol per
initial mol with composition 0.65/0.35. Its JUnit counterpart is
`CvdMaterialBalanceTest`, including serialization and rejected attachments.

```java
CvdMaterialBalance evidence = new CvdMaterialBalance(
    "sample-v1", "synthetic CVD complete experiment", "public analytic inputs", 350,
    new String[] {"A", "B"},
    new CvdMaterialBalance.Observation[] {
        new CvdMaterialBalance.Observation("sat", 100e5, 1, 0, 0, new double[] {0.8, 0.2}),
        new CvdMaterialBalance.Observation("step-1", 50e5, 1, 0.2, 0.2, new double[] {0.9, 0.1}),
        new CvdMaterialBalance.Observation("step-2", 25e5, 1, 0.2, 0.4, new double[] {0.9, 0.1})},
    1e-12, 1e-8,
    new CvdMaterialBalance.FinalLiquidAnalysis(
        "independent analytic final liquid",
        "exact rational reference with 1e-12 absolute fraction numerical bound",
        new double[] {0.65, 0.35}, new double[] {1e-12, 1e-12}));
evidence.requireCompatible();
double[] finalLiquid = evidence.getStage(2).getLiquidMoleFractions();
String audit = evidence.toJson();
```

For independently calibrated models of the same identified sample, pass this
report as the last argument of the new nine-argument `CalibratedPvtModel`
constructor (after optional gas-density evidence). The complete report is frozen
in `getProvenance()` and held-out comparisons. Wrong-sample or failed evidence
cannot attach. Null preserves existing behavior. Screening evidence does not
implicitly become a calibration or holdout experiment. Keep whole laboratory
calibration and holdouts independent, and keep both separate from reservoir
volume/support fitting and field holdouts. The checkpoint schema includes the
optional passing report. Java and JPype callers use the same object API;
discovery does not imply a new stateless MCP operation.

## Primary published NS-1 benchmark

Whitson and Torp, *Evaluating Constant-Volume Depletion Data*, JPT 35(3),
610–620 (1983), [DOI 10.2118/10067-PA](https://doi.org/10.2118/10067-PA),
Table 1 on printed page 614, reports a North Sea rich-condensate recombined
separator sample at 280°F (410.9277777777778 K). Seven pressures are absolute psia.
The vapor analyses are laboratory-based **smoothed/adjusted** values. The
calculated final liquid column is derived by material balance; it is not an
independent measurement or an EOS holdout. The experimental final liquid is a
separate observation, with incomplete published measurement uncertainty.

The [source receipt and runnable benchmark](../../src/test/resources/neqsim/pvtsimulation/cvd-material-balance/README.md)
retain all seven admitted rows, 11 components, both final-liquid columns, an
offline standard-library 50-digit decimal reference and complete six-stage
outputs. Tests compare 66 liquid compositions and 198 produced/remaining/liquid
component inventories with that independent reference. Maximum final calculated
composition discrepancy from the paper is **0.000447811 mol%**, below the frozen
**0.0005 mol%** half-last-digit tolerance. This qualifies the accounting arithmetic.

A separate conservative box screen propagates only printed resolution: pressure
±0.05 psia, Z ±0.0005, vapor composition ±0.005 mol%, cumulative production
±0.0005 percentage point, liquid volume ±0.05 percentage point; protocol zeros
remain exact. It permits independent combinations and ignores correlations and
composition-sum constraints. The experimental final analysis receives its own
±0.005 mol% printed bin. These are **not measurement sigmas or confidence bounds**.

| Final component | Reconstructed mol% | Rounding-only reconstructed interval mol% | Experimental mol% |
| --- | ---: | ---: | ---: |
| Methane | 10.7041022 | 10.3185705–11.0912170 | 12.42 |
| C7+ | 72.8151410 | 72.5109548–73.1209854 | 71.01 |

Both experimental values remain outside these conservative printing-only
intervals. The report retains those failed comparisons and blocks attachment
when that analysis is included. This does not establish that the experiment is
invalid: unreported measurement uncertainty, smoothing and sample-analysis
provenance require reconciliation. Do not delete the analysis or widen bounds
to claim measured acceptance. The no-analysis arithmetic report makes no
experimental-comparison claim.

## Remaining acceptance and related capabilities

Use the [native CCE/CVD/DLE handoff](depletion_pvt_generation.md) for EOS protocol
replay and the [gas-density gate](laboratory_gas_density_consistency.md) for
same-state density identities. This NS-1 receipt supplies primary numerical PVT
accounting; it does not supply independently tuned EOS accuracy, whole-experiment
holdouts, measured production/injection/average-pressure history, held-out field
GOR/composition, coupled aquifer qualification or multi-tank matching.

The 2025 Tran rescaled-CVD/density method remains a distinct research dependency:
its full equations, definitions and independent numerical benchmark are still
needed. Fresh retrieval of the [publisher full-text route](https://www.sciencedirect.com/science/article/pii/S2949908925000159)
and [46-page SSRN preprint delivery](https://papers.ssrn.com/sol3/Delivery.cfm/a3fff389-ff06-49ec-acf6-03808f3d25a6-MECA.pdf?abstractid=4932591&mirid=1)
returned HTTP 403 on 2026-11-03 UTC; public metadata/abstract access is insufficient.
Missing material includes the complete RCVD/FPM/density equations and conventions,
fluid composition/EOS/relative-permeability inputs, variable BHP and boundary
schedules, and numerical benchmark outputs. Nothing here implements it from an abstract. In-place estimates remain
separate from recoverable reserves. Campaign [#4740](https://github.com/equinor/neqsim/issues/4740)
remains open until the complete merged laboratory-to-field acceptance is shown.
