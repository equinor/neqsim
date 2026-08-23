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
usable tuple identity for same-sign `theta` or ternary `psi`, and it has no `lambda`, `zeta`, `mu`,
or `eta` representation. Mixed-ion calculations therefore require explicit parameter definitions.

`PhasePitzer.getPitzerParameterCoverage()` returns active primary-salt cations and anions plus every
missing binary, `theta`, and `psi` key. For a mixed primary-salt topology, the automatic gate fails
before activity or water-osmotic evaluation when that coverage is incomplete. Established
single-cation/single-anion calculations retain their binary behavior; callers can explicitly audit
their binary row with `requireCompletePitzerParameterCoverage()`. An explicit zero set through
`setBinaryParameters`, `setTheta`, or `setPsi` counts as a definition; an absent value does not.
Acid-base species created as reaction-solver trial variables (`H3O+`, `OH-`, `HCO3-`, and `CO3--`)
are outside this first gate: their transient iteration amounts are not a stable input topology, and
they require a later model-specific reaction-dataset audit. The diagnostic is cached by active
primary-salt topology and parameter revision, and the automatic gate runs once after each standard
level-zero state initialization. Call `init(0)` after changing composition, as required by the
general NeqSim state contract. This work is confined to
`PhasePitzer`; neutral PR, SRK, CPA, Electrolyte-CPA, and Fürst electrolyte-EOS paths do no new work.

## Source-comparison matrix

No numerical coefficient was adopted in this review. “Not inspected” means that no value may be
inferred from the source.

| Source / version | Species and parameter families | Conditions, equation, scale, and alpha | Evidence, range, uncertainty | Lineage and reuse status | Mapping decision / conflict |
|---|---|---|---|---|---|
| NeqSim legacy CSV, audited 2026-08-23 | 30 cation-anion rows; `beta0`, `beta1`, `beta2`, `Cphi`; columns named `theta` and `psi_common_ion` contain only zero and no tuple identity | 298.15 K reference; CSV `Tmin`/`Tmax`; NeqSim three-term T expression; molality activity path; hard-coded alpha 2.0 for pairs containing a univalent ion and 1.4/12.0 for 2-2 | References vary from publications to “Estimated”; per-row residuals and uncertainty absent | Repository data under NeqSim's license; legacy lineage is not a coherent qualified release | Preserve as dataset v1. Binary 298.15 K NaCl is numerically consistent with the PHREEQC example, but temperature and electrostatic mappings are not equivalent. Missing mixed interactions now fail closed. |
| Pitzer (1973), [general equations](https://doi.org/10.1021/j100621a026), and Pitzer–Mayorga (1973), [strong electrolytes](https://doi.org/10.1021/j100638a009) | Foundational binary virial families for strong electrolytes; individual tuples must be read from the original article | Molality-scale Pitzer formulation near 298.15 K; exact parameter-specific alpha and fitted range must be taken from the paper | Mean activity and osmotic-coefficient fits; no value transcribed in this review | Primary publications; ACS copyright, so values require row-level reuse review or redistributable corroboration | Equation lineage accepted; no direct adoption pending exact NeqSim term-by-term mapping. |
| Harvie, Møller and Weare (1984), [natural-water model](https://doi.org/10.1016/0016-7037(84)90098-X) | Na/K/Mg/Ca/H with Cl/SO4/OH/HCO3/CO3/CO2/H2O; binary and mixed interactions | 25 °C, high ionic strength; molality-scale Pitzer model | Fitted isopiestic, EMF, and solubility data; multicomponent comparisons outside subsystems are reported | Primary Elsevier article; numerical-table redistribution not established here | High-priority scientific comparison. No coefficient copied; full family and electrostatic convention must be mapped together. |
| Pitzer (1975), [higher-order electrostatic mixing](https://doi.org/10.1007/BF00646562) | Nonsymmetric same-sign electrostatic terms `Etheta` and its ionic-strength derivative for unequal charge pairs; no fitted short-range coefficient is supplied by this term | Molality-scale Pitzer formulation; the term is zero for equal charges and depends on charge tuple, ionic strength, and `Aphi` | Primary equation source; it establishes model structure rather than a parameter fit | Primary Springer article; no numerical table copied | Required before unequal-charge `theta` values can be mapped. Current NeqSim omits this term, so Na/Ca and Na/Ba mixed sets remain blocked. |
| USGS PHRQPITZ (Plummer et al. 1988), [WRIR 88-4153](https://doi.org/10.3133/wri884153), PHREEQC 3.8.6 tag commit [`74cdaf0`](https://github.com/phreeqc-dev/phreeqc3/commit/74cdaf00f90b15b7a5bbc03f405eb2f8129aacf1), and audited current source commit [`b0b3be7`](https://github.com/phreeqc-dev/phreeqc3/commit/b0b3be767158ccc3322d2c816625cf470045e67e) | Na/K/Mg/Ca/H plus major anions and extended Fe/Mn/Sr/Ba/Li/Br; `B0`, `B1`, `B2`, `C0`, `theta`, `psi`, `lambda`, `zeta`, `mu`, `eta`, alpha, and six temperature coefficients | Molality scale; PHREEQC enables nonsymmetric electrostatic mixing by default. For 1:1 salts alpha1 is 2.0. At 298.15 K, current NaCl is `B0=0.07534`, `B1=0.2769`, `C0=0.00148`; KCl is `0.04808`, `0.2168`, `-0.000788`; K/Na `theta=-0.012`; Cl/K/Na `psi=-0.0015`. | USGS describes a 25 degrees C core with largely untested extensions; exact validity, observables, residuals, and uncertainty remain row/source specific | USGS software and data are public domain. Audited blobs: [`pitzer.cpp` `1f32a08`](https://github.com/phreeqc-dev/phreeqc3/blob/b0b3be767158ccc3322d2c816625cf470045e67e/src/pitzer.cpp) and [`pitzer.dat` `324f852`](https://github.com/phreeqc-dev/phreeqc3/blob/b0b3be767158ccc3322d2c816625cf470045e67e/database/pitzer.dat). | Preferred redistributable provenance index. NeqSim legacy NaCl is `0.0765/0.2664/0.00127` and KCl is `0.04835/0.2122/-0.00084`, so the sources conflict. `C0`/Cphi, `Etheta`, and six-term temperature mappings are not yet accepted; no value was adopted. |
| Kaasa (1998), *Prediction of pH, mineral precipitation and multiphase equilibria during oil recovery*, [National Library item](https://www.nb.no/items/d1d68b489b8ee6704786a011fd2e7283) | Candidate oil-recovery-brine Pitzer tables; exact tuples and families not inspected | Unknown until lawful page-level inspection | No page, value, units, fitted data, uncertainty, or cited primary source was readable in this review | Scan access/reuse terms unresolved; no table may be copied | Retained as high-priority provenance index. Zero Kaasa parameters were found or adopted in this review. |
| THEREDA release 2026-01, [official database](https://www.thereda.de/) | Quality-controlled high-salinity Pitzer datasets, including variable-temperature sets | Official site reports an oceanic set from 0 to 110 °C; formulation and scale are dataset specific | Official site reports 425 tests and more than 3200 results; row-level uncertainty remains dataset specific | Current release and licensing/reuse terms must be confirmed before storage | Candidate validation and provenance source only; no value adopted while reuse and exact convention mapping remain unresolved. |

## Adoption gate

A parameter-set PR must preserve a versioned dataset identity and per-row provenance. It must map
all applicable binary, same-sign, ternary, neutral-ion, alpha, electrostatic, and temperature terms;
document standard state, species charge, units, pressure assumptions, fitted observables, validity,
uncertainty, preprocessing, and license; and validate against data outside the fit. Required checks
include mean activity and osmotic coefficients or water activity, mixed-brine speciation and mineral
saturation, electroneutrality and material balance, derivative continuity, boundary behavior,
determinism, and an independently configured exact-version implementation such as PHREEQC.

The 2026-08-23 compatibility repair adopted no coefficient. The Kaasa scan remained inaccessible:
zero Kaasa parameters, pages, or tables were read, transcribed, inferred, or OCRed. The current PHREEQC
Na/K/Cl values above disagree with the NeqSim legacy binaries and with the PHREEQC 3.8.6 ternary example;
they were rejected for adoption until the full model mapping and independent validation are complete.

The next dependency is a reviewed implementation of PHREEQC nonsymmetric electrostatic mixing, followed
by the six-term temperature function and a complete redistributable Na/K/Cl binary–theta–psi set.
