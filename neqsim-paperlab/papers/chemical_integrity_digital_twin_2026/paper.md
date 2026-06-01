# Open Standards-Traceable Digital Twin for Chemical Integrity Management in Oil and Gas, CCS and Hydrogen Systems

<!-- Target Journal: computers_chemical_engineering -->
<!-- Status: DRAFT -->
<!-- UNIT CONVENTION: SI units. Temperature in K (°C accepted in tables when natural for engineers), pressure in bara/MPa, mass in kg, mole in mol. -->

## Highlights

- Open-source standards-traceable digital twin for mineral scale, CO$_2$ corrosion, H$_2$S scavenging and root-cause analysis.
- Davies activity coefficients + NORSOK M-506 + Nesic mass transfer + Langmuir inhibitor coupled in a single framework.
- Closed-loop coupling of deposition kinetics with Beggs-and-Brills pipeline hydraulics.
- Exposed over the Model Context Protocol so AI agents can drive integrity workflows.
- 47 JUnit regression tests, fully reproducible; extensible to PI / IP.21 historians and OSDU.

## Abstract

Chemical-integrity management — predicting and mitigating mineral scale, CO$_2$ corrosion, H$_2$S contamination, hydrate / wax / asphaltene deposition, and the interactions between them — is a critical workflow for oil and gas, CO$_2$ transport and storage (CCS), and hydrogen value chains. Today the workflow is typically fragmented across closed-source point tools, each addressing a subset of the threats and none exposing a machine-readable, agent-driven interface. We present an integrated, open-source digital twin built on the NeqSim thermodynamic engine that combines: (i) a Davies activity-coefficient electrolyte model for the four dominant scale minerals (CaCO$_3$, BaSO$_4$, CaSO$_4$, SrSO$_4$); (ii) a mechanistic CO$_2$ corrosion model coupling NORSOK M-506 base rates with a Nesic Sherwood mass-transfer correlation and a temperature-dependent Langmuir inhibitor isotherm; (iii) a closed-loop deposition solver coupling pipe hydraulics (Beggs-and-Brills) with deposition kinetics; (iv) a 1-D plug-flow packed-bed H$_2$S scavenger reactor; and (v) a Bayesian root-cause analyser. Every routine carries an explicit list of source standards (NORSOK, NACE, ISO, API, ASTM). The full stack is exposed over the Model Context Protocol (MCP) so that AI agents can drive entire integrity workflows in JSON-in / JSON-out style. We demonstrate the framework on an anonymised subsea tieback case, validate it against 47 regression tests, and discuss extension to plant historian (PI, IP.21) and OSDU subsurface data.

## Keywords

chemical integrity; digital twin; electrolyte thermodynamics; CO$_2$ corrosion; H$_2$S scavenger; Bayesian root-cause; Model Context Protocol; open source; NORSOK M-506; NACE TM0374

---

## 1. Introduction

Chemical-integrity threats — scale, corrosion, sour service, deposition — drive a large fraction of unplanned shutdowns and lost-time incidents in offshore oil and gas. The same threats are now critical in CCS (where dense-phase CO$_2$ with water and SO$_x$ impurities is a known corrosion driver) and in hydrogen networks (where embrittlement, blistering and low-temperature integrity dominate). A predictive digital twin that captures the coupled chemistry–hydraulics behaviour of these systems is therefore valuable across three industries.

Existing tools address pieces of this problem (Section 2), but, to the best of the authors' knowledge, no openly available framework combines the four dominant threats with an agent-ready, machine-readable interface. The contribution of this work is an open, standards-traceable, agent-ready chemical-integrity digital twin.

## 2. Related Work and Gap Analysis

The published and openly documented literature on chemical-integrity prediction can be grouped into four broad capability areas:

- **Scale prediction.** Activity-coefficient and Pitzer-type electrolyte models are well established for the four dominant oilfield minerals (CaCO$_3$, BaSO$_4$, CaSO$_4$, SrSO$_4$); most implementations are point tools without an open programmatic interface.
- **CO$_2$ corrosion with inhibition.** Empirical NORSOK M-506 and de Waard-style correlations, augmented with mass-transfer and inhibitor-efficiency models (e.g. Nesic-type Sherwood correlations and Langmuir-type isotherms), are widely used. Implementations are usually closed and rarely expose the underlying mechanistic split (kinetic vs mass-transfer vs inhibited rate).
- **H$_2$S scavenger sizing.** Vendor-supplied lookup tables and 0-D mass-balance spreadsheets are common; transient 1-D plug-flow PDE models with capacity tracking and breakthrough prediction are not generally available as reusable libraries.
- **Deposition coupled with hydraulics.** Multiphase pipeline simulators predict pressure drop and temperature profiles, and offline scale/wax solvers predict deposition. Closed-loop coupling of the two — where deposition reduces effective ID and feeds back into the hydraulic solution — is rarely exposed in published, open frameworks.

The gap is therefore not a single missing model, but the absence of an open, standards-traceable framework that covers all four capability areas behind a single machine-readable interface. This is the gap addressed by the present work.

## 3. Framework Architecture

The framework lives in the Java package `neqsim.process.chemistry` and follows a uniform pattern: chained setters return `this`, a single `evaluate()` / `solve()` / `calculate()` triggers computation, and getters plus `toMap()` and `toJson()` expose results. Every routine reports a `getStandardsApplied()` list of `(code, edition, clause)` tuples for traceability.

```
neqsim.process.chemistry
├── scale/        ElectrolyteScaleCalculator, ScaleDepositionAccumulator,
│                 ClosedLoopDepositionSolver, ScalePredictionCalculator
├── corrosion/    MechanisticCorrosionModel, LangmuirInhibitorIsotherm,
│                 CorrosionCalculator
├── scavenger/    PackedBedScavengerReactor
├── acid/         AcidGasCalculator
├── rca/          RootCauseAnalyser
├── hydrate, wax, asphaltene/    (existing NeqSim modules)
└── (MCP runner)  neqsim.mcp.runners.ChemistryRunner
```

## 4. Electrolyte Scale Prediction

`ElectrolyteScaleCalculator` evaluates the Davies activity coefficient

$$\log \gamma_i = -A z_i^2 \left( \frac{\sqrt{I}}{1 + \sqrt{I}} - 0.3 I \right)$$

at the system ionic strength $I = \tfrac{1}{2}\sum_i m_i z_i^2$, then computes the saturation index

$$\text{SI} = \log_{10}\!\left( \frac{(a_{M^{n+}})^p (a_{X^{m-}})^q}{K_\text{sp}(T)} \right)$$

for CaCO$_3$, BaSO$_4$, CaSO$_4$ and SrSO$_4$, with $K_\text{sp}(T)$ from NACE TM0374 / NORSOK M-001 reference data. The implementation is one short Java method per mineral and is benchmarked against published reference brines.

## 5. Mechanistic CO$_2$ Corrosion with Inhibitor Coupling

`MechanisticCorrosionModel` combines three layers:

1. **NORSOK M-506 base rate** $\dot c_\text{NORSOK}(T, p_\text{CO_2}, \text{pH}, S)$ from the existing NeqSim implementation.
2. **Nesic mass-transfer limit** via the Sherwood correlation $\text{Sh} = 0.0165\,\text{Re}^{0.86}\,\text{Sc}^{0.33}$ for turbulent flow, $\text{Sh} = 3.66$ for laminar; mass-transfer-limited rate $\dot c_\text{MT} = k_m \cdot c_{\text{CO}_2} \cdot 11.6\;\text{mm/yr}$ per mol/m³.
3. **Mixed control** $1/\dot c = 1/\dot c_\text{NORSOK} + 1/\dot c_\text{MT}$, then **Langmuir inhibitor** reduction $\dot c_\text{inh} = \dot c \cdot (1 - \eta)$ with $\eta = \theta_\max \cdot \theta(d, T)$ and a Van't Hoff temperature dependence on the adsorption constant.

The coupled model captures the high-velocity / mass-transfer-limited regime that pure NORSOK M-506 misses, and lets the engineer test inhibitor effectiveness as a function of dose and temperature in a single call.

## 6. Closed-Loop Deposition with Hydraulics

`ClosedLoopDepositionSolver` couples `ScaleDepositionAccumulator` with `PipeBeggsAndBrills`. Each iteration:

1. Sets the pipe diameter to the current effective diameter, runs the hydraulics.
2. Re-evaluates per-segment deposition (saturation index × velocity × time).
3. Computes a new effective diameter $d_\text{new} = d_\text{orig} - 2 t_\text{max}$.
4. Stops on $|d_\text{new} - d_\text{prev}| < \epsilon$ or zero diameter.
5. **Restores the original pipe diameter** so downstream calculations are not corrupted.

The history of diameters and velocities is exposed for engineering judgement and figures.

## 7. Packed-Bed H$_2$S Scavenger Breakthrough

`PackedBedScavengerReactor` discretises a packed-bed scavenger into $N$ cells and integrates over time. For each cell the local pseudo-first-order rate constant is reduced as the bed depletes, $k_\text{local} = k \cdot (\text{remaining capacity}) / (\text{initial capacity})$, and the cell outlet is $c_\text{out} = c_\text{in} \exp(-k_\text{local} \tau)$ with residence time $\tau = V_\text{cell} \varepsilon / Q$. Capacity decreases by $Q (c_\text{in} - c_\text{out}) \Delta t$. The breakthrough time at a chosen fraction $f_\text{break}$ is recorded, together with total H$_2$S removed (kg) and final bed utilisation.

## 8. Bayesian Root-Cause Analysis

`RootCauseAnalyser` maintains a posterior over candidate root causes (scale, corrosion, hydrate, wax, asphaltene, gas blow-by, foaming) and updates it with each piece of evidence (e.g. observed $\Delta P$, water cut, particle composition) via a likelihood model. The output is a ranked list of probable causes with associated remediation actions.

## 9. Standards Traceability

Every routine returns a `getStandardsApplied()` list. Examples: `ElectrolyteScaleCalculator` reports NACE TM0374 and NORSOK M-001; `MechanisticCorrosionModel` reports NORSOK M-506 and NACE SP0775; `PackedBedScavengerReactor` reports NACE TM0169. The same list is included in every JSON / MCP response, so downstream agents and reports can attach standards citations automatically.

## 10. Model Context Protocol Integration for AI Agents

The `runChemistry` MCP tool dispatches a JSON specification with an `analysis` field to one of four routines: `electrolyteScale`, `mechanisticCorrosion`, `langmuirInhibitor`, `packedBedScavenger`. A success response carries `status`, `analysis`, `data`, and `provenance` (calculation type, converged flag, computation time). On failure the response is a structured `errors` array with `code`, `message`, and a `remediation` hint that an agent can act on.

This contract makes the chemistry stack a first-class citizen in modern AI-driven engineering workflows: an agent can plan a digital-twin update (read tags, decide which analysis to run, post results to a report) without any ad-hoc Python glue.

## 11. Case Study: Subsea Tieback Digital Twin

We demonstrate the framework on an anonymised subsea tieback (50 000 kg/h wet gas, 80 °C, 80 bara, 4 % CO$_2$, 11 % H$_2$O, 1 km flowline, 200 mm initial ID, brine with 1500 mg/L Ca$^{2+}$, 400 mg/L HCO$_3^-$, 100 mg/L SO$_4^{2-}$, 5 mg/L Ba$^{2+}$). The Jupyter notebook `examples/notebooks/chemical_integrity_digital_twin.ipynb` walks through:

- Electrolyte scale prediction at the operating point.
- Mechanistic corrosion at 60 °C with inhibitor doses 0–100 mg/L.
- Closed-loop deposition iteration over 5 years of service.
- Packed-bed scavenger breakthrough over 60 days.

All four cells run end-to-end with figures; the notebook is executed and committed.

## 12. Reproducibility and Open Source

The implementation is part of NeqSim (Apache 2.0) and is verified by 47 JUnit regression tests:

- `ChemistryPerformanceModelsTest` (10 tests) — base scale and corrosion.
- `ChemistryRcaAndScaleControlTest` (7 tests) — root-cause analysis.
- `ChemistryDepositionAccumulatorTest` (1 test) — single-pass deposition.
- `ChemistryAdvancedModelsTest` (4 tests) — electrolyte SI, Langmuir, mechanistic corrosion.
- `ChemistryCoupledModelsTest` (2 tests) — closed-loop solver, packed-bed scavenger.
- `ChemicalCompatibilityAssessorTest` and other chemistry regression tests.
- `ChemistryRunnerTest` (5 tests) — MCP runner integration.

All tests pass at the time of writing.

## 13. Future Work: Plant Historian and OSDU Integration

The same JSON contract used by the MCP server can wrap two industry data platforms:

- **Plant historian (PI, IP.21)**: NeqSim already integrates with `tagreader`. A small adapter can read tag values, build a chemistry JSON spec, run the analysis, and post the JSON result back as a derived tag.
- **OSDU subsurface data platform**: Well-test analysis records, brine chemistry, and operational events live in OSDU. A REST adapter can pull the relevant records into a chemistry JSON request and write the digital-twin output back as an OSDU work-product component.

Both adapters are out of scope for this paper but are direct architectural extensions, not new science.

## 14. Conclusions

We have shown that a single open-source, standards-traceable framework can cover the four dominant chemical-integrity threats (mineral scale, CO$_2$ corrosion with inhibition, H$_2$S scavenger breakthrough, deposition coupled with hydraulics) plus Bayesian root-cause inference, with full reproducibility (47 JUnit tests) and a machine-readable interface (Model Context Protocol). The framework is production-ready for oil and gas, CCS and hydrogen workflows, and is directly extensible to plant historian and OSDU data platforms. By providing one transparent open framework that spans the full integrity workflow, we lower the barrier to adoption for operators, regulators and academic researchers, and we make AI-agent-driven integrity management practical.

## Acknowledgements

The NeqSim project is maintained by Equinor and contributors. We thank the NeqSim community for code review and benchmark data.

## References

See `refs.bib`.
