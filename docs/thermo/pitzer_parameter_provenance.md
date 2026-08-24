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

## Source-comparison matrix

Only the complete CO2-Na2SO4 subset above is adopted. Other candidate values remain comparison
evidence unless this document explicitly identifies them as part of that versioned set.

| Source / version | Species and parameter families | Conditions, equation, scale, and alpha | Evidence, range, uncertainty | Lineage and reuse status | Mapping decision / conflict |
|---|---|---|---|---|---|
| NeqSim legacy CSV, audited 2026-08-23 | 30 cation-anion rows; `beta0`, `beta1`, `beta2`, `Cphi`; columns named `theta` and `psi_common_ion` contain only zero and no tuple identity | 298.15 K reference; CSV `Tmin`/`Tmax`; NeqSim three-term T expression; molality activity path; hard-coded alpha 2.0 for pairs containing a univalent ion and 1.4/12.0 for 2-2 | References vary from publications to “Estimated”; per-row residuals and uncertainty absent | Repository data under NeqSim's license; legacy lineage is not a coherent qualified release | Preserve as dataset v1. Binary 298.15 K NaCl is numerically consistent with the PHREEQC example, but temperature and electrostatic mappings are not equivalent. Missing mixed interactions now fail closed. |
| Pitzer (1973), [general equations](https://doi.org/10.1021/j100621a026), and Pitzer–Mayorga (1973), [strong electrolytes](https://doi.org/10.1021/j100638a009) | Foundational binary virial families for strong electrolytes; individual tuples must be read from the original article | Molality-scale Pitzer formulation near 298.15 K; exact parameter-specific alpha and fitted range must be taken from the paper | Mean activity and osmotic-coefficient fits; no value transcribed in this review | Primary publications; ACS copyright, so values require row-level reuse review or redistributable corroboration | Equation lineage accepted; no direct adoption pending exact NeqSim term-by-term mapping. |
| Harvie, Møller and Weare (1984), [natural-water model](https://doi.org/10.1016/0016-7037(84)90098-X) | Na/K/Mg/Ca/H with Cl/SO4/OH/HCO3/CO3/CO2/H2O; binary and mixed interactions | 25 °C, high ionic strength; molality-scale Pitzer model | Fitted isopiestic, EMF, and solubility data; multicomponent comparisons outside subsystems are reported | Primary Elsevier article; numerical-table redistribution not established here | High-priority scientific comparison. No coefficient copied; full family and electrostatic convention must be mapped together. |
| Pitzer (1975), [higher-order electrostatic mixing](https://doi.org/10.1007/BF00646562) | Nonsymmetric same-sign electrostatic terms `Etheta` and its ionic-strength derivative for unequal charge pairs; no fitted short-range coefficient is supplied by this term | Molality-scale Pitzer formulation; the term is zero for equal charges and depends on charge tuple, ionic strength, and `Aphi` | Primary equation source; it establishes model structure rather than a parameter fit | Primary Springer article; no numerical table copied | Equation structure accepted in this increment: the public PHREEQC recurrence and its ion/activity/osmotic placement are mapped directly. No fitted coefficient is copied; short-range tuple coverage remains independently fail-closed. |
| USGS PHRQPITZ (Plummer et al. 1988), [WRIR 88-4153](https://doi.org/10.3133/wri884153), PHREEQC 3.8.6 tag commit [`74cdaf0`](https://github.com/phreeqc-dev/phreeqc3/commit/74cdaf00f90b15b7a5bbc03f405eb2f8129aacf1), and audited current source commit [`b0b3be7`](https://github.com/phreeqc-dev/phreeqc3/commit/b0b3be767158ccc3322d2c816625cf470045e67e) | Na/K/Mg/Ca/H plus major anions and extended Fe/Mn/Sr/Ba/Li/Br; `B0`, `B1`, `B2`, `C0`, `theta`, `psi`, `lambda`, `zeta`, `mu`, `eta`, alpha, and six temperature coefficients | Molality scale; PHREEQC enables nonsymmetric electrostatic mixing by default. The adopted Na+/SO4-- 1-2 binary uses alpha1=2. At 298.15 K, current NaCl is `B0=0.07534`, `B1=0.2769`, `C0=0.00148`; KCl is `0.04808`, `0.2168`, `-0.000788`; K/Na `theta=-0.012`; Cl/K/Na `psi=-0.0015`. | Appelo (2015), [DOI 10.1016/j.apgeochem.2014.11.007](https://doi.org/10.1016/j.apgeochem.2014.11.007), is the database's cited high-temperature lineage. Exact validity, observables, residuals, and uncertainty remain row/source specific. Rumpf and Maurer (1993), [DOI 10.1002/bbpc.19930970116](https://doi.org/10.1002/bbpc.19930970116), fitted CO2 in Na2SO4/ammonium-sulfate brines at 313.15–433.15 K and up to 10 MPa. | USGS software and data are public domain. Audited blobs: [`pitzer.cpp` `1f32a08`](https://github.com/phreeqc-dev/phreeqc3/blob/b0b3be767158ccc3322d2c816625cf470045e67e/src/pitzer.cpp) and [`pitzer.dat` `324f852`](https://github.com/phreeqc-dev/phreeqc3/blob/b0b3be767158ccc3322d2c816625cf470045e67e/database/pitzer.dat). | The complete CO2-Na2SO4 subset listed above is adopted and independently checked. NeqSim legacy NaCl and KCl conflict with current PHREEQC, so those unrelated rows remain legacy-only. The parameter-free `Etheta`, six-term functions, neutral-family placement, and `C0`/`Cphi` mapping are accepted. |
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
| [16] | Duan et al. (1992), [methane in brines](https://doi.org/10.1016/0016-7037(92)90215-5): CH4 solubility in 0–6 molal brines, 0–250 °C, and 0–1600 bar | Supports the neutral-methane interaction lane only. NeqSim lacks the required neutral Pitzer family representation, so these rows fail closed. |

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
CO2-Na2SO4 subset is the sole new fitted dataset: its complete companion families, equation mapping,
range evidence, and independent IPhreeqc/held-out gates are recorded above. Current PHREEQC Na/K/Cl
values still disagree with the NeqSim legacy binaries; they remain rejected until a separately
versioned complete-family adoption and validation is ready.

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
`C0`/`Cphi` equation convention. Parameter-source conflicts outside the qualified CO2-Na2SO4 subset
and reaction-species coverage remain unresolved. The supplied Kaasa scan was inspected during this mapping, but no Kaasa numerical value was copied
or adopted. Its same-sign and ternary tables confirm the required family topology; source lineage,
reuse, validity, and independent numerical agreement remain row-level blockers.

The next parameter dependency is a complete redistributable mixed-oilfield-brine family with
row-level lineage and held-out mixed-brine validation; the active CO2-Na2SO4 subset is not silently
expanded with unrelated Na/K/Cl values.

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
| `beta(0)`, `beta(1)`, `C` | `B0`, `B1`, `C0` use all six coefficients | atomic binary setter for `beta0`, `beta1`, and NeqSim `Cphi` | Function adopted; PHREEQC `C0` maps identically to NeqSim `Cphi`; qualified CO2-Na2SO4 coefficients adopted |
| `beta(2)` | `B2` uses all six coefficients | explicit `beta2` temperature setter and temperature-aware activity/osmotic calls | Function adopted; no coefficient adopted |
| `theta` | `THETA` uses all six coefficients | sparse same-sign-pair function, including both ion and water paths | Function adopted; no coefficient adopted |
| `psi` | `PSI` uses all six coefficients | sparse ternary function, including both ion and water paths | Function adopted; no coefficient adopted |
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
electrolyte EOS models, reaction tables, and all neutral models are unchanged.

This resolves an equation-convention dependency only. It does not qualify a PHREEQC row, reconcile
the conflicting NaCl or KCl values above, establish pressure or temperature validity, or adopt any
coefficient. PHRQPITZ and the exact PHREEQC source are USGS public-domain evidence. The supplied
Kaasa scan independently confirms the printed Cphi family name and six-term algebra, but no thesis
value is reused because its table-redistribution terms and row-level qualification remain unresolved.

The legacy NeqSim database columns `*_25`, `*_T1`, and `*_T2` keep their established
three-coefficient behavior. The six-term layer is empty by default and guarded before map lookup, so
legacy binary systems pay only one predictable boolean branch and non-Pitzer models execute no new
code. Setter calls are explicit and retain dataset identity and mixed-brine coverage diagnostics.
Functions survive serialization; clones copy only the sparse map and share immutable function values.

Synthetic coefficients continue to isolate the public equation mapping at 298.15 and 373.15 K.
Separately, the named PHREEQC CO2-Na2SO4 helper embeds only the qualified rows listed above; it does
not bulk-import `pitzer.dat`, change the reaction database, or select reactions. Its explicit
temperature/molality evidence envelope does not make pressure-dependent VLE, extrapolation, or any
additional species topology validated.

The supplied Kaasa (1998) scan was visually inspected at chapter 4 §4.3.2 and Appendix F.
Its family inventory and coefficient-order convention are recorded above, but no numerical table value
was copied or adopted. The next parameter dependency is a redistributable, independently checked
mixed oilfield-brine family with row-level lineage and held-out mean-activity, osmotic,
water-activity, speciation, and mixed-brine validation.
