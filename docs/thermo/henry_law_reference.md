---
title: "Henry-Law Reference States and Aqueous Gas-Solubility Evidence"
description: "IAPWS pure-water Henry correlations, GE/Pitzer standard-state mapping, provenance, and validation boundaries."
keywords: "Henry law, gas solubility, electrolyte, Pitzer, activity coefficient, aqueous phase, provenance"
---

# Henry-law reference states and aqueous gas-solubility evidence

NeqSim uses Henry-law reference states for neutral solutes in aqueous
excess-Gibbs models. This page records the implemented convention, source
provenance, qualification status, and the evidence boundary for brines and
multiphase calculations.

## Engineering question and stop boundary

This increment asks whether common gases can use a reproducible, attributed
pure-water Henry reference instead of invalid or undocumented database
placeholders, without changing electrolyte interactions, reaction constants,
EOS mixing rules, flash topology, or non-aqueous model paths.

It implements the IAPWS G7-04 H2O correlation for He, Ne, Ar, Kr, Xe, H2, N2,
O2, CO, CO2, H2S, CH4, C2H6, and SF6. It does not add a pressure/Poynting
correction, a vapor-liquid distribution-constant correlation, brine salting-out
parameters, reactive speciation, or new electrolyte-EOS parameters.

## Convention and equations

IAPWS defines the mole-fraction Henry constant on the liquid-water saturation
boundary as

$$k_H(T)=\lim_{x_i\rightarrow0}\frac{f_i}{x_i}.$$

With \(T_r=T/T_c\), \(\tau=1-T_r\), and water saturation pressure \(p^*\),

$$\ln\left(\frac{k_H}{p^*}\right)
 =\frac{A}{T_r}+\frac{B\tau^{0.355}}{T_r}
  +C T_r^{-0.41}\exp(\tau).$$

The implementation returns \(k_H\) in bar and provides the analytical
\(\mathrm{d}\ln k_H/\mathrm{d}T\) required by fugacity derivatives. It reports
whether a requested state is inside the species-specific fitted range, a
guideline extrapolation, unsupported, or outside the liquid-water correlation
domain. A supported species outside the domain fails closed.

Pitzer neutral activities use a molality standard state. At infinite dilution,

$$H_m=k_H M_{\mathrm{water}},$$

where \(M_{\mathrm{water}}=0.01801528\) kg/mol. The existing \(m_i/x_i\)
conversion then maps the Pitzer expression back to the common
mole-fraction/fugacity kernel. The constant conversion leaves
\(\mathrm{d}\ln H/\mathrm{d}T\) unchanged.

The coherent family is identified as `iapws-g7-04-water-gases-v1`.
Ordinary runtime evaluation accepts only the published species-specific fitted
range. `getHenryCoefficientBarAllowExtrapolation` is deliberately named for
reproducing guideline check values outside that range; GE and Pitzer fugacity
paths never call it.

### Analytical derivative contract

For the implemented GE reference states,

$\phi_i=\frac{\gamma_i H_i}{P}$

for a Henry solute, or the corresponding solvent vapor-pressure expression.
NeqSim therefore exposes the constant-composition analytical derivatives

$\left(\frac{\partial\ln\phi_i}{\partial T}\right)_{P,\mathbf{x}}=\frac{\partial\ln\gamma_i}{\partial T}+\frac{\partial\ln H_i}{\partial T}$

and

$\left(\frac{\partial\ln\phi_i}{\partial P}\right)_{T,\mathbf{x}}=\frac{\partial\ln\gamma_i}{\partial P}-\frac{1}{P}$

`SystemInterface.init(2)` and `init(3)` publish these values through
`getdfugdt()`, `getdfugdp()`, and the Java/JPype
`getProperty("logfugdT"|"logfugdP", component, phase)` adapter. Pressure is in
bar, so the pressure derivative is in 1/bar. The `-1/P` term is the derivative
of the explicit fugacity-coefficient denominator; it is not a Poynting
correction. The IAPWS reference remains defined at water saturation, and no
pressure-dependent partial-molar-volume model is implied.

### Versioned coefficient family

The following values are transcribed from IAPWS G7-04 Tables 2 and 5. `A`, `B`,
and `C` are dimensionless; temperature limits are in kelvin; RMS is the
published root-mean-square deviation in `ln(kH)`.

| Species | A | B | C | Tmin | Tmax | RMS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| He | -3.52839 | 7.12983 | 4.47770 | 273.21 | 553.18 | 0.0341 |
| Ne | -3.18301 | 5.31448 | 5.43774 | 273.20 | 543.36 | 0.0577 |
| Ar | -8.40954 | 4.29587 | 10.52779 | 273.19 | 568.36 | 0.0443 |
| Kr | -8.97358 | 3.61508 | 11.29963 | 273.19 | 525.56 | 0.0434 |
| Xe | -14.21635 | 4.00041 | 15.60999 | 273.22 | 574.85 | 0.0363 |
| H2 | -4.73284 | 6.08954 | 6.06066 | 273.15 | 636.09 | 0.0517 |
| N2 | -9.67578 | 4.72162 | 11.70585 | 278.12 | 636.46 | 0.0372 |
| O2 | -9.44833 | 4.43822 | 11.42005 | 274.15 | 616.52 | 0.0377 |
| CO | -10.52862 | 5.13259 | 12.01421 | 278.15 | 588.67 | 0.0039 |
| CO2 | -8.55445 | 4.01195 | 9.52345 | 274.19 | 642.66 | 0.0528 |
| H2S | -4.51499 | 5.23538 | 4.42126 | 273.15 | 533.09 | 0.0408 |
| CH4 | -10.44708 | 4.66491 | 12.12986 | 275.46 | 633.11 | 0.0386 |
| C2H6 | -19.67563 | 4.51222 | 20.62567 | 275.44 | 473.46 | 0.0259 |
| SF6 | -16.56118 | 2.15289 | 20.35440 | 283.14 | 505.55 | 0.0505 |

## Model semantics

| Runtime path | IAPWS reference decision | Electrolyte-interaction decision |
| --- | --- | --- |
| Generic aqueous GE | use the mole-fraction reference for a supported neutral gas in water, including H2/He/Ar rows historically marked as solvents | retain the GE model's own activity coefficient |
| Pitzer, no ionic component topology | use the explicit molality conversion above | no salting-out claim; this is the pure-water limit |
| Pitzer, ions and a coverage-checked neutral family | use the molality reference | apply the explicitly selected `lambda`, `zeta`, `mu`, and `eta` family |
| Pitzer, ions without a qualified neutral family | preserve the established database/capping reference for the complete calculation | do not silently combine IAPWS with a unit neutral activity coefficient |
| SRK, PR, CPA, and electrolyte EOS | do not dispatch through this GE reference | retain EOS fugacity, association, and mixing-rule semantics |

The Pitzer decision depends on component topology, not the instantaneous ionic
strength. Consequently a reaction solve cannot change reference models merely
because trace ions appear or disappear. Pitzer \(\lambda\), \(\zeta\), \(\mu\),
and \(\eta\) interactions remain distinct parameter families. CO2 and H2S also
require a qualified neutral family before Pitzer changes their reference, even
before reaction species have been added. This prevents initialization order
from seeding a reactive calculation with a temporarily different standard
state.
- Pure-water Henry data do not qualify electrolyte salting-out behavior.
  Brine prediction still requires independently sourced neutral-ion
  interactions and dataset qualification.
- EOS models such as SRK, PR, CPA, and electrolyte CPA retain their EOS
  fugacity, association, and mixing-rule paths.
- Molecular CO2 and H2S dissolution does not replace reaction equilibrium
  constants or the electroneutral species balance.

## Current-master gap reproduced before adoption

The audited component table contained a methane overflow sentinel, zero
coefficient rows for nitrogen and oxygen, and incomplete or undocumented rows
for several other gases. Those rows could not serve as qualified common-gas
water correlations. The IAPWS dataset is selected by explicit species and water
topology; unrelated database correlations remain available for unsupported
species.

## Public evidence and source comparison

| Source | Species/system | Convention and range | Licensing/reuse | Decision |
| --- | --- | --- | --- | --- |
| Fernandez-Prini et al. (2003), [DOI 10.1063/1.1564818](https://doi.org/10.1063/1.1564818) | 14 gases in H2O and 7 gases in D2O | (k_H=f/x) at water saturation; triple-point region toward critical conditions; species-specific fitted ranges and RMS residuals | primary JPCRD article available through NIST | adopted lineage and independent equation cross-check for the 14 H2O gases |
| IAPWS G7-04, [Henry guideline](https://iapws.org/technical-guidance/release/HenGuide) | He, Ne, Ar, Kr, Xe, H2, N2, O2, CO, CO2, H2S, CH4, C2H6, SF6 in H2O | same equation and standard state; Table 2 fitted ranges; Table 6 check values at 300/400/500/600 K | guideline states that publication in whole or in part is permitted with attribution | adopted coefficients, ranges, equations, and 56 published check values |
| Sander (2023), [DOI 10.5194/acp-23-10901-2023](https://doi.org/10.5194/acp-23-10901-2023) | water-solvent compilation | standardized alternative Henry definitions and conversions | article and supplement are CC BY 4.0 | terminology and conversion cross-check; no values adopted |
| NIST Chemistry WebBook SRD 69, [CO2](https://webbook.nist.gov/cgi/cbook.cgi?ID=C124389&Mask=10), [CH4](https://webbook.nist.gov/cgi/cbook.cgi?ID=C74828&Mask=10), and [N2](https://webbook.nist.gov/cgi/cbook.cgi?ID=C7727379&Mask=10) Henry data | three gases in water near ambient temperature | solubility-form values and temperature parameters; conventions require conversion before comparison | compilation is copyright protected | read-only independent screening index; no values copied |
| USGS PHREEQC 3.9.0, [`pitzer.dat` provenance and license](pitzer_parameter_provenance.md) | ionic and neutral Pitzer interaction families | molality scale, PHREEQC six-term temperature functions, explicit tuple topology | USGS release is public domain; exact release/commit/blob are recorded in the linked matrix | confirms that a pure-water Henry constant is not a substitute for missing neutral-ion terms; no Pitzer value adopted here |
| Kaasa (1998), [stable National Library item](https://www.nb.no/items/d1d68b489b8ee6704786a011fd2e7283) | oil-recovery-brine Pitzer binary, same-sign, ternary, and neutral families | Appendix F is a provenance index with its own coefficient order and row-level source lineage | scan-table redistribution terms are unresolved; original pages were not retrievable from the public item in this run | metadata/provenance index only; no value inferred, OCRed, copied, or adopted |

The IAPWS high-temperature root-mean-square deviations in \(\ln k_H\) range
from 0.0039 for CO to 0.0577 for Ne. These source residuals describe the
correlation fit; they are not NeqSim regression tolerances or independent
held-out validation.

Baard Kaasa's 1998 thesis remains a provenance index for Pitzer
specific-ion-interaction parameters. No Kaasa table was copied and no Pitzer
coefficient was adopted here: Henry constants and Pitzer interaction
coefficients are different model families.

## Regression evidence

`IapwsHenryLawTest` reproduces all 56 rounded IAPWS Table 6 values, compares
the analytical derivative with centered finite differences for all 14 gases,
checks fitted/extrapolated/unsupported/out-of-domain diagnostics, verifies
mole-fraction-to-molality mapping, and exercises fail-closed behavior.

`ComponentGEHenryDerivativeTest` retains the legacy database-correlation
regressions, including the dimensional identity, corrects the historic
H2/He/Ar solvent classification in aqueous GE phases, and verifies that an
ionic Pitzer topology without a qualified neutral family remains on one
deterministic compatibility path even at trace ionic strength. It also checks
the system-level `init(2)` derivative dispatch against an IAPWS analytical
temperature derivative and a centered pressure finite difference, including
changed-state, return-state, clone, and Java/JPype property access. The
system-level Pitzer regression verifies the explicit molality conversion,
including the fixed IAPWS water molar mass. The dimensional identity is

$$\frac{\mathrm{d}\ln H}{\mathrm{d}T}=\frac{1}{H}\frac{\mathrm{d}H}{\mathrm{d}T}.$$

These tests are implementation and source-table regressions. They are separate
from independent VLE/VLLE or brine validation. In particular, six held-out
CO2-Na2SO4 bubble pressures from Bermejo et al. (2005),
[DOI 10.1016/j.fluid.2005.10.006](https://doi.org/10.1016/j.fluid.2005.10.006),
are underpredicted by 32.6–43.8% even though the phase-boundary calculations
close. `SystemPitzer.requirePitzerDatasetValidationFor(GAS_AQUEOUS_VLE)`
therefore rejects that parameter subset; aqueous-activity and water-property
qualification are unaffected. The Pitzer provenance page records the NIST
ThermoML checksum, preprocessing, uncertainty, and composition convention.

## Scientific and performance controls

The repository's complete reactive-H2S benchmark remains on the established
legacy Pitzer reference because its diagnostic reports missing `H2S|H2S`,
`H2S|H3O+`, `H2S|HS-`, and `H2S|H3O+|HS-` neutral interactions. At 298.15 K,
the repaired branch gives maximum absolute reaction `ln(Q/K)` residual
`4.2633e-14`, maximum elemental residual `1.1927e-13`, charge
`-1.0825e-14` mol, and normalized charge residual `1.5922e-10`. HS- molality
increases from `3.3995324e-5` mol/kg at 298.15 K to `4.3530791e-5` mol/kg at
318.15 K. These are compatibility, closure, and nearby-state controls; they
are not treated as new independent H2S-solubility validation.

`PitzerCatalogPerformanceBenchmark` uses nine fixed-work batches after warmup.
On OpenJDK 17 in the same runner, the affected combined IAPWS value/derivative
kernel decreased from 1140 ns on exact pre-repair PR head `a3acb09cf` to 573 ns
on the repaired tree. The complete reactive-H2S median was 40.76 ms versus
50.25 ms on that head, with the compatible chemical state above. Absolute
wall-clock timings remain environment-sensitive; the comparison is a
regression screen, not a portable throughput guarantee. Neutral SRK/PR/CPA
paths do not dispatch through this helper and remain covered by their existing
tests.

## Remaining validation boundary

Publication-quality aqueous/electrolyte predictions still require:

1. independently sourced neutral-ion interactions for the selected Pitzer
   dataset, including complete binary and ternary coverage;
2. held-out pure-water and brine solubility over temperature, pressure, ionic
   strength, and salt composition;
3. reactive CO2/H2S material balance, electroneutrality, and
   \(\max|\ln(Q/K)|\);
4. VLE/VLLE fugacity closure, stable topology, normalized non-negative phases,
   and nearby-state trends;
5. deterministic repeated and changed-state execution, stale-state protection,
   clone/serialization/thread checks, and Java/JPype composability;
6. complete-calculation and kernel benchmarks, including neutral EOS controls.

The next parameter increment is therefore a complete, redistributable
neutral-gas/ion interaction family with held-out brine validation. Missing
interactions must remain explicit rather than silently defaulting to zero.
