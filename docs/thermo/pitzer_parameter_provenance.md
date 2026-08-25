---
title: "Pitzer Parameter Provenance and Coverage"
description: "Dataset identity, equation conventions, licensing, and fail-closed coverage diagnostics for NeqSim's Pitzer model."
keywords: "Pitzer, electrolyte, activity coefficient, osmotic coefficient, theta, psi, provenance, brine"
---

# Pitzer parameter provenance and coverage

`SystemPitzer` is an electrolyte-GE model. Its interaction coefficients must not be placed in
reaction-equilibrium, mineral-solubility, SIT, eNRTL, Extended-UNIQUAC, or electrolyte-EOS tables.
Pitzer results may validate another model, but direct coefficient transfer requires a separately
derived and reviewed equation mapping.

## Current dataset and coverage contract

The bundled table is identified as `neqsim-legacy-pitzer-parameters-v1`. It stores molality-scale
binary coefficients at 298.15 K and a NeqSim three-term temperature expression:

`p(T) = p(298.15 K) + pT1 (1/T - 1/298.15 K) + pT2 ln(T/298.15 K)`.

This identity documents the existing table; it does not certify every legacy row. The table has no
usable tuple identity for same-sign `theta` or ternary `psi`, and it has no qualified `lambda`,
`zeta`, `mu`, or `eta` rows. Mixed-ion and neutral-solute calculations therefore require explicit
parameter definitions.

`PhasePitzer.getPitzerParameterCoverage()` returns active primary-salt cations and anions plus every
missing binary, `theta`, and `psi` key. For a mixed primary-salt topology, the automatic gate fails
before activity or water-osmotic evaluation when that coverage is incomplete. Established
single-cation/single-anion calculations retain their binary behavior; callers can explicitly audit
their binary row with `requireCompletePitzerParameterCoverage()`. An explicit zero set through
`setBinaryParameters`, `setTheta`, or `setPsi` counts as a definition; an absent value does not.
The neutral families are sparse and opt-in. Once any neutral interaction is configured, every active
neutral-neutral (including repeated species) and neutral-ion `lambda` pair and every
neutral-cation-anion `zeta` tuple must be explicit. If a dataset enables
`mu` or `eta`, its active neutral or same-sign-ion topology is audited too. Missing rows fail before
activity or osmotic evaluation; an explicitly defined zero remains distinct from an absent row.
Acid-base species created as reaction-solver trial variables (`H3O+`, `OH-`, `HCO3-`, and `CO3--`)
are outside this first gate: their transient iteration amounts are not a stable input topology, and
they require a later model-specific reaction-dataset audit. The diagnostic is cached by active
primary-salt topology and parameter revision, and the automatic gate runs once after each standard
level-zero state initialization. Call `init(0)` after changing composition, as required by the
general NeqSim state contract. This work is confined to `PhasePitzer` and `ComponentGePitzer`;
neutral PR, SRK, CPA, Electrolyte-CPA, and Fürst electrolyte-EOS paths do no new work.

### Qualified public-domain CO2-Na2SO4 subset

`PitzerParameterDatasets.applyPhreeqcCo2SodiumSulfate` installs one coherent subset identified as
`usgs-phreeqc-pitzer-b0b3be767158ccc3322d2c816625cf470045e67e-co2-na2so4-v1`.
It is copied exactly from public-domain PHREEQC commit `b0b3be7`, database blob `324f852`, and it
marks the phase as manually populated so the legacy table cannot be mixed into the selected set.
The species use the molality standard state, pressure is not an argument of these interaction
functions, the reference temperature is 298.15 K, and the six coefficients are stored in PHREEQC
order. The Na+/SO4-- 1-2 binary uses alpha1=2 and has no beta2 term.
Java and Python callers can use `SystemPitzer.applyPhreeqcCo2SodiumSulfateParameters()` after adding
the required species; cloning and subsequent `init(0)` calls preserve the selected dataset.

| Species tuple | Family | PHREEQC coefficients `[a0,a1,a2,a3,a4,a5]` | Source-lineage note |
|---|---|---|---|
| Na+ / SO4-- | `beta(0)` | `[2.73e-2, 0, -5.8, 9.89e-3, 0, -1.563e5]` | PHREEQC `pitzer.dat`; exact current public-domain row |
| Na+ / SO4-- | `beta(1)` | `[0.956, 2.663e3, 0, 1.158e-2, 0, -3.194e5]` | same coherent binary row |
| Na+ / SO4-- | `Cphi` (`C0`) | `[3.418e-3, -384, 0, -8.451e-4, 0, 5.177e4]` | unchanged `C0` value; downstream charge normalization is in the equation |
| CO2 / CO2 | `lambda` | `[-1.34e-2, 348, 0.803, 0, 0, 0]` | PHREEQC comment limits this temperature function to 150 °C |
| CO2 / Na+ | `lambda` | `0.085` | constant row in the same database |
| CO2 / SO4-- | `lambda` | `0.075` | constant row in the same database |
| CO2 / Na+ / SO4-- | `zeta` | `-0.015` | required ternary companion row |

This is a deliberately closed species system, not a general brine database. Any additional active
neutral or ion forces the ordinary binary/mixed-ion and neutral-family coverage checks; a missing
interaction is never silently replaced by zero. `isWithinCo2SodiumSulfateValidationRange` records
the independently checked envelope of 303.15–423.15 K and 1–2 mol/kg Na2SO4. It is an evidence
envelope for this subset, not an extrapolation guarantee.

Independent calculation evidence uses IPhreeqc 3.7.3 with the exact database blob above and
MacInnes scaling disabled. Four CO2 states at 298.15, 373.15, and 423.15 K and 0.1–2 mol/kg Na2SO4
match PHREEQC natural-log activity contributions within `1.5e-5`. At 298.15 K and approximately
1 mol/kg Na2SO4, NeqSim gives mean ionic activity coefficient `0.20016248` versus PHREEQC
`0.20137297` (−0.60%) and osmotic coefficient `0.64159881` versus `0.64224702` (−0.10%). The
remaining difference is retained as an explicit tolerance because NeqSim's water electrostatic
correlation is not PHREEQC's optional database `Aphi` function.

The held-out qualitative gate is Dos Santos et al. (2020),
[DOI 10.1021/acs.jced.0c00230](https://doi.org/10.1021/acs.jced.0c00230): 48 CO2-solubility points
at 303.15–423.15 K and 1–2 mol/kg Na2SO4 report increased salting-out with salt molality. The
qualified subset independently produces a larger CO2 activity coefficient from 1 to 2 mol/kg at
373.15 K. The publication is used only as cited validation evidence; no copyrighted table is stored.
A second outside-range comparison is Zhao et al. (2015),
[DOI 10.1002/aic.14825](https://doi.org/10.1002/aic.14825), covering Na2SO4 ionic strength 1–6 mol/kg
at 15 MPa and 323–423 K; it is evidence for later full-VLE work, not grounds to widen this subset's
accepted range.

The current hybrid EOS-GE flash still fails for some gas-forming CO2/brine inventories before a
scientific VLE comparison can be made. That is a separate phase-topology dependency coordinated
with the generic flash campaign; this subset is therefore qualified for aqueous activity and
water/osmotic use only. It must not be described as independently validated CO2 VLE.

### Qualified public-domain Na-K-Cl subset

`PitzerParameterDatasets.applyPhreeqcSodiumPotassiumChloride` installs the coherent subset
`usgs-phreeqc-pitzer-b0b3be767158ccc3322d2c816625cf470045e67e-na-k-cl-v1`. The selected rows were
read twice and checked against public-domain PHREEQC commit `b0b3be7`, `pitzer.dat` blob `324f852`.
The subset keeps the molality standard state and PHREEQC six-term temperature expression at
298.15 K. Both binaries use alpha1=2 and have no `beta(2)` term. It is separate from, and does not
overwrite, `neqsim-legacy-pitzer-parameters-v1`.

| Species tuple | Family | PHREEQC coefficients `[a0,a1,a2,a3,a4,a5]` | Source-lineage note |
|---|---|---|---|
| Na+ / Cl- | `beta(0)` | `[0.07534, 9598.4, 35.48, -0.058731, 1.798e-5, -5.0e5]` | exact public-domain `pitzer.dat` row |
| Na+ / Cl- | `beta(1)` | `[0.2769, 13770, 46.8, -0.069512, 2.0e-5, -748230]` | same coherent binary row |
| Na+ / Cl- | `Cphi` (`C0`) | `[0.00148, -120.5, -0.2081, 0, 1.166e-7, 11121]` | raw `C0`; charge normalization remains in the equation |
| K+ / Cl- | `beta(0)` | `[0.04808, -758.48, -4.7062, 0.010072, -3.7599e-6, 0]` | exact public-domain `pitzer.dat` row |
| K+ / Cl- | `beta(1)` | `[0.2168, 0, -6.895, 0.02262, -9.293e-6, -1.0e5]` | same coherent binary row |
| K+ / Cl- | `Cphi` (`C0`) | `[-0.000788, 91.27, 0.58643, -0.001298, 4.9567e-7, 0]` | raw `C0`; charge normalization remains in the equation |
| K+ / Na+ | `theta` | `[-0.012, 0, 0, 0, 0, 0]` | required same-sign companion |
| Cl- / K+ / Na+ | `psi` | `[-0.0015, 0, 0, 1.8e-5, 0, 0]` | required ternary companion |

The PHREEQC source evaluation also requires two common single-ion terms. The binary derivative
contribution to `F` is summed over every active cation-anion pair, not only the pair containing the
target ion. The normalized `C0` sum is likewise global and contributes
`|z_i| sum_c sum_a m_c m_a C0_ca/(2 sqrt(|z_c z_a|))` to every ion. These terms are enabled only by
an explicitly mapped PHREEQC dataset. The legacy Pitzer path retains its historical result, and all
non-Pitzer models bypass this code.

Independent evidence uses IPhreeqc 3.7.3 with MacInnes scaling disabled and the same Na/K/Cl rows.
Eight charge-balanced states cover pure 1 mol/kg NaCl, pure 1 mol/kg KCl, mixed 0.05/0.05/0.1,
0.5/0.5/1.0, 0.2/0.8/1.0, and 1.5/1.5/3.0 mol/kg Na/K/Cl, plus 0.5/0.5/1.0 at 373.15 and 423.15 K.
At 298.15 K the maximum absolute natural-log single-ion residual is below 0.002; it is below 0.008
at 373.15 K and 0.019 at 423.15 K. Across the matrix, water-activity and osmotic-coefficient
residuals are below 0.0007 and 0.007, respectively. The growing high-temperature difference is
retained rather than fitted away: PHREEQC may use its database `Aphi` function, while NeqSim retains
its existing water dielectric/density correlation. The checked implementation-comparison envelope
is 298.15–423.15 K and 0.1–3.0 mol/kg chloride; it is not a pressure or universal experimental
validity claim. Composition sweeps separately check continuity, repeated execution, normalization,
non-negative phase state, and electroneutrality. Additional active species fail closed until all
required binary and mixed-ion companions are qualified.

An independent experimental-data synthesis checks the NaCl binary without reusing PHREEQC output.
Partanen and Partanen (2020), [DOI 10.1021/acs.jced.0c00402](https://doi.org/10.1021/acs.jced.0c00402),
Tables 6 and 10, recommend mean activity and osmotic coefficients from a traceable extended-Huckel
analysis of vapor-pressure, electrochemical, cryoscopic, and solubility evidence. The article and
tables are licensed CC BY 4.0 and postdate the adopted PHREEQC parameter lineage. At 298.15 K and
0.2, 0.5, 1.0, and 2.0 mol/kg NaCl, NeqSim's maximum relative mean-activity residual is 1.36%, the
maximum absolute osmotic-coefficient residual is 0.0053, and the maximum water-activity residual
derived from the reported osmotic coefficient is below 0.0004. The published recommendations are
rounded to 0.001; acceptance limits are 1.5%, 0.006, and 0.0005, respectively. No coefficient was
refitted.

The KCl binary has a separate held-out check against the critically evaluated 298.15 K values of
Hamer and Wu (1972), [DOI 10.1063/1.3253108](https://doi.org/10.1063/1.3253108), prepared at the
U.S. National Bureau of Standards and published by the U.S. Secretary of Commerce. Four values at
0.0982, 0.5001, 0.9926, and 1.9895 mol/kg are independently reproduced in Table 2 of the open-access
primary experiment by Dash et al. (2012),
[DOI 10.5402/2012/730154](https://doi.org/10.5402/2012/730154), under its CC BY terms. NeqSim's
maximum relative mean-activity residual is 0.110%; the acceptance limit is 0.20%, allowing for the
source's 0.001 rounding without refitting. Dash et al.'s own single-ion-selective-electrode means
are 10.7–13.4% higher at these four states. They are retained as a conflicting method result, not
used to refit or relax the critically evaluated thermodynamic gate. Together with the independent
IPhreeqc water/osmotic checks, this qualifies both binary boundaries of the Na/K/Cl family.

Independent mixed NaCl+KCl measurements exist in Rodil et al. (2009),
[DOI 10.1021/je800389q](https://doi.org/10.1021/je800389q), with a mandatory correction at
[DOI 10.1021/je9002674](https://doi.org/10.1021/je9002674). The ACS supporting information is
publicly downloadable, but the article and correction are copyright ACS and no redistribution
license was established in this audit. No table value is stored. The exact-composition IPhreeqc
matrix therefore remains the mixed-family numerical gate; licensing-clear held-out mixed-salt
experimental data are still the next evidence dependency.

### Versioned PHREEQC catalog and Ca-Mg-Cl-SO4 scale-brine qualification

`PhreeqcPitzerParameterCatalog` lazily reads the exact `PITZER` block from public-domain USGS
PHREEQC 3.9.0-17591 (released 2026-05-13), commit
`b0b3be767158ccc3322d2c816625cf470045e67e`, database blob
`324f852784be84650b77bd7f07f8316aafd8188b`. The 3.9.0 release notes identify revisions to
Mg++, Ba++, CO3--, HCO3-, CO2, and related bicarbonate and neutral-hydrogen Pitzer interactions.
The bundled catalog contains 54 `B0`, 48 `B1`,
8 `B2`, 32 `C0`, 30 `theta`, 59 `psi`, 27 `lambda`, and 10 `zeta` rows. No reaction constant,
mineral `log K`, gas binary, SIT, eNRTL, Extended-UNIQUAC, or electrolyte-EOS coefficient is
imported. The resource is parsed only after an explicit catalog API call, so neutral EOS and legacy
Pitzer paths do no catalog I/O or lookup work.

`applyCompletePhreeqcPitzerCatalog` selects rows by the active aqueous species. Every active
opposite-sign pair requires explicit `B0`, `B1`, and `C0`; every same-sign pair requires `theta`;
every mixed-ion triple requires `psi`; and active neutral solutes require their complete `lambda`
and `zeta` topology. An absent required row fails before phase mutation. A `B2` row is optional
only in the strict sense that it is applied whenever the source defines it; the loader never creates
one or silently substitutes an unqualified zero. This makes a broad source database available while
preserving a closed, reviewable calculation topology.

The first fully exercised four-ion family is Ca++/Mg++/Cl-/SO4--. It includes all four binaries,
both same-sign pairs, and all four ternary compositions:

| Tuple | Family | PHREEQC coefficients `[a0,a1,a2,a3,a4,a5]` |
|---|---|---|
| Ca++ / Cl- | `B0`; `B1`; `B2`; `C0` | `[0.3159,0,0,-3.27e-4,1.4e-7,0]`; `[1.614,0,0,7.63e-3,-8.19e-7,0]`; `[-1.13,0,0,-0.0476,0,0]`; `[1.4e-4,-57,-0.098,-7.83e-4,7.18e-7,0]` |
| Ca++ / SO4-- | `B0`; `B1`; `B2`; `C0` | `[0,0,0,0,0,0]`; `[3.546,0,0,5.77e-3,0,0]`; `[-59.3,0,0,-0.443,-3.96e-6,0]`; `[0.114,0,0,0,0,0]` |
| Mg++ / Cl- | `B0`; `B1`; `C0` | `[0.351,0,0,-9.32e-4,5.94e-7,0]`; `[1.65,0,0,-1.09e-2,2.6e-5,0]`; `[0.00651,0,0,-2.5e-4,2.418e-7,0]` |
| Mg++ / SO4-- | `B0`; `B1`; `B2`; `C0` | `[0.2135,-951,0,-2.34e-2,2.28e-5,0]`; `[3.367,-5.78e3,0,-0.148,1.576e-4,0]`; `[-32.45,0,-3.236e3,21.812,-1.8859e-2,0]`; `[2.875e-2,0,-2.084,1.1428e-2,-8.228e-6,0]` |
| Ca++ / Mg++; Cl- / SO4-- | `theta` | `[0.007,0,0,0,0,0]`; `[0.03,0,0,0,0,0]` |
| Ca++ / Cl- / Mg++; Ca++ / Cl- / SO4-- | `psi` | `[-0.012,0,0,0,0,0]`; `[-0.122,0,0,-1.21e-3,0,0]` |
| Ca++ / Mg++ / SO4--; Cl- / Mg++ / SO4-- | `psi` | `[0.024,0,0,0,0,0]`; `[-0.008,32.63,0,0,0,0]` |

PHREEQC source assigns `alpha1=2` and `alpha2=12` to pairs containing a monovalent ion,
`alpha1=1.4` and `alpha2=12` to 2-2 pairs, and `alpha1=2`, `alpha2=50` to the remaining charge
orders. Current-master NeqSim previously discarded `B2` for every non-2-2 pair, which omitted the
explicit CaCl2 term. The catalog path now maps this PHREEQC convention in activity, common
`B-prime`, and both water/osmotic calculations; legacy rows with zero `B2` are unchanged.

Held-out CaCl2 mean ionic activity coefficients come from Partanen (2013),
[DOI 10.1021/je300852v](https://doi.org/10.1021/je300852v), through the NIST ThermoML archive.
The archived states are on the molality scale at 298.15 K and 101 kPa with reported uncertainty
0.001. At 0.1, 0.5, 1.0, and 2.0 mol/kg, NeqSim predicts 0.515385, 0.444320, 0.497186, and
0.798718 versus 0.517, 0.449, 0.502, and 0.790; the maximum relative residual is 1.104%, without
parameter fitting. NIST identifies the ThermoML archive as publisher-permitted and its Data.gov
catalog applies the [NIST public-data license](https://www.nist.gov/open/license). Exact-version
mixed-family PHREEQC activity/water comparisons remain a validation dependency.

Held-out MgCl2 mean ionic activity coefficients come from Partanen (2013),
[DOI 10.1016/j.jct.2013.06.016](https://doi.org/10.1016/j.jct.2013.06.016), through the same
publisher-permitted NIST ThermoML archive. At 0.1667, 0.3333, 1.0, and 2.0 mol/kg and 298.15 K,
NeqSim predicts 0.493757, 0.469707, 0.564503, and 1.051132 versus 0.5018, 0.4796, 0.5776, and
1.0695. The maximum relative residual is 2.27%, without parameter fitting. The binary CaCl2 and
MgCl2 evidence is acceptable for screening inside this narrow envelope, but it does not qualify the
four-ion mixture.

The independent MgCl2+MgSO4 potentiometric study at 298.15 K,
[DOI 10.1016/j.jct.2011.08.020](https://doi.org/10.1016/j.jct.2011.08.020), reports
`theta(Cl,SO4)=0.0252 +/- 0.0042` and `psi(Mg,Cl,SO4)=-0.0049 +/- 0.0003`. The PHREEQC catalog
values are 0.03 and -0.008, respectively. Theta agrees within about 1.14 reported standard
uncertainties, while psi differs by about 10.3. These are fitted parameters from a different
experimental/model lineage, not direct transferable validation values. NeqSim keeps the coherent
PHREEQC family unchanged and records the disagreement; it does not mix the two parameter sets.

Independent observable validation now uses the 28 mixed MgCl2+MgSO4 water activities of Guendouzi
and Errougui (2007), [DOI 10.1021/je7002176](https://doi.org/10.1021/je7002176). The hygrometric
measurements are at 298.15 K and 101 kPa for MgCl2 ionic-strength fractions 0.20, 0.50, and 0.80,
with total formula-unit molality from 0.35 to 3.80 mol/kg water. The NIST ThermoML record specifies
the pure-water standard state and 95% combined expanded water-activity uncertainties of 0.001 to
0.004. The checked JSON has MD5 `29969a5f814eb0ad6d7ca5b4f093b658`; the unchanged numerical
subset is stored with NIST acknowledgment under the
[NIST public-data license](https://www.nist.gov/open/license). Composition is mapped without
speciation or fitting as Mg++ = m(MgCl2)+m(MgSO4), Cl- = 2m(MgCl2), and SO4-- = m(MgSO4).

Across all 28 states, NeqSim's maximum absolute water-activity residual is 0.002985 and its RMSE is
0.001275; 26 states fall inside their individual reported 95% expanded uncertainty. Both calculated
and experimental water activity decrease monotonically with total molality on each composition
line. The regression also checks formula-unit material balance, electroneutrality, normalized and
non-negative phase state, water-activity/osmotic-coefficient identity, deterministic repetition,
changed-state equivalence, and complete parameter coverage. No coefficient was fitted or changed.
This qualifies only mixed Mg-Cl-SO4 water activity at the three documented composition lines and
298.15 K; it does not qualify Ca-bearing mixtures, pressure/temperature extrapolation, individual
ion activities, mineral saturation, or precipitation.

`PitzerParameterDatasets.getQualification(datasetId)` exposes this distinction to Java and Python
callers. Complete PHREEQC catalog topology reports `PARTIALLY_EXPERIMENTALLY_VALIDATED`, lists the
CaCl2 and MgCl2 binaries plus mixed MgCl2-MgSO4 water activity as checked, and explicitly leaves
Ca-bearing mixed activity and mineral precipitation unresolved. The range diagnostic accepts only
the three experimentally checked ionic-strength-fraction lines and their line-specific molality
intervals. Unknown or private dataset identities report `UNQUALIFIED`. This metadata lookup is
outside the activity/property kernels and adds no non-electrolyte overhead.

The catalog is an aqueous-GE parameter source, not a complete scale or process model by itself.
`SystemPitzer` keeps SRK gas and oil role phases and exposes catalog application on its Pitzer
aqueous role. Hybrid gas-oil-water phase stability, model-specific aqueous reactions, mineral
solubility/precipitation complementarity, and process mass/energy/property closure retain their
separate gates and database semantics.

### Mineral-solubility and precipitation boundary

`ThermodynamicOperations.precipitateScale` now couples the selected aqueous activity model to the
existing COMPSALT solubility-product correlations for one named pure mineral. At 298.15 K and
1.01325 bara, the unmodified COMPSALT correlations give log10 Ksp values -9.934923 for BaSO4,
-6.631329 for SrSO4, and -4.355596 for anhydrite (`CaSO4_A`). The exact public-domain PHREEQC
3.9.0-17591 database at commit `b0b3be767158ccc3322d2c816625cf470045e67e` gives -9.97, -6.63,
and -4.362, respectively. These are independent mineral-constant comparisons; no PHREEQC mineral
constant was fitted or copied into COMPSALT.

The complete PHREEQC Ca-Mg-Cl-SO4 interaction family is therefore used for activity-consistent
anhydrite precipitation validation. The same catalog contains no explicit Ba++/SO4-- `B0` row, so
a fully qualified barite Pitzer precipitation calculation still fails closed. The close barite Ksp
agreement does not qualify its missing aqueous interaction family. A redistributable, coherent
Ba/Sr sulfate family with binary and all required mixed-brine terms plus held-out solubility or
saturation evidence is the next parameter dependency.

`ThermodynamicOperations.precipitateScales` adds a model-neutral orchestration layer for competing
pure COMPSALT minerals. It does not merge parameter semantics: `SystemPitzer` continues to evaluate
molality-scale activities from the explicitly selected PHREEQC family, while electrolyte EOS
systems continue to use their own aqueous fugacity/activity implementation. No Pitzer interaction
is copied into an EOS or reaction table.

The active set owns an external, non-negative pure-solid ledger, precipitates supersaturated
minerals, and redissolves undersaturated present minerals until the maximum complementarity
violation is at most `1e-6` log10-SR. It fails closed on non-convergence and on a dissolved-plus-solid
component residual above `1e-10 mol`. A continuation API accepts the previous ledger after a T/P or
composition change, preventing a stale solid state in process sequences.

The algorithmic regression uses `CaSO4_A` and `CaSO4_G`, whose unmodified COMPSALT correlations at
298.15 K give log10 Ksp values `-4.355596` and `-4.578529`, respectively. Because both consume one
Ca++ and one SO4--, the lower-Ksp correlation must be present and the higher-Ksp phase absent,
independent of input order. This is an internal complementarity/topology regression, separate from
the PHREEQC anhydrite Ksp comparison and from fitted-data validation. The `CaSO4_G` COMPSALT row
does not explicitly represent crystallization-water activity or mass; therefore the regression does
not qualify gypsum hydration thermodynamics, and that standard-state extension remains a distinct
parameter/model boundary.

`PitzerCatalogPerformanceBenchmark` measures nine fixed-work batches after warmup. On OpenJDK 17
in the development container, the median four-ion activity/osmotic kernel was 10.542 microseconds
and the complete aqueous `init(3)` plus physical-property calculation was 0.168756 milliseconds.
The same neutral SRK property control measured 31.940 microseconds before catalog loading and
22.576 microseconds after loading (ratio 0.707, reflecting additional JIT warmup rather than a
regression). No neutral EOS production class is changed, and catalog parsing is absent until its
explicit API is called.
These figures are diagnostic wall-clock evidence, not portable hardware guarantees.

## Source-comparison matrix

The exact PHREEQC Pitzer block is now stored as a versioned catalog. A calculation may activate only
an explicit, complete topology; the CO2-Na2SO4, Na-K-Cl, and Ca-Mg-Cl-SO4 families above have
dedicated evidence. Other catalog rows remain source-available rather than scientifically qualified
until their complete topology and validation matrix are documented.

| Source / version | Species and parameter families | Conditions, equation, scale, and alpha | Evidence, range, uncertainty | Lineage and reuse status | Mapping decision / conflict |
|---|---|---|---|---|---|
| NeqSim legacy CSV, audited 2026-08-23 | 30 cation-anion rows; `beta0`, `beta1`, `beta2`, `Cphi`; columns named `theta` and `psi_common_ion` contain only zero and no tuple identity | 298.15 K reference; CSV `Tmin`/`Tmax`; NeqSim three-term T expression; molality activity path; hard-coded alpha 2.0 for pairs containing a univalent ion and 1.4/12.0 for 2-2 | References vary from publications to “Estimated”; per-row residuals and uncertainty absent | Repository data under NeqSim's license; legacy lineage is not a coherent qualified release | Preserve as dataset v1. Binary 298.15 K NaCl is numerically consistent with the PHREEQC example, but temperature and electrostatic mappings are not equivalent. Missing mixed interactions now fail closed. |
| Pitzer (1973), [general equations](https://doi.org/10.1021/j100621a026), and Pitzer–Mayorga (1973), [strong electrolytes](https://doi.org/10.1021/j100638a009) | Foundational binary virial families for strong electrolytes; individual tuples must be read from the original article | Molality-scale Pitzer formulation near 298.15 K; exact parameter-specific alpha and fitted range must be taken from the paper | Mean activity and osmotic-coefficient fits; no value transcribed in this review | Primary publications; ACS copyright, so values require row-level reuse review or redistributable corroboration | Equation lineage accepted; no direct adoption pending exact NeqSim term-by-term mapping. |
| Harvie, Møller and Weare (1984), [natural-water model](https://doi.org/10.1016/0016-7037(84)90098-X) | Na/K/Mg/Ca/H with Cl/SO4/OH/HCO3/CO3/CO2/H2O; binary and mixed interactions | 25 °C, high ionic strength; molality-scale Pitzer model | Fitted isopiestic, EMF, and solubility data; multicomponent comparisons outside subsystems are reported | Primary Elsevier article; numerical-table redistribution not established here | High-priority scientific comparison. No coefficient copied; full family and electrostatic convention must be mapped together. |
| Pitzer (1975), [higher-order electrostatic mixing](https://doi.org/10.1007/BF00646562) | Nonsymmetric same-sign electrostatic terms `Etheta` and its ionic-strength derivative for unequal charge pairs; no fitted short-range coefficient is supplied by this term | Molality-scale Pitzer formulation; the term is zero for equal charges and depends on charge tuple, ionic strength, and `Aphi` | Primary equation source; it establishes model structure rather than a parameter fit | Primary Springer article; no numerical table copied | Equation structure accepted in this increment: the public PHREEQC recurrence and its ion/activity/osmotic placement are mapped directly. No fitted coefficient is copied; short-range tuple coverage remains independently fail-closed. |
| USGS PHRQPITZ (Plummer et al. 1988), [WRIR 88-4153](https://doi.org/10.3133/wri884153), PHREEQC 3.8.6 tag commit [`74cdaf0`](https://github.com/phreeqc-dev/phreeqc3/commit/74cdaf00f90b15b7a5bbc03f405eb2f8129aacf1), and [PHREEQC 3.9.0-17591](https://github.com/phreeqc-dev/phreeqc3/releases/tag/v3.9.0) commit [`b0b3be7`](https://github.com/phreeqc-dev/phreeqc3/commit/b0b3be767158ccc3322d2c816625cf470045e67e) | Na/K/Mg/Ca/H plus major anions and extended Fe/Mn/Sr/Ba/Li/Br; `B0`, `B1`, `B2`, `C0`, `theta`, `psi`, `lambda`, `zeta`, and six temperature coefficients. Release 3.9.0 revises Mg/Ba/carbonate/bicarbonate/CO2 species and related HCO3 and neutral-H2 Pitzer interactions. | Molality scale; PHREEQC enables nonsymmetric electrostatic mixing by default. Default alpha is charge dependent: `2/12` when a pair contains a monovalent ion, `1.4/12` for 2-2, and `2/50` otherwise. Pressure is absent from these interaction functions. | Appelo (2015), [DOI 10.1016/j.apgeochem.2014.11.007](https://doi.org/10.1016/j.apgeochem.2014.11.007), documents database principles and calculations from 0–200 °C and 1–1000 atm; applicability remains parameter/system specific. Exact fitted observables, residuals, and uncertainty remain row/source specific. | USGS software and data are public domain. Audited release date: 2026-05-13. Audited blobs: [`pitzer.cpp` `1f32a08`](https://github.com/phreeqc-dev/phreeqc3/blob/b0b3be767158ccc3322d2c816625cf470045e67e/src/pitzer.cpp) and [`pitzer.dat` `324f852`](https://github.com/phreeqc-dev/phreeqc3/blob/b0b3be767158ccc3322d2c816625cf470045e67e/database/pitzer.dat). | The exact interaction block is stored as a lazy catalog; calculation activation fails closed on incomplete topologies. CO2-Na2SO4, Na-K-Cl, and Ca-Mg-Cl-SO4 have dedicated mappings/evidence. Legacy rows remain default and are not overwritten. |
| Partanen and Partanen (2020), [traceable NaCl values](https://doi.org/10.1021/acs.jced.0c00402) | Recommended NaCl mean activity and water osmotic coefficients; no Pitzer parameter is supplied or adopted | 273.15–373.15 K, 0.2 mol/kg to saturation; molality scale; extended-Huckel model fitted independently of the PHREEQC implementation | Traceable synthesis of vapor-pressure, electrochemical, cryoscopic, and solubility evidence; tabulated values are rounded to 0.001 | Primary open-access article and numerical tables under CC BY 4.0 | Four 298.15 K points from 0.2–2.0 mol/kg are held-out validation only. Max NeqSim residuals are 1.36% in mean activity, 0.0053 in osmotic coefficient, and below 0.0004 in derived water activity; no coefficient was refitted. |
| Hamer and Wu (1972), [NBS/NIST critical evaluation](https://doi.org/10.1063/1.3253108), independently reproduced by Dash et al. (2012), [open-access table](https://doi.org/10.5402/2012/730154) | Critically evaluated KCl mean activity coefficients; no Pitzer parameter is supplied or adopted | 298.15 K, molality scale, 1:1 electrolyte; four selected states span 0.0982–1.9895 mol/kg | Hamer-Wu evaluates thermodynamic literature; reproduced values are rounded to 0.001. Dash et al.'s direct single-ion-selective-electrode means are 10.7–13.4% higher at the same states and are treated as a method conflict. | U.S. NBS authorship and U.S. Secretary of Commerce publication; the four checked values are also reproduced in a CC BY open-access primary experiment | Held-out binary validation only. Max NeqSim mean-activity residual is 0.110% against a 0.20% gate; no coefficient was refitted. The critically evaluated values take precedence over the conflicting single-ion method. |
| Partanen (2013), [traceable MgCl2 values](https://doi.org/10.1016/j.jct.2013.06.016) | Recommended MgCl2 mean activity and water osmotic coefficients; no Pitzer parameter is supplied or adopted | 298.15 K, 0–3 mol/kg, molality scale; four checked states span 0.1667–2.0 mol/kg | Traceable synthesis and tests against isopiestic and vapor-pressure literature; tabulated values are rounded | Journal of Chemical Thermodynamics data distributed through the publisher-permitted NIST ThermoML archive; NIST public-data terms and citation request recorded | Held-out binary validation only. Max NeqSim mean-activity residual is 2.27% without refitting; this does not qualify mixed Mg chloride/sulfate brines. |
| Dinane, Messnaoui and Abou Nohra (2012), [mixed MgCl2+MgSO4 experiment](https://doi.org/10.1016/j.jct.2011.08.020) | Mean MgCl2 activities over total ionic strength 0.001–8 mol/kg; fitted `theta(Cl,SO4)` and `psi(Mg,Cl,SO4)` | 298.15 K; salt ratios 2.5, 5, 7.5, 10, and 15; potentiometric Mg-ISE/Ag-AgCl method and Pitzer fit | `theta=0.0252 +/- 0.0042`, `psi=-0.0049 +/- 0.0003`; PHREEQC has 0.03 and -0.008 | Primary Elsevier article; no table is copied and numerical-table redistribution was not established | Theta broadly agrees, but psi materially conflicts. No coefficient is adopted or mixed; the coherent PHREEQC family is assessed against separate observable data. |
| Guendouzi and Errougui (2007), [mixed MgCl2+MgSO4 water activity](https://doi.org/10.1021/je7002176), NIST ThermoML MD5 `29969a5f814eb0ad6d7ca5b4f093b658` | 28 measured water activities; no interaction parameter is adopted. The paper also fits mixing parameters, but they are not transcribed or mixed with PHREEQC. | 298.15 K, 101 kPa; MgCl2 ionic-strength fractions 0.20, 0.50, 0.80; total formula-unit molality 0.35–3.80 mol/kg; molality composition and pure-water activity standard state | Hygrometric method; per-state 95% combined expanded uncertainty 0.001–0.004. NeqSim max absolute residual 0.002985, RMSE 0.001275, and 26/28 states inside individual uncertainty without refitting. | Primary article; numerical record is publisher-permitted NIST ThermoML. Stored unchanged subset follows the NIST public-data license and acknowledgment terms. | Accepted as held-out mixed Mg-Cl-SO4 water-activity evidence only. It supports PHREEQC `B0/B1/B2/C0`, Cl-/SO4-- `theta`, Mg++/Cl-/SO4-- `psi`, charge-dependent alpha, and electrostatic conventions as one coherent calculation; it does not transfer a fitted parameter or qualify Ca/minerals. |
| Rodil, Arce, Wilczek-Vera and Vera (2009), [mixed NaCl+KCl experiment](https://doi.org/10.1021/je800389q) and [mandatory correction](https://doi.org/10.1021/je9002674) | Individual Cl-/Na+/K+ activity evidence in mixed NaCl+KCl at cation molal fractions 0.75, 0.5, and 0.25 | 298.15 K; total chloride to 4 mol/kg; Henderson liquid-junction correction and single-ion convention | Original paper reports the experiment; the correction replaces erroneous Na+/K+ tabulations without changing conclusions | ACS supporting information is publicly downloadable, but copyright and no explicit data-redistribution license were established | Candidate held-out mixed-family validation only. No value is stored; licensing-clear data and convention mapping remain the blocker. Exact-composition IPhreeqc checks are retained as implementation evidence, not a substitute for experiment. |
| Kaasa (1998), *Prediction of pH, mineral precipitation and multiphase equilibria during oil recovery*, [National Library item](https://www.nb.no/items/d1d68b489b8ee6704786a011fd2e7283), supplied scan inspected 2026-08-23 | Appendix F printed pp. 260–263: binary `beta(0)`, `beta(1)`, occasional `beta(2)`, and `Cphi` for oil-recovery-brine ions; pp. 264–266: same-sign `theta` and ternary `psi`; pp. 266–267: neutral `lambda` and `ksi` (zeta) for CO2, H2S, and CH4 systems | Molality-scale Pitzer model; 298.15 K reference. Appendix F equation (F.1) uses six coefficients in raw order `[a,b,c,d,e,f]`; NeqSim/PHREEQC order is `[a,d,e,b,c,f]`. Pressure effects are handled separately in chapter 4. | Appendix F cites sources 1–16, including primary literature and rows marked “This work.” Chapter 4 §4.3.2, printed pp. 100–101, states that source ranges differ and some high-temperature fits were forced toward zero where extrapolation became nonphysical, with a poorer fit to experiments. Row uncertainty and complete validity are not tabulated. | User-supplied scan was visually inspected against its text layer. Thesis-table redistribution terms remain unclear; no numerical row was copied. Original cited publications require independent row-level checks and their own reuse review. | Formula, page/family inventory, and coefficient-order permutation accepted. `PitzerTemperatureFunction.fromKaasa1998` performs only the tested order mapping. No coefficient, forced extrapolation, or “This work” estimate was adopted. |
| THEREDA release 2026-01, [official database](https://www.thereda.de/) | Quality-controlled high-salinity Pitzer datasets, including variable-temperature sets | Official site reports an oceanic set from 0 to 110 °C; formulation and scale are dataset specific | Official site reports 425 tests and more than 3200 results; row-level uncertainty remains dataset specific | Current release and licensing/reuse terms must be confirmed before storage | Candidate validation and provenance source only; no value adopted while row-level lineage, reuse, range, and complete family compatibility remain unresolved. |

## Kaasa 1998 supplied-scan audit

The supplied scan removed the access blocker but not the scientific or licensing gates. Selected
Appendix F citations were checked against their primary publisher records before any adoption decision:

| Kaasa reference | Primary source and verified scope | Agreement / conflict decision |
| --- | --- | --- |
| [8] | He and Morse (1993), [carbonate/calcite brines](https://doi.org/10.1016/0016-7037(93)90137-L): CO2, bicarbonate, carbonate, and calcite evidence in Na/K/Ca/Mg chloride/sulfate solutions from 0 to 90 °C at about 0.1032 MPa | Supports the thesis family and temperature lineage, but does not establish redistribution rights or validate every transcribed coefficient. No row adopted. |
| [13] | Harvie, Møller and Weare (1984), [natural-water model](https://doi.org/10.1016/0016-7037(84)90098-X): mixed Na/K/Mg/Ca/H chloride/sulfate/carbonate system at 25 °C and high ionic strength | Family topology agrees with Appendix F. The primary model is a 25 °C reference, so it cannot independently qualify Kaasa's later temperature fits. No row adopted. |
| [14] | Pabalan and Pitzer (1987), [high-temperature mixed electrolytes](https://doi.org/10.1016/0016-7037(87)90295-X): Na/K/Mg chloride/sulfate/hydroxide mixtures and mineral solubility | Supports temperature-dependent theta/psi lineage for its stated system. Species coverage is narrower than the full thesis table and reuse remains publisher-controlled. No row adopted. |
| [16] | Duan et al. (1992), [methane in brines](https://doi.org/10.1016/0016-7037(92)90215-5): CH4 solubility in 0–6 molal brines, 0–250 °C, and 0–1600 bar | Supports the neutral-methane interaction lane only. NeqSim represents neutral Pitzer families, but no complete, redistributable CH4 family with the required hybrid EOS-GE validation is adopted, so this topology still fails closed. |

Appendix F
printed page 259 defines

`p(T) = a + b(T-Tr) + c(T²-Tr²) + d(1/T-1/Tr) + e ln(T/Tr) + f(1/T²-1/Tr²)`,

with `a=p(298.15 K)`. This is algebraically the PHREEQC six-term function, but its printed
coefficient columns are not in PHREEQC storage order. The exact permutation is
`[a,b,c,d,e,f] -> [a,d,e,b,c,f]`. The setup-only
`PitzerTemperatureFunction.fromKaasa1998` factory makes that permutation explicit, validates the
six finite coefficients through the immutable value object, and performs no unit, standard-state,
family, or numerical-table conversion.

The visual audit found the full implemented binary/theta/psi families and additional neutral-ion
families that NeqSim does not yet represent. The tables cover H/Na/K/Mg/Ca/Sr/Ba/Fe with major
chloride, sulfate, bisulfate, hydroxide, carbonate, bicarbonate, bromide, bisulfide, and acetate
species, followed by CO2/H2S/CH4 neutral interactions. This is a provenance inventory, not a usable
dataset: source references and experimental ranges vary by row, some rows are thesis estimates, and
chapter 4 documents high-temperature zero-forcing used to suppress nonphysical extrapolation.
Therefore all numerical rows remain rejected until an original redistributable source independently
agrees, the complete interacting family is available, row ranges and uncertainty are recorded, and
held-out activity, osmotic/water-activity, speciation, mineral, mixed-brine, continuity, balance, and
performance validation pass. No thesis coefficient is stored in NeqSim.

## Adoption gate

A parameter-set PR must preserve a versioned dataset identity and per-row provenance. It must map
all applicable binary, same-sign, ternary, neutral-ion, alpha, electrostatic, and temperature terms;
document standard state, species charge, units, pressure assumptions, fitted observables, validity,
uncertainty, preprocessing, and license; and validate against data outside the fit. Required checks
include mean activity and osmotic coefficients or water activity, mixed-brine speciation and mineral
saturation, electroneutrality and material balance, derivative continuity, boundary behavior,
determinism, and an independently configured exact-version implementation such as PHREEQC.

The supplied Kaasa scan was inspected at chapter 4 §4.3.2 (printed pp. 100–101) and Appendix F
(printed pp. 259–267). No Kaasa numerical parameter was transcribed or adopted because reuse, row
lineage, ranges, and independent agreement remain unresolved. The exact public-domain PHREEQC
CO2-Na2SO4 and Na-K-Cl subsets are separately versioned: their complete companion families,
equation mappings, range evidence, and independent IPhreeqc gates are recorded above. Conflicting
NeqSim legacy NaCl/KCl rows remain available only under the unchanged legacy identity; no family is
silently mixed across formulations.

## Unequal-charge electrostatic mixing mapping

The unequal-charge mapping keeps Pitzer's parameter-free nonsymmetric electrostatic term separate
from the fitted short-range `theta` coefficient. For same-sign ions $j$ and $k$, NeqSim evaluates
the public-domain PHREEQC 3 `ETHETAS` recurrence at the current molal ionic strength and
$A_\\phi$. Equal-charge pairs return exactly zero. Unequal-charge pairs contribute
$\\theta_{jk}+E\\theta_{jk}$ to the direct single-ion term,
$m_jm_k\\,dE\\theta_{jk}/dI$ to the common activity-coefficient function, and
$m_jm_k(E\\theta_{jk}+I\\,dE\\theta_{jk}/dI)$ to both water/osmotic-coefficient implementations.

This is the term-by-term mapping in PHREEQC source commit
[`b0b3be7`](https://github.com/phreeqc-dev/phreeqc3/blob/b0b3be767158ccc3322d2c816625cf470045e67e/src/pitzer.cpp#L1510-L1650).
The recurrence coefficients are USGS public-domain source values, not fitted thermodynamic data.
Regression values for charges +1/+2 at independently fixed $A_\\phi=0.392$ cover ionic strengths
0.01, 0.1, 1, and 5 mol/kg; a centered finite-difference check separately gates the derivative.
A per-thread recurrence workspace preserves concurrency without per-call recurrence allocations, and
a cached component-topology gate bypasses the kernel for equal-charge and ordinary binary salts.

The electrostatic mapping itself does not qualify or adopt any binary, `theta`, or `psi` coefficient.
Mixed-brine coverage still fails closed until every short-range tuple is explicitly defined. The
later sections resolve the PHREEQC six-term temperature representation, neutral families, and the
`C0`/`Cphi` equation convention. Parameter-source conflicts outside the qualified CO2-Na2SO4 and
Na-K-Cl subsets and reaction-species coverage remain unresolved. The supplied Kaasa scan was inspected during this mapping, but no Kaasa numerical value was copied
or adopted. Its same-sign and ternary tables confirm the required family topology; source lineage,
reuse, validity, and independent numerical agreement remain row-level blockers.

The next parameter dependency is a complete redistributable mixed-oilfield-brine family with
row-level lineage and held-out mixed-brine validation. The two active subsets are not silently
expanded with Mg/Ca/Sr/Ba/Fe, sulfate/carbonate, neutral-gas, or reaction parameters.

## Six-term temperature-function mapping

NeqSim also supports the PHREEQC six-coefficient temperature function as an explicit, sparse,
dataset-owned override:

`p(T) = a0 + a1(1/T - 1/Tr) + a2 ln(T/Tr) + a3(T - Tr) + a4(T² - Tr²) + a5(1/T² - 1/Tr²)`

The mapping follows `calc_pitz_param` in the audited public-domain PHREEQC source
(commit [`b0b3be7`](https://github.com/phreeqc-dev/phreeqc3/blob/b0b3be767158ccc3322d2c816625cf470045e67e/src/pitzer.cpp#L1721-L1850)).
PHREEQC fixes (T_r=298.15) K in `PTEMP`; the NeqSim value object stores the reference
temperature explicitly so imported datasets cannot silently assume a different reference. Values within
0.001 K of the reference reproduce PHREEQC's exact (a_0) branch. Temperature is absolute kelvin, and
the returned parameter retains the source parameter's units and standard-state convention.

| Family | PHREEQC six-term support | NeqSim mapping in this increment | Adoption decision |
| --- | --- | --- | --- |
| `beta(0)`, `beta(1)`, `C` | `B0`, `B1`, `C0` use all six coefficients | atomic binary setter for `beta0`, `beta1`, and NeqSim `Cphi` | Function adopted; PHREEQC `C0` maps identically to NeqSim `Cphi`; qualified CO2-Na2SO4 and Na-K-Cl coefficients adopted |
| `beta(2)` | `B2` uses all six coefficients | explicit `beta2` temperature setter and temperature-aware activity/osmotic calls | Function adopted; no coefficient adopted |
| `theta` | `THETA` uses all six coefficients | sparse same-sign-pair function, including both ion and water paths | Function and qualified K+/Na+ coefficient adopted for the Na-K-Cl subset |
| `psi` | `PSI` uses all six coefficients | sparse ternary function, including both ion and water paths | Function and qualified Cl-/K+/Na+ coefficient adopted for the Na-K-Cl subset |
| `lambda`, `zeta`, `mu`, `eta` | all four use six coefficients | sparse immutable neutral-family tuples in activity and both water/osmotic paths | Function and equation placement adopted; qualified CO2-Na2SO4 `lambda`/`zeta` rows adopted |
| `Aphi` | optional database function | NeqSim retains its existing water dielectric/density correlation | Not replaced; separate validation is required |

## Neutral-solute interaction mapping

The neutral layer follows the public [PHREEQC PITZER contract](https://water.usgs.gov/water-resources/software/PHREEQC/documentation/phreeqc3-html/phreeqc3-37.htm)
and exact source commit [`b0b3be7`](https://github.com/phreeqc-dev/phreeqc3/blob/b0b3be767158ccc3322d2c816625cf470045e67e/src/pitzer.cpp).
The parameter families remain distinct:

- `lambda` is a pair containing at least one neutral solute: neutral-ion or neutral-neutral;
- `zeta` is a neutral-cation-anion tuple;
- `mu` is a neutral-neutral-neutral tuple, including repeated neutral species; and
- `eta` is a neutral-cation-cation or neutral-anion-anion tuple.

For a distinct lambda pair, each species receives `2 m_other lambda` and the osmotic sum receives
`m_neutral m_ion lambda`. Zeta and eta use the direct derivative of their three-species molality
product. Mu preserves PHREEQC's permutation multiplicities: 1 for three identical neutrals, 3 when
two are identical, and 6 when all are distinct; repeated component slots are differentiated before
their contributions are accumulated. All four families use the same six-term temperature function
documented above. The water-activity denominator includes neutral-solute molality only after this
opt-in layer is configured, which preserves the legacy result exactly when its sparse map is empty.

`PitzerNeutralParameterCoverage` distinguishes absent rows from explicit zero values. Lambda and
zeta are mandatory for every active neutral/ion topology once the layer is enabled. Mu and eta become
topology-complete gates when their family is present. This prevents a partly imported gas/brine
dataset from silently treating an unqualified missing cross-interaction as zero.

The equations are mapped on the molality scale and contribute to natural-log activity coefficients
and the common Pitzer osmotic sum. They do not convert activity scales, standard states, pressure
corrections, or species definitions. The Kaasa Appendix F `lambda` and `ksi` (`zeta`) rows for CO2,
H2S, and CH4 are now structurally representable, but no thesis value is copied or adopted. Row-level
lineage, reuse rights, range, uncertainty, complete companion interactions, and held-out validation
remain mandatory before a versioned parameter dataset can use them.

## PHREEQC `C0` and NeqSim `Cphi` convention

PHREEQC parses the database option `-C0` as `TYPE_C0`. In both its complete and optimized
parameter loops, the public-domain implementation contributes
`C0/(2*sqrt(abs(zM*zX)))` to the Pitzer binary term
([exact source](https://github.com/phreeqc-dev/phreeqc3/blob/b0b3be767158ccc3322d2c816625cf470045e67e/src/pitzer.cpp#L1022-L1030)).
NeqSim stores the corresponding database field as `Cphi` and applies the same
`Cphi/(2*sqrt(abs(zM*zX)))` normalization in single-ion activity and both independent
water/osmotic paths. PHREEQC `C0` is therefore identical to NeqSim `Cphi`; it is not the
already charge-normalized `C` term.

The explicit `setPhreeqcBinaryTemperatureCoefficients` API passes every PHREEQC `-C0`
coefficient unchanged. Callers must not pre-divide or multiply a source value by
`2*sqrt(abs(zM*zX))`. Synthetic 1-1 and 2-1 tuple regressions pin the identity mapping and
the distinct downstream charge normalization at 298.15 and 373.15 K. The setter executes only
while a parameter dataset is configured; runtime activity equations, the legacy Pitzer dataset,
electrolyte EOS models, reaction tables, and all neutral models are unchanged. Explicit PHREEQC
datasets additionally enable the model's global normalized `C0` sum and global binary-derivative
`F` sum; these opt-in terms are required for mixed-ion single-ion activity equivalence.

The equation-convention mapping alone does not qualify a PHREEQC row or establish pressure or
temperature validity. PHRQPITZ and the exact PHREEQC source are USGS public-domain evidence. The
Na-K-Cl rows above are adopted only as their complete named family with independent IPhreeqc checks;
other rows remain unqualified. The supplied Kaasa scan independently confirms the printed Cphi
family name and six-term algebra, but no thesis value is reused because its table-redistribution
terms and row-level qualification remain unresolved.

The legacy NeqSim database columns `*_25`, `*_T1`, and `*_T2` keep their established
three-coefficient behavior. The six-term layer is empty by default and guarded before map lookup, so
legacy binary systems pay only one predictable boolean branch and non-Pitzer models execute no new
code. Setter calls are explicit and retain dataset identity and mixed-brine coverage diagnostics.
Functions survive serialization; clones copy only the sparse map and share immutable function values.

Synthetic coefficients continue to isolate the public equation mapping at 298.15 and 373.15 K.
Separately, the named PHREEQC CO2-Na2SO4 and Na-K-Cl helpers embed only their qualified rows; they do
not bulk-import `pitzer.dat`, change the reaction database, or select reactions. Their explicit
temperature/molality evidence envelopes do not make pressure-dependent VLE, extrapolation, or any
additional species topology validated.

The supplied Kaasa (1998) scan was visually inspected at chapter 4 §4.3.2 and Appendix F.
Its family inventory and coefficient-order convention are recorded above, but no numerical table value
was copied or adopted. The next parameter dependency is a redistributable, independently checked
mixed oilfield-brine family with row-level lineage and held-out mean-activity, osmotic,
water-activity, speciation, and mixed-brine validation.
