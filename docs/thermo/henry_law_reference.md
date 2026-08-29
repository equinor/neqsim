---
title: "Henry-Law Reference States and Aqueous Gas-Solubility Evidence"
description: "Henry-law conventions, temperature derivatives, parameter provenance, and validation gates for aqueous GE and Pitzer models."
keywords: "Henry law, gas solubility, electrolyte, Pitzer, activity coefficient, aqueous phase, provenance"
---

# Henry-law reference states and aqueous gas-solubility evidence

NeqSim uses Henry-law reference states for neutral solutes in aqueous excess-Gibbs
models. This page records the implemented convention, the evidence boundary, and
the validation required before changing gas-solubility parameters.

## Engineering question and stop boundary

The current increment asks whether the temperature derivative supplied to
equilibrium and property algorithms is consistent with the implemented Henry
correlation. It does not change a coefficient, EOS binary interaction parameter,
Pitzer neutral-ion interaction, reaction equilibrium constant, flash topology, or
model default.

The implementation stores four coefficients and evaluates

$$H(T)=1.802\exp(C_1+C_2/T+C_3\ln T+C_4T)$$

where the public component contract reports \(H\) in bar and \(T\) in kelvin.
For an active finite correlation, the logarithmic derivative required by a
fugacity coefficient is

$$\frac{\mathrm{d}\ln H}{\mathrm{d}T}=-\frac{C_2}{T^2}+\frac{C_3}{T}+C_4.$$

The previous GE path added \(\mathrm{d}H/\mathrm{d}T\) directly to
\(\mathrm{d}\ln\gamma/\mathrm{d}T\). That mixes dimensional and logarithmic
derivatives. The corrected path divides by \(H\), while a fail-closed constant
Henry reference contributes zero.

## Model semantics

- EOS models such as SRK, PR, CPA, and electrolyte CPA calculate aqueous
  solubility from EOS fugacity and mixing/association parameters. This GE
  derivative change adds no EOS kernel work.
- Pitzer uses a molality activity scale for neutral dissolved gases and maps it
  to the common mole-fraction fugacity kernel. Henry reference parameters and
  Pitzer \(\lambda\), \(\zeta\), \(\mu\), and \(\eta\) interactions are separate
  parameter families and must not be substituted for one another.
- Pure-water Henry data do not qualify electrolyte salting-out behavior.
  Brine validation additionally requires independently sourced neutral-ion
  interactions or a separately reviewed activity correction.
- A reaction equilibrium constant does not replace the molecular-gas Henry
  reference. Reactive CO2 and H2S cases require both molecular dissolution and
  aqueous speciation closure.

## Current-master coefficient audit

The exact \`COMP.csv\` rows on master
\`3d539d745168727ccca22fe2f571c4e200669b85\` expose a concrete qualification
gap:

| Component | Stored Henry row | Current interpretation | Qualification decision |
| --- | --- | --- | --- |
| CO2 | four non-zero coefficients | finite temperature correlation | provenance and range not recorded per row; do not refit silently |
| methane | \(C_1=900\) | overflows and is fail-closed as effectively insoluble in Pitzer | unqualified for aqueous-solubility prediction |
| nitrogen | all zero | evaluates to 1.802 bar through the conversion factor | unqualified; zero is not accepted as evidence |
| oxygen | all zero | evaluates to 1.802 bar through the conversion factor | unqualified; zero is not accepted as evidence |
| hydrogen | all zero and database reference state \`solvent\` | not a qualified aqueous solute reference | unqualified |
| H2S | only \(C_4\) is non-zero | finite but row provenance/range is absent | unqualified for publication-quality solubility |

No value from this audit is newly adopted. The table is a source-comparison and
triage record; it prevents placeholder or sentinel values from being mistaken
for qualified correlations.

## Public evidence matrix

| Source | Species/system | Convention and range | Licensing/reuse | Use in this increment |
| --- | --- | --- | --- | --- |
| Sander (2023), [DOI 10.5194/acp-23-10901-2023](https://doi.org/10.5194/acp-23-10901-2023) | water-solvent compilation, 10,173 species | standardized Henry definitions; source-specific ranges and uncertainty | article and supplement are CC BY 4.0 | terminology, conversion cross-check, and future redistributable source index |
| Fernandez-Prini et al. (2003), [DOI 10.1063/1.1564818](https://doi.org/10.1063/1.1564818) | H2, N2, O2, CO, CO2, H2S, CH4, C2H6 and other gases in water | IAPWS Henry and vapor-liquid distribution correlations from water triple point toward critical conditions; species-specific fitted ranges | authoritative public guideline/article; coefficient redistribution terms not assumed | independent high-temperature validation target; no coefficients copied |
| IAPWS G7-04, [Henry guideline](https://iapws.org/technical-guidance/release/HenGuide) | common gases in H2O and D2O | authoritative equation and validity definitions | public access; reuse terms must be checked before storing coefficients | independent implementation target only |
| NIST Chemistry WebBook SRD 69, [CO2 Henry data](https://webbook.nist.gov/cgi/cbook.cgi?ID=C124389&Mask=10) | CO2 in water near ambient temperature | solubility form \(k_H\), mol/(kg bar), with temperature parameter | compilation is copyright protected | read-only cross-check; values are not copied into the repository |

The Sander compilation is not treated as a substitute for its primary
experimental references. A future coefficient batch must trace each adopted row
to the original measurements or authoritative correlation, record uncertainty
and preprocessing, and hold out independent data from fitting.

## Validation gates for a coefficient batch

A publishable gas-solubility dataset must include, for each species and model:

1. explicit Henry convention, standard state, pressure unit, concentration
   basis, reference temperature, pressure correction, and valid range;
2. original data/correlation citation, redistributable license, uncertainty,
   preprocessing, and a versioned dataset identity;
3. pure-water held-out solubility over temperature and pressure;
4. brine held-out data over ionic strength and salt composition, with complete
   Pitzer neutral-ion interactions or an explicit missing-parameter failure;
5. reactive CO2/H2S molecular-plus-speciation balance, electroneutrality, and
   reaction \(\max|\ln(Q/K)|\);
6. VLE/VLLE fugacity closure, non-negative normalized phases, nearby-state
   trends, deterministic repeated and changed-state execution, clone,
   serialization, and Java/JPype composability;
7. complete-calculation and kernel benchmarks, including neutral EOS controls.

## Regression evidence

\`ComponentGEHenryDerivativeTest\` compares the analytical logarithmic derivative
with a centered finite difference of \(\ln H\), exercises the Pitzer neutral-gas
path, and verifies zero derivative for overflow and unsupported hydrocarbon
fail-closed references. These are regression and mathematical-consistency
checks, not independent solubility validation.

## Next dependency

Adopt one coherent, redistributable common-gas family only after the convention
mapping and primary-source lineage are complete. The preferred first batch is
CO2/CH4/N2/H2S in pure water plus NaCl brine, because it binds EOS validation,
Pitzer neutral-ion terms, reactive CO2/H2S closure, and produced-water use
without mixing parameter formalisms.
