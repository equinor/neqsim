---
title: "Property method alignment with The Properties of Gases and Liquids"
description: "Source-based coverage audit of Poling and the 2023 sixth edition, verified Chung conductivity repairs, bounded Letsou-Stiel viscosity estimation, evidence limits and remaining qualification work."
---

## Scope and edition evidence

This audit began against NeqSim commit `22d45de95350a9b4ce88d6ba03d603743427a469` on
24 October 2026. [Issue #4571](https://github.com/equinor/neqsim/issues/4571) tracks completion.
It distinguishes an implemented calculation type from a verified equation and from experimental
accuracy over a stated domain. A class name or a positive result is not qualification.

Poling, Prausnitz and O'Connell authored the fifth edition. The publisher's
[current sixth-edition record](https://www.mheducation.com/highered/mhp/product/properties-gases-liquids-sixth-edition.html)
identifies Elliott, Diky, Knotts and Wilding and a 2023 publication date. It adds explicit
uncertainty coverage and newer estimation/EOS material. The full sixth-edition text was not
accessible during this audit. Its topic list supplies the coverage framework; this document
does **not** assert equation-by-equation sixth-edition conformance or that the newest paper
for every fluid has been identified. Mathematical correlations are independently implemented;
no handbook property database or book text is redistributed.

## Coverage matrix

“Present” below means source/API coverage, not full numerical qualification. Paths are relative
to `src/main/java/neqsim/`. The nearest guides document existing choices and constraints.

| Property family | Existing architecture and examples | Evidence and remaining work |
|---|---|---|
| Pure constants and characterization | `thermo/component/Component`, `thermo/characterization/TBPfractionModel` and component databases | Present. Establish per-parameter provenance and validity; generic molecular group-contribution estimation is not established by this inventory. |
| Ideal-gas Cp, Cv, H, S and formation data | `Component.getCp0/getCv0/getHID/getIdEntropy`, `SystemIdealGas` | Present. Polynomial paths need independent coefficient and integral matrices. Existing PR #4568 handles the CpD accessor separately. |
| Vapor pressure, saturation and phase-change enthalpy | `Component.getAntoineVaporPressure` and its derivative/inverse, `ThermodynamicOperations` saturation operations, `SystemInterface.getHeatOfVaporization`, `Component.calculateWatsonHeatOfVaporization` | Present through correlations and EOS operations. The low-level coefficient getters now share the five-coefficient DIPPR 106 form and explicit missing-data contract; per-fluid parameter qualification remains separate from EOS phase enthalpy differences. Opt-in Watson reference scaling is checked against six benzene calorimetry points at 307-353 K only. |
| Pure-fluid PVT and caloric properties | SRK/PR, CPA, PC-SAFT, Span-Wagner, water and hydrogen reference utilities | Present. Selecting a modern EOS does not establish modern transport-model completeness. See [thermodynamics](../thermo/). |
| Mixture PVT and equilibrium | `SystemGERG2008Eos`, `SystemEOSCGEos`, cubic/CPA/PC-SAFT mixtures, TP/PH/PS and saturation flashes | Present. Reuse [#2937](https://github.com/equinor/neqsim/issues/2937) for numerical flash and stability qualification. Standalone general Tsonopoulos virial prediction was not found; embedded virial terms do exist in AGA8/GERG-related code. |
| Activity coefficients, infinite dilution and specialized phase behavior | Wilson, NRTL, UNIQUAC, UNIFAC/PSRK/UMR, solid-liquid operations, Henry-law APIs | Present. Benchmark each model/parameter set and infinite-dilution limit. A supplied class does not establish a suitable parameter set for an arbitrary pair. |
| Density | COSTALD, Rackett, Peneloux and EOS density | Present; see [density models](density_models). Existing correction/mixture methods should be qualified, rather than duplicated as detached utility code. |
| Viscosity | PFCT, LBC, Chung gas, friction theory, pure-fluid reference choices | Present; see [viscosity models](viscosity_models). **Added:** bounded pure-liquid Letsou-Stiel estimation. **Repaired:** CO2 background viscosity now includes the missing initial-density term and passes published Table 6/8 checks. Critical enhancement remains unimplemented. |
| Thermal conductivity | PFCT, dilute/dense Chung, polynomial, Filippov and reference choices | **Repaired:** dense Chung equation, units, original coefficients and conformal mixing. Filippov's simplified pressure factor still needs comparison with the actual Missenard correlation. See [conductivity models](thermal_conductivity_models). |
| Gas and liquid diffusivity | Chapman-Enskog, Wilke-Lee, Fuller, Wilke-Chang, Siddiqi-Lucas, Tyn-Calus, Hayduk-Minhas and specialized aqueous methods | Present; see [diffusivity models](diffusivity_models). Extend independent value/range checks per method; do not infer dense-fluid validity from dilute-gas accuracy. |
| Surface tension | Parachor, gradient theory, linear gradient theory, density-functional and empirical interfaces | Present; see [interfacial properties](interfacial_properties). Qualify by phase pair, critical approach and parameter source. |
| Uncertainty | Existing numerical and study-specific uncertainty utilities | General property-model validity/uncertainty contracts are incomplete. Avoid reporting a universal percentage uncertainty for a correlation or a handbook. |

## Verified transport changes

### Dense Chung conductivity

The previous implementation divided an already molar Cp by phase moles. For 75/25
methane/ethane at 350 K and 100 bara it returned 0.04673335678896647 W/(m K) for
1 mol and 45.777039730801135 W/(m K) for 0.001 mol. A focused regression fails
against the original class and checks 0.001, 1 and 1000 mol with the correction.

The repair also restores the density contribution, correct reduced temperature, consistent
SI equation, transport association factor, original conductivity coefficients and conformal
mixture rules. Composition-dependent work uses the symmetric half of the binary matrix;
cube roots are computed once per component. A pure-component fast path avoids mixture
arrays. All evaluation state is local, so there is no composition cache to invalidate after
cloning, a component edit or a temperature change. Default model selection is unchanged.

`ChungDenseCorrelationTest` freezes these independent Python equation evaluations with the
original coefficient table. They test equation implementation, **not experimental accuracy**.

| T (K) | Molar density (mol/m³) | M (kg/mol) | Tc (K) | Vc (cm³/mol) | omega | Cv0 (J/mol/K) | Dipole (D) | Association | Conductivity (W/m/K) |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 300 | 0 | 0.0280134 | 126.2 | 89.4 | 0.0372 | 20.8 | 0 | 0 | 0.026436687902351361 |
| 300 | 4000 | 0.0280134 | 126.2 | 89.4 | 0.0372 | 20.8 | 0 | 0 | 0.031034910394252442 |
| 473 | 5810.575246949447 | 0.042081 | 364.9 | 184.6 | 0.142 | 82.67 | 0.4 | 0 | 0.059100960749808175 |
| 400 | 12000 | 0.04607 | 516.25 | 168 | 0.6371 | 90 | 1.69 | 0.175 | 0.11497183673220499 |
| 200 | 24000 | 0.016043 | 190.564 | 98.6 | 0.01142 | 25 | 0 | 0 | 0.14523150764316997 |

`ChungDenseConductivityRegressionTest` additionally checks component order, amount, repeated
evaluation, cloning and physical nitrogen pressure behavior. The dense correlation is an
estimator and has no critical enhancement. Mixture combining factors are unity and have not
been fitted or qualified for arbitrary polar/associating mixtures.

### Letsou-Stiel saturated-liquid viscosity

`LetsouStielViscosity` extends the existing liquid viscosity method, implements the existing
viscosity interface, and is selected with `PhysicalProperties.setViscosityModel`.
It also exposes a stateless Java API usable through the Python bridge. Units and range
contracts are in the [viscosity guide](viscosity_models#letsou-stiel-saturated-liquid-estimator).
It requires no new database fields or runtime dependency and is never selected automatically.
Pure liquids outside 0.76–0.98 reduced temperature and mixtures are refused.

The independently published ethanol worked example is reproduced to rounding precision;
tests also exercise the documented benzene phase integration, units, limits and cloning.
These are implementation checks, not a qualification of compressed-liquid viscosity or of
associating fluids. No pressure correction or mixture rule has been silently added.

### CO₂ reference background viscosity

The baseline `CO2ViscosityMethod` included the dilute and residual Laesecke–Muzny terms,
but omitted the linear-in-density Rainwater–Friend contribution. It failed all six
finite-density rows of the paper's Table 6. The correction restores equations 5–6 and
retains the existing phase-bound model and Span–Wagner density fallback. NaN density now
also triggers that fallback. Invalid scalars and unsupported species/mixtures fail explicitly.

The correlation uses the published energy scale 200.76 K, length scale 0.378421 nm,
Table 3 virial coefficients, and a fixed pure-CO₂ molar mass of 0.0440095 kg/mol.
The dimensionless virial coefficient is converted with Avogadro's constant and the
length scale cubed; division by molar mass is essential because the API takes kg/m³.
All coefficients are fixed, with no parameter fitting in this change.

| T (K) | Density (kg/m³) | Published background viscosity (mPa s) | Baseline error (%) |
|---:|---:|---:|---:|
| 220 | 3 | 0.011104 | +0.13 |
| 225 | 1150 | 0.22218 | +1.98 |
| 300 | 65 | 0.015563 | -2.15 |
| 300 | 1400 | 0.50594 | -1.42 |
| 700 | 100 | 0.033112 | -3.94 |
| 700 | 1200 | 0.22980 | -6.81 |

All six corrected values fall within half the last printed digit. The three dilute
Table 6 rows, including the stated zero-density extrapolation to 10000 K, are also checked.
Seven independent Table 8 recommendations additionally exercise the existing EOS, phase
selection, property initialization and SI viscosity access at 240–1000 K and 1–1000 bara;
the integration tolerance is 0.1%. These are checks against the published reference
correlation and its recommendations, not new experimental qualification.

**Critical enhancement is not included.** The main static API supports 100–2000 K;
the separate dilute-only API explicitly permits extrapolation to 10000 K. These scalar
bounds do not establish physical phase stability or the melting limit. Callers needing
near-critical viscosity must account for the missing enhancement separately.

[NIST IR 8608 (February 2026), section 11](https://doi.org/10.6028/NIST.IR.8608)
still identifies Laesecke–Muzny (2017) as the reference viscosity formulation and retains
its parameters when evaluating the new alternative CO₂ EOS. That EOS is separate from
this viscosity repair; it has not been implemented or qualified by this audit.

## Coefficient-based saturation latent heat

Both `Component.getHeatOfVapourization(T)` and
`Component.getPureComponentHeatOfVaporization(T)` evaluate the same DIPPR 106 form:

$$H_{vap}=10^{-3}A(1-T_r)^{B+CT_r+DT_r^2+ET_r^3},\quad T_r=T/T_c$$

Temperature and critical temperature are in K, database A is in J/kmol and the result
is in J/mol. The legacy getter previously treated these coefficients as a malformed
polynomial in absolute temperature; the other getter omitted E. The full five-term
form is documented in Aspen Physical Property Methods and Models, section
“DIPPR Heat of Vaporization Equation”
([vendor manual](https://sites.poli.usp.br/d/pqi2408/physpropmodels.pdf)).
This establishes the equation form, not sixth-edition conformance or parameter validity.

The methods return NaN for nonfinite/nonpositive temperature, T at or above Tc,
nonfinite coefficients, nonpositive A, nonpositive evaluated exponent, or a nonfinite/
nonpositive result. No fit-temperature limits are stored with these coefficients;
0 < T < Tc alone does not qualify a particular fluid's fit. The
system-level EOS enthalpy-difference calculation is unchanged.

`ComponentHeatOfVaporizationTest` checks an independent five-coefficient value,
invalid/missing parameters, temperature trends, amount invariance and clone behavior.
The shipped benzene coefficients are compared at 353.3 K with the
[NIST WebBook](https://webbook.nist.gov/cgi/cbook.cgi?ID=C71432&Mask=4)
value 30.72 kJ/mol (Majer and Svoboda, 1985). The shipped coefficient evaluation gives
39.147973 kJ/mol, **27.4% high**: this remains an unqualified parameter set, not a passing
physical-accuracy benchmark. Regression tests freeze the independent equation evaluation
and do not loosen a physical tolerance to hide this discrepancy. For example,
water and benzene currently share latent-heat coefficient values, so parameter provenance
and fluid-specific validation remain open under #4571.

The pipeline evaporation fallback consumes J/mol directly: signed flux in mol/(m2 s)
times latent heat gives W/m2. The previous extra factor of 1000 is removed, with an
independent 78.295947 W/m2 equation-based benzene unit check and evaporation/condensation sign checks.
The fallback still omits unavailable component correlations; that existing approximation
is not a qualified substitute for coupled interphase enthalpy transport. This repair does
not certify evaporation distance or two-phase thermal closure against experiments.

### Watson reference-state scaling (opt-in)

A caller with trustworthy pure-fluid saturation reference data can now use
`Component.calculateWatsonHeatOfVaporization(T, Tref, Href, Tc)` or
`component.getHeatOfVaporizationWatson(T, Tref, Href)` through the existing component
interface (also available through the Python Java bridge). The instance adapter uses
that component's Tc. The static operation is registered through `runCapability`.
It does not use or replace the database latent-heat coefficients.

$$H(T)=H_{ref}\left(\frac{T_c-T}{T_c-T_{ref}}\right)^{0.38}$$

All temperatures are in K and both enthalpies are in J/mol. The equation and fixed
exponent are from Watson (1943), equation 13, printed page 402
([original paper](https://personal.ems.psu.edu/~radovic/Watson_IEC_1943.pdf),
[DOI](https://doi.org/10.1021/ie50400a004)); the scanned equation was inspected.
This reference scaling differs from NIST TDE's fitted multi-term HVP expansion.

Require finite positive Href, 0 < Tref < Tc, and 0 < T <= Tc. The reference value is
returned exactly at T=Tref and the mathematical limit is zero at T=Tc. A supercritical
target, invalid reference or unrepresentable positive subcritical result throws
`IllegalArgumentException`. Temperature differences avoid separately subtracting
reduced temperatures from one near Tc. No cross-state cache is used.

The documented adapter example is executed in `watsonComponentAdapterAndClone`:

```java
SystemSrkEos fluid = new SystemSrkEos(332, 1);
fluid.addComponent("benzene", 1);
ComponentInterface component = fluid.getPhase(0).getComponent(0);
double estimate = component.getHeatOfVaporizationWatson(332, 353.3, 30720);
```

The standalone static route with Tc=562.1 K gives 31875.128087 J/mol at 332 K.
The adapter uses the shipped component Tc and therefore need not give identical digits.
An independent NIST benzene comparison uses the Majer/Svoboda (1985) anchor
30720 J/mol at 353.3 K and Tc=562.1 K; none of the following Dong et al. (1988)
calorimetry points were used to fit the fixed 0.38 exponent:

| Temperature (K) | NIST calorimetry (J/mol) | Watson estimate (J/mol) | Relative error (%) |
| ---: | ---: | ---: | ---: |
| 307 | 33400 | 33149.24 | -0.75 |
| 314 | 33100 | 32800.60 | -0.90 |
| 324 | 32400 | 32291.80 | -0.33 |
| 332 | 31900 | 31875.13 | -0.08 |
| 344 | 31400 | 31232.93 | -0.53 |
| 353 | 30600 | 30736.76 | +0.45 |

All six points pass a declared 1% comparison gate. This is a limited benzene check,
not a universal uncertainty statement or validation outside 307-353 K. Use reference
data near the target temperature. The original paper identifies water below its normal
boiling point as an exception. Pure-fluid phase stability/melting limits, mixtures,
compressed-liquid pressure corrections and near-critical accuracy/derivatives remain
outside this estimator's qualification. The zero endpoint is a model limit, not proof
of critical-region accuracy. Default EOS latent heat, all property selections and
normal process evaluation costs are unchanged; work is performed only on explicit calls.
The earlier database parameter discrepancy and sixth-edition evidence gap remain open.

## Execution routes and computational cost

The stateless transport correlation APIs are registered in `devtools/engineering_capabilities.json`
with parameter units, applicability, frozen examples and test sources. They use the existing
`runCapability` static-JSON invocation route. `TransportPropertyCapabilityTest` executes all four
routes and checks the viscosity range refusals; no new execution mechanism is introduced.
The generated engineering-coverage inventory records source and evidence references, not
an experimental-accuracy certification.

`ChungDenseConductivityBenchmarkTest` is an opt-in benchmark. On Java 17, one local run
measured five batches of 50,000 calls after 20,000 warmup calls, using the same harness and
an independently compiled original class from the inspected baseline. Median times were:

| Components | Original (ns/call) | Corrected (ns/call) |
|---:|---:|---:|
| 1 | 292 | 157 |
| 2 | 290 | 302 |
| 8 | 1602 | 1172 |

These are diagnostic microbenchmarks, not stable CI thresholds or whole-process speed claims.
The binary case was about 4% slower in this run, while the pure and larger-mixture cases
were faster. The implementation performs the complete corrected mixture equations without
an unsafe cross-state cache. Default PFCT evaluation paths are unchanged. Letsou-Stiel is
an opt-in constant-cost polynomial evaluation and introduces no work in default calculations.

The CO₂ continuation was measured with `CO2ViscosityBenchmarkTest`: Java 17, five
200,000-call batches after 20,000 warmup calls, using the same phase-bound harness and
a separately compiled original class. Median times in one local run were:

| T (K) | Density (kg/m³) | Original (ns/call) | Corrected (ns/call) |
|---:|---:|---:|---:|
| 220 | 3 | 94.45 | 79.77 |
| 225 | 1150 | 91.48 | 78.18 |
| 300 | 65 | 92.13 | 79.30 |
| 300 | 1400 | 90.63 | 79.60 |
| 700 | 100 | 90.79 | 79.64 |
| 700 | 1200 | 90.77 | 81.06 |

The corrected model is faster in this diagnostic run despite restoring the missing
physics. Residual constants are precomputed; fractional powers in the virial term use
two square roots and a polynomial; integer powers use products. No mutable state cache
or additional EOS solve is introduced on the ordinary finite-density path. Timing does
not measure an entire flash/process calculation or establish a stable speedup guarantee.

## Remaining priorities and evidence gates

1. Obtain sixth-edition equation/errata evidence and attach per-method provenance before making
   a full edition-conformance statement. Do not replace a validated method merely because
   another method is newer; compare accuracy, range, required parameters and computation cost.
2. Qualify CO2 critical enhancement as a separate numerical/thermodynamic increment.
   The background correlation is repaired and checked against the paper; the singular
   critical contribution remains excluded. Assess the new NIST IR 8608 alternative EOS
   and its potential speed/derivative benefits separately from changing transport coefficients.
3. Qualify latent-heat parameter provenance/fit ranges and liquid pressure corrections with
   published examples before changing defaults. Distinguish an unsupported alternative
   correlation from a calculation type already supported by EOS enthalpy differences.
4. Assess standalone Tsonopoulos and molecular
   group-contribution APIs; Watson reference-state scaling is now exposed with the limited qualification above. Require a genuine use case, verified parameters, finite/invalid
   input behavior, dimensional tests and independent values before implementation.
5. Extend [#3792](https://github.com/equinor/neqsim/issues/3792)'s worked-example harness across
   the matrix. Keep [#469](https://github.com/equinor/neqsim/issues/469)'s SUPERTRAPP work separate.
   This audit does not supersede the existing EOS, electrolyte or flash campaigns.

## References

- Poling, B. E., Prausnitz, J. M. and O'Connell, J. P. *The Properties of Gases and Liquids*, 5th ed.
- Elliott, J. R., Diky, V., Knotts, T. A. and Wilding, W. V. *The Properties of Gases and Liquids*, 6th ed., McGraw Hill, 2023. Publisher metadata only was available for this audit.
- Chung et al. (1988), [doi:10.1021/ie00076a024](https://doi.org/10.1021/ie00076a024).
- Bouraba et al. (2017), [doi:10.1016/j.ijrefrig.2017.05.008](https://doi.org/10.1016/j.ijrefrig.2017.05.008), Table A.2 for the original conductivity coefficients.
- [Cantera 3.2 Chung source](https://cantera.org/3.2/cxx/d2/d89/HighPressureGasTransport_8cpp_source.html), equation and mixture-rule cross-check. Its reproduced coefficient table differs; it is not used as the coefficient authority here.
- Letsou and Stiel (1973), [doi:10.1002/aic.690190241](https://doi.org/10.1002/aic.690190241); polynomial form in the [ChemSep book](https://www.chemsep.org/downloads/docs/book2.pdf), equations 10.158–10.161, and [COCO/TEA documentation](https://www.cocosimulator.org/index_help.php?page=TEA%2Fviscosity.htm).
- [Chemicals viscosity documentation](https://chemicals.readthedocs.io/chemicals.viscosity.html), independent ethanol worked example; no Python package is required by NeqSim.
- Huber (2018), [NIST IR 8209](https://doi.org/10.6028/NIST.IR.8209), fluid-specific reference transport models and model selection.
- Laesecke and Muzny (2017), [doi:10.1063/1.4977429](https://doi.org/10.1063/1.4977429), equations 4–9 and Tables 3, 6 and 8. Table 6 explicitly excludes the critical enhancement.
- Lemmon, Harvey and Hellmann (2026), [NIST IR 8608](https://doi.org/10.6028/NIST.IR.8608), section 11 on continued applicability of the existing CO₂ transport reference formulations.

## Related documentation

- [Physical properties overview](README)
- [Viscosity models](viscosity_models)
- [Thermal conductivity models](thermal_conductivity_models)
- [Extending physical properties](../development/extending_physical_properties)
