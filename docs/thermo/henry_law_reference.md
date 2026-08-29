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

## Model semantics

- Generic aqueous GE models use the IAPWS mole-fraction reference only when
  water is present and the neutral species is supported.
- Pitzer uses the explicit molality conversion above. Pitzer \(\lambda\),
  \(\zeta\), \(\mu\), and \(\eta\) interactions remain distinct parameter
  families.
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
| NIST Chemistry WebBook SRD 69, [CO2 Henry data](https://webbook.nist.gov/cgi/cbook.cgi?ID=C124389&Mask=10) | CO2 in water near ambient temperature | solubility-form values and temperature parameter | compilation is copyright protected | read-only validation index; no values copied |

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
regressions, including the dimensional identity

$$\frac{\mathrm{d}\ln H}{\mathrm{d}T}=\frac{1}{H}\frac{\mathrm{d}H}{\mathrm{d}T}.$$

These tests are implementation and source-table regressions. They are separate
from independent VLE/VLLE or brine validation.

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
