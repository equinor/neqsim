---
title: TwoFluidPipe Evidence Matrix and Supported Envelope
description: Evidence levels, supported operating envelope, executable examples, and explicit limitations for steady and transient TwoFluidPipe use.
---

# TwoFluidPipe Evidence Matrix and Supported Envelope

This page is the qualification boundary for **TwoFluidPipe**. It separates available code from
numerical verification and public experimental qualification. A completed calculation, finite
profile, small pressure change, or stationary pressure trace is not by itself evidence that the
model converged or represents the requested physics.

## Evidence labels

| Label | Meaning |
|---|---|
| **Implemented** | The public configuration and runtime path exist. This says nothing about convergence or accuracy. |
| **Numerically verified** | An executable test checks relevant conservation, convergence, boundedness, sensitivity, or refinement contracts. |
| **Experimentally qualified** | A reproducible public measured-data comparison passes its declared error gates, with source provenance and uncertainty or acceptance definitions. |
| **Not qualified** | A required numerical or experimental gate fails, is unavailable, or has not been run. The result must not be promoted by loosening the gate. |
| **Unsupported** | The configuration is rejected because the governing boundary or state information is missing. |

## Current evidence matrix

| Capability and configuration | Implemented | Numerically verified | Experimentally qualified | Evidence and use boundary |
|---|---:|---:|---:|---|
| Positive-flow steady gas/liquid pressure, holdup, thermal and terrain profiles | Yes | Yes | No general claim | Require the complete steady convergence report to be converged, every residual below its recorded tolerance (including total phase mass flux below `getMassFluxTolerance()`), and no pressure-floor or wall-clock termination. Repeat mesh sensitivity for the actual geometry. |
| Steady gas/oil/water on the compact 3 km, 10-degree uphill fixture | Yes | Yes | No | The 30/60-cell results differ by 0.538% in arrival pressure and 0.983% in mean liquid holdup. All three phases remain present and the final thermodynamic/holdup reconciliation is inside the unchanged 1e-4 tolerance. The unavailable historical 73.8 km case is not covered. |
| Liquid-rich unchanged-boundary transient with shared slug force balance, interfacial pressure, and coupled pressure/momentum | Opt-in | Yes | Not applicable | Over 1,800 s, inventory drift is 1.323% at 40 cells and 1.358% at 80 cells, below the declared 2% fixture gate, with total-mass closure. This does not qualify slug loads or another operating envelope. |
| Default liquid-rich unchanged-boundary transient | Yes | No | No | The recorded 1,800 s inventory drift is 5.757%, above the unchanged 5% gate. Do not infer default-mode qualification from the opt-in shared-force result. |
| Mohmmed et al. public horizontal air/water slug kinematics | Harness and data implemented | Conservation only | **Failed** | At 40 cells and 0.05 s outer steps, 3/9 comparisons pass, MARE is 1.0402, and maximum absolute relative error is 3.2147. The 40/80-cell and 0.05/0.025 s sweep is non-monotone; steady starts are unconverged and pressure-floor limited and transients clamp outlet backflow. The fixed 20% speed and 30% length/frequency gates remain unchanged. |
| Conservative severe-slugging characterization against the public Tengesdal envelope | Opt-in | Partial characterization | **Failed** | The 600 s run completes, but its 65.163 kPa pressure amplitude is below the 68.6 kPa lower gate, no required repeated settled cycle is detected, and pressure limiting remains active. It is not a slug-load or extreme-pressure design basis. |
| Flash-driven phase appearance/disappearance with phase and energy ledgers | Yes | Yes | No | Check gas/oil/water and total mass, transfer closure, temperature sensitivity, and latent-inclusive energy balance. Record EOS, mixing rule, composition, pressure, temperature, relaxation time, mesh, and time step. |
| Named-component advection for supported positive-flow boundaries | Yes | Yes | No | Require every named-component ledger, bounded normalized phase fractions, phase/component synchronization, and component-sum closure. |
| Conservative slug/film + named components + phase transfer + thermal balance | Yes | Yes | No | Merged in #3547. A closed four-cell wet-gas cooling case transfers 1.5855002575e-9 kg of water, records 0.0034892651 J latent heat and -0.0305006304 K mean temperature change on both outer-step partitions, while a seeded marker preserves accepted-time geometry. This is coupled-ledger evidence, not spontaneous slug initiation. |
| Reverse outlet inflow with named-component transport and no external composition | **Unsupported** | Fail-closed | No | Configuration is rejected in either setter order. The last interior composition is not a physical external boundary condition. |
| Multi-stage conservative slug + component + phase-transfer coupling | **Unsupported** | Fail-closed | No | Phase appearance inside an intermediate stage needs stage-local component inventories. The coupled four-way path is currently restricted to single-stage Euler. |

The Mohmmed source provenance, exact spreadsheet cells, point selection, missing-data policy, and
error definitions are maintained in
[TwoFluidPipe Reporting and Validation](../wiki/two_fluid_reporting_and_validation). Those fixed
engineering gates are not claimed as measurement confidence intervals; the paper does not publish
pointwise uncertainty intervals for the selected observations.

## Supported-use envelope

Use **TwoFluidPipe** only when the case fits a row above and its runtime evidence is retained:

1. Run steady initialization and inspect the complete immutable convergence report. A stationary
   pressure profile is insufficient if liquid split, holdup, thermodynamics, or total pressure drop
   remains outside tolerance, or if total phase mass flux does not close against the inlet.
2. For transient work, require requested elapsed time, phase and total mass balances, positivity,
   and all sticky pressure/momentum, pressure-limiter, rejected-substep, outlet-backflow, component,
   and energy diagnostics applicable to the selected configuration.
3. Repeat mesh and time-step sensitivity at the actual length, diameter, inclination, composition,
   phase split, thermal boundary, rates, and event duration. Evidence from one fixture does not
   define a universal operating envelope.
4. Treat seeded slug markers as transport/coupling probes. They do not demonstrate spontaneous
   slug initiation, sustained cycles, arrival statistics, or load distributions.
5. Use **PipeBeggsAndBrills** for a correlation-based steady or quasi-steady screen, not for
   conservative distributed line-pack dynamics. Use a separately qualified transient model or a
   controlled experimental study when the requested TwoFluidPipe row is failed or unsupported.

## High-throughput steady mass conservation (#3686)

`TwoFluidPipeSteadyMassFluxTest` reproduces the 3,100 m well with a 0.23 m diameter, 2,380 m rise,
20 sections, 205 bara and 90 degrees Celsius inlet, and the issue's synthetic SRK fluid and
stock-tank rates. The original 166.113217788 kg/s case lost 3.473846596 kg/s at the outlet section
because gas velocity was capped at 100 m/s. At 5% higher flow the deficit was 7.007383459 kg/s;
the 5% lower-flow case did not reach the cap.

The regression checks all three rates against phase-summed section mass flux with relative
tolerance 1e-10 and absolute tolerance 1e-12 kg/s. It also requires the original hydraulic and
thermodynamic residuals to pass. Separate gas, oil and water fixtures exercise the former steady
gas/liquid caps, and injected 2% and nonfinite reporting errors must prevent convergence even
when the hydraulic residuals have settled. The existing 30/60-cell three-phase fixture remains
the free-water and mesh-refinement check. This is conservation and numerical-convergence evidence,
not experimental or critical-flow qualification.

## Executable steady and transient example

The executable **TwoFluidPipeEvidenceExampleTest** in
src/test/java/neqsim/process/equipment/pipeline runs a 5 km,
0.30 m liquid-rich gas-condensate line at 50 kg/s, 60 bara and 50 degrees Celsius. It enables the
opt-in shared slug force balance, interfacial pressure, and coupled pressure/momentum before steady
initialization. The same object then advances ten one-second outer steps with unchanged boundaries.

The example fails unless:

- the complete steady report is converged without pressure-floor or wall-clock termination;
- the transient accepts exactly 10 s;
- maximum relative total-mass residual is at most 1e-10;
- inventory and outlet-pressure drift are each at most 2%;
- no outlet clamp, nonlinear failure, pressure-correction limiter, or rejected substep occurs.

Execute it from the repository root:

~~~bash
./mvnw -q -Dtest=TwoFluidPipeEvidenceExampleTest test
~~~

The exact Stage 5 execution values and source/head commit are recorded in the Stage 5 pull request
and the issue #3298 series ledger. These values are reproducibility evidence for the example, not
experimental qualification.

The prepared local execution on master
`5bb230e1c2e2227074ce4d20ec19d6be91b53ec7` produced:

| Quantity | Result |
|---|---:|
| Steady termination / iterations / tolerance | CONVERGED / 16 / 1e-4 |
| Steady outlet pressure | 57.62005697 bara |
| Initial mean liquid holdup | 0.269435835 |
| Initial total inventory | 72,424.765816 kg |
| Accepted transient duration | 10.0 s |
| Maximum total-mass residual | 1.92344e-11 kg |
| Maximum relative total-mass residual | 2.65574e-16 |
| Inventory drift | 0.00977583% |
| Outlet-pressure drift | 0.0% |
| Final mean liquid holdup | 0.269473910 |

The Stage 5 pull request and issue ledger bind these values to the final evidence-only head.

## Decision record

The five-stage evidence chain is recorded in issue
[#3298](https://github.com/equinor/neqsim/issues/3298):

- #3514: steady-to-transient consistency and the opt-in liquid-rich drift result;
- #3541: public Mohmmed benchmark reporting and failed qualification;
- #3543: honest three-phase steady convergence residuals and compact refinement evidence;
- #3547: merged coupled slug/component/phase/thermal ledgers and fail-closed unsupported combinations;
- the Stage 5 PR: this evidence matrix, executable example, and agent/skill routing.

Open or draft work is not a released capability. Confirm the target release contains the referenced
implementation before using its row.
