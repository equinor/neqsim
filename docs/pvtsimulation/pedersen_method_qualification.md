---
title: Reservoir-Fluid Method Qualification
description: Pedersen topic coverage, current NeqSim calculation routes, independent swelling verification and remaining validation work
---

## Purpose and evidence boundary

NeqSim implements many calculations used to study petroleum reservoir fluids.
Their existence does not establish accuracy for every fluid or operating range.
This matrix connects the topic scope of Pedersen, Christensen and Shaikh,
*Phase Behavior of Petroleum Reservoir Fluids*, third edition (2024), to current
source and representative tests. The [publisher's contents](https://www.routledge.com/Phase-Behavior-of-Petroleum-Reservoir-Fluids/Pedersen-Christensen-Shaikh/p/book/9781138313811)
establish the book scope. Full chapter numerical examples were not available for
this audit; no claim is made that every example in the book has been reproduced.

The initial audit used master `98d185ded48c5c0c12b2faec374544b6b6b043b1`
on 24 October 2026. The swelling correction and independent numerical check below
are additions to that baseline. Follow the complete work in
[roadmap #4564](https://github.com/equinor/neqsim/issues/4564).

Use three distinct evidence levels:

- **Implemented:** a verified source/API route exists, with stated limitations.
- **Numerically verified:** analytical, independent same-model or conservation
  checks pass for specified inputs and tolerances.
- **Experimentally validated:** predictions agree with traceable measurements
  over a documented range, with uncertainty and a separate hold-out set when tuned.

A numerical fixture captured from earlier NeqSim output is a regression guard.
It is useful but does not independently establish experimental accuracy. The
representative suites in the matrix are evidence pointers, not a claim that all
of those suites or all physical models have been requalified in this change.

## Capability and evidence matrix

| Book topic | Current implementation and representative evidence | Status and next qualification |
| --- | --- | --- |
| Fluid types (1) | Compositional `SystemInterface` fluids, named components, TBP and plus fractions | Implemented inputs; select public black-oil, volatile-oil, condensate and gas cases with complete model definitions |
| Sampling and analysis (2) | Fluid readers, composition inputs and recombination workflows; `WhitsonPVTReaderTest` | Software routes exist; certify composition basis, sample quality, recombination ratios and component balances per dataset |
| Laboratory experiments (3) | `ConstantMassExpansion`, `ConstantVolumeDepletion`, `DifferentialLiberation`, `MultiStageSeparatorTest`, `SwellingTest`; corresponding tests | Core routes implemented; swelling correction is verified below. Equilibrium contacts and gas revaporization need explicit capability/benchmark mapping |
| EOS (4) | SRK, PR, CPA, PC-SAFT, volume correction and nonclassical mixing-rule implementations | Broad model surface; fix the complete EOS definition before comparing results. No assertion that different models give identical results |
| C7+ characterization (5) | `PlusFractionModel`, `TBPfractionModel`, `LumpingModel`, `PseudoComponentCombiner`; `PedersenPlusModelSolverTest` | Implemented, with tracked MW-moment and covolume-override gaps; qualify conservation and lump-count/common-slate sensitivity |
| Flash and envelopes (6) | `ThermodynamicOperations`, saturation and envelope solvers; saturation tests and [envelope guide](phase_envelope_guide.md) | Numerical regressions exist; require stability, fugacity and EOS-volume closure, critical/retrograde cases and explicit failure status |
| PVT simulation (7) | CCE/CVD/DL/separator arrays and [laboratory guide](pvt_lab_tests.md) | Implemented; validate measured relative volume, dropout, Bo/Bg/Rs, densities, GOR and standard-state conventions |
| Thermal properties (8) | Heat capacities, enthalpy/entropy, PH/PS flashes and JT coefficient | Implemented property/flash routes; compare caloric data and derivatives with consistent ideal-gas and formation references |
| Regression (9) | `PVTRegression`, data classes and `PVTRegressionTest` | CCE/CVD/DLE/separator/viscosity routes exist. Enum values alone do not prove target support; see #4567 |
| Transport (10) | PFCT/CSP and LBC viscosity, conductivity models, `ViscositySim`; `PFCTViscosityMethodTest` | Implemented; model-to-model checks must be supplemented with measured gas and oil viscosity/conductivity over the intended range |
| Wax (11) | WAT, `WaxFractionSim`, `WaxCurveCalculator`; `WaxModelComparisonTest` | Model-specific routes and regression repairs exist; require measured appearance/precipitation curves and solid balances |
| Asphaltenes (12) | Characterization, onset flashes and quantitative/screening models; onset and stability suites | Partial qualification by method; field-risk classification is not a measured equilibrium onset or precipitated-amount benchmark |
| Hydrates (13) | Formation T/P and inhibitor flashes; hydrate and brine assessment suites | Selected literature assessment exists; qualify the actual gas/brine/inhibitor combination and distinguish equilibrium from kinetics |
| Depth variation (14) | `TPgradientFlash`, `TPgradientFlashTest` | Implemented with regression fixtures; independently validate pressure/composition profiles and gravity/thermal-gradient assumptions |
| Gas-injection EOR (15) | Bubble-branch `SwellingTest`, `SlimTubeSim`, `MMPCalculator` | Partial: critical/dew swelling and rigorous conservative MMP remain open (#4565, #4566) |
| Water and inhibitors (16) | CPA/electrolyte phase calculations, inhibitor and mutual-solubility routes | Model- and dataset-specific qualification required; check all phase contents, salts, inhibitor basis and balances |
| Mineral scale (17) | Empirical `ScalePredictionCalculator`, activity models and `MultiMineralScaleEquilibrium`; `ScaleKspLiteratureBenchmarkTest` | Selected Ksp/solution benchmarks exist; distinguish screening, high-salinity activities and coupled quantitative precipitation |

## Reproduced swelling defect and correction

For an SRK oil containing 0.2 mol methane and 0.8 mol nC10, constructed at
298.15 K, the previous calculation reported **1.052108003210363** at zero gas
addition when the requested experiment temperature was 353.15 K. The reference
volume had been calculated before applying that temperature. It also rescaled
the caller's injection template and ignored the temperature unit at later stages.
The two original regression tests failed before the implementation change.

The corrected calculation applies one temperature to the reference and every
addition, uses density-corrected volumes, clones gas additions, conserves the original-oil mole basis, and starts
each run from the constructor's initial oil snapshot. Invalid schedules are
rejected. A failed bubble-point calculation throws with stage/cause information;
no completed result arrays are published. See
[issue #4561](https://github.com/equinor/neqsim/issues/4561).

The [independent reference generator](../../devtools/generate_srk_swelling_reference.py)
uses Python's standard library without importing NeqSim. It solves SRK cubic
volume roots analytically, iterates incipient-vapor fugacity coefficients and
bisects pressure to enforce equilibrium. It rejects equal-composition trivial
roots and checks the pressure equation for both phases. This is an original
synthetic numerical benchmark with frozen inputs, not measured petroleum data.

| Input | Frozen value |
| --- | --- |
| EOS / mixing / shifts | Original Soave SRK alpha; classical quadratic/linear mixing; all kij = 0; volume translation disabled |
| Initial oil / injection | 0.2 mol methane + 0.8 mol nC10 / pure methane |
| Temperature | 353.15 K |
| Methane Tc / Pc / acentric factor | 190.56 K / 45.99 bara / 0.0115 |
| nC10 Tc / Pc / acentric factor | 617.70 K / 21.1 bara / 0.4923 |
| Gas constant | 8.3144621 J/(mol K) |
| Addition basis | Cumulative mol% of the original oil, not final-mixture mol% |

| Cumulative addition, mol% | Independent bubble pressure, bara | Independent volume ratio |
| --- | ---: | ---: |
| 0 | 51.2837097379 | 1.0000000000 |
| 10 | 73.2010992783 | 1.0301020972 |
| 20 | 93.1415075418 | 1.0602317416 |

`SwellingTestTest` requires agreement within 0.0001 bara and 0.000001 in volume
ratio. The generator's maximum absolute log-fugacity residual is below 2e-13;
its maximum relative EOS-pressure residual is below 2e-14. The Java tests also
cover Celsius/Kelvin/default temperature, repeat runs, schedule copying and
duplicate stages, input preservation, invalid inputs and explicit saturation
failure. Mixed methane/ethane injection tests check every component and total
mass for both SRK and PR, including ethane absent from the initial oil.

A separate failing regression exposed omitted liquid-density volume shifts.
For constant methane/nC10 shifts of 0.00001/0.00003 m3/mol, the independently
shifted 20 mol% volume ratio is **1.0578891507452128**; the former implementation
still returned the unshifted **1.0602317415732487**. The corrected API includes
these shifts and the test also verifies that saturation pressures are unchanged.

These tolerances verify numerical agreement for identical model inputs. They
are not universal accuracy tolerances for measured reservoir-fluid properties.

## Open implementation work and existing dependencies

| Work | Tracking |
| --- | --- |
| Swelling temperature, reference volume and reusable input repair | [#4561](https://github.com/equinor/neqsim/issues/4561) |
| Critical transition and dew-branch swelling, stage CCE/dropout | [#4565](https://github.com/equinor/neqsim/issues/4565) |
| Conservative displacement, complete injection components and qualified MMP methods | [#4566](https://github.com/equinor/neqsim/issues/4566) |
| Unsupported regression experiments currently dispatch to zero | [#4567](https://github.com/equinor/neqsim/issues/4567) |
| Plus-fraction molecular-weight moment conservation | [#4549](https://github.com/equinor/neqsim/issues/4549) |
| Persistent per-component PR covolume overrides | [#4548](https://github.com/equinor/neqsim/issues/4548) |
| Named oil density/viscosity in CCE | [#4547](https://github.com/equinor/neqsim/issues/4547), active [PR #4556](https://github.com/equinor/neqsim/pull/4556) |
| Heavy-TBP validity and SRK alpha inversion | [PR #4540](https://github.com/equinor/neqsim/pull/4540) |
| Flash/stability and electrolyte qualification | [#2937](https://github.com/equinor/neqsim/issues/2937), [#3144](https://github.com/equinor/neqsim/issues/3144) |

Closed wax/onset defects #3709, #3914 and #4083 are historical regression evidence,
not newly open blockers. Retain their tests while adding measured-data coverage.

## How to establish comparable results

Freeze the full model before comparing calculations: component properties,
composition and normalization, heavy-fraction distribution/lumping, EOS and alpha
function, mixing rule and BIPs, volume translation, transport model, enabled
phases, units, standard temperature/pressure and experiment procedure. Otherwise
an output difference may reflect different physical inputs or definitions.

For each public benchmark, record source and reuse permission, measured values
and uncertainty, exact NeqSim revision, complete fluid inputs, untuned residuals,
parameters and bounds used for fitting, and separate hold-out residuals. Fix
property-specific tolerances before fitting. Use both absolute and relative
errors, including absolute dropout/precipitation tolerances near zero. Validate
component/total balances, equilibrium closure and nearby operating points.

Start with measured oil and condensate CCE/CVD/DL/separator data, then complete
gas-injection and heavy-fraction dependencies. Extend the same traceable approach
to caloric/transport data, depth profiles, aqueous partitioning and solid-phase
equilibria. Only claim accuracy for the cases and ranges actually qualified.

## References

- Pedersen, K.S., Christensen, P.L. and Shaikh, J.A. (2024), *Phase Behavior of
  Petroleum Reservoir Fluids*, 3rd ed., CRC Press, [DOI](https://doi.org/10.1201/9780429457418).
- Soave, G. (1972), *Equilibrium constants from a modified Redlich-Kwong equation
  of state*, Chemical Engineering Science 27, 1197–1203,
  [DOI](https://doi.org/10.1016/0009-2509(72)80096-4).
- Energistics, [PRODML swelling-test definition](https://docs.energistics.org/PRODML/PRODML_TOPICS/PRO-PVT-000-034-0-C-sv2000.html).
- [PVT calibration and validation workflow](pvt_workflow.md).
- [Characterization mathematics and common EOS slates](fluid_characterization_mathematics.md).
