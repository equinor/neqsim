---
title: "Flow-Assurance Study Report: one command from a route, basis and cases to a report and pipeline drawings"
description: "How to turn a pipeline or tie-back route, an engineering basis and a set of operating cases into a validated flow-assurance study report (Word/PDF) and a ten-sheet A3 pipeline drawing package with the neqsim flow-assurance-report command: hydrate margin, cooldown, hydraulics, inhibitor balance, threat applicability, mitigation comparison, external-simulator hand-offs and a fail-closed validation report."
---

# Flow-Assurance Study Report

`neqsim flow-assurance-report` produces a reviewable **flow-assurance study package** in one step: a Word report
(and PDF where Microsoft Word or LibreOffice is available) with 16 chapters and appendices, ten A3 pipeline
drawing sheets (SVG, PNG, PDF), one canonical study dataset and a machine-readable validation report. It is the
flow-assurance sibling of [process-study report](PROCESS_STUDY_REPORT.md) and reuses its report, drawing and
validation helpers.

The pipeline lives in `devtools/` (`flow_assurance_report`, `fa_study`, `fa_solver`, `fa_geometry`,
`fa_report`, `fa_diagrams`, `fa_validate`, `fa_handoff`). The skill
[neqsim-flow-assurance-study-report](../../.github/skills/neqsim-flow-assurance-study-report/SKILL.md) holds the
method and the agent [@flow-assurance-study-report](../../.github/agents/flow-assurance-study-report.agent.md)
drives it. The calculation patterns remain in the
[neqsim-flow-assurance](../../.github/skills/neqsim-flow-assurance/SKILL.md) skill. Every command and code block on
this page is executed by
[test_flow_assurance_study_report_documentation.py](../test_flow_assurance_study_report_documentation.py).

## 1. What it does and does not do

| Supported (screening level) | Not supported (reported as open or unsupported, never as safe) |
|-----------------------------|-----------------------------------------------------------------|
| Steady pressure, temperature, holdup, velocity and regime label with `PipeBeggsAndBrills` (SRK) | Startup, restart, ramp-up, depressurisation, sweep-out |
| Backward solve: host pressure and rates given, source pressures calculated | Pigging and slug-receiving requirements |
| Hydrate equilibrium margin with SRK-CPA, inhibited and uninhibited, MEG mass balance | Severe and terrain slugging (Beggs and Brill does not qualify) |
| Lumped cooldown time to the hydrate temperature (not time to plug) | Wax, asphaltene, scale and CO2 corrosion without inputs (open threats) |
| Erosional-velocity ratio against a project criterion | DNV-RP-O501 erosion assessment |
| Topology variants that isolate sources or segments | Bypass, reroute or added segments; more than one junction |

Unsupported events get a hand-off file (`handoff/EVT-*.json`) for an external, qualified simulator; the result
comes back with `--external-results`. Method qualification is recorded in Chapter 6 and Appendix G; no method is
promoted beyond its evidence. The software never assigns the `REVIEWED` readiness label.

## 2. Quick start

The next block writes the input templates. Nothing is calculated: every value that must come from a source reads
`unavailable - TO BE CONFIRMED`, and the command refuses to run on an unfilled template (exit code 2).

<!-- run -->
```powershell
neqsim flow-assurance-report --init study_inputs
```

After filling `route.yaml`, `basis.yaml` and `cases.yaml` (the synthetic example in
`devtools/fa_examples/offshore_tieback/` shows every field; `devtools/fa_examples/single_flowline/` is a second synthetic
example with one source, no junction or PLEM and a route along one axis), run the study. `--strict` returns exit code 1 when a
validation check fails.

<!-- parse -->
```powershell
neqsim flow-assurance-report route.yaml --basis basis.yaml --cases cases.yaml --out report --strict
neqsim flow-assurance-report --demo --out demo
neqsim flow-assurance-report study.json --basis basis.yaml --out report
```

The `--demo` run solves a synthetic two-template tie-back (eight cases, about one minute) and is the reference
for the output layout. Without the `neqsim` command use `<python> -m neqsim_cli flow-assurance-report ...`.

The shared runtime adapter uses `neqsim_dev_setup` to load compiled workspace classes in a source checkout
or the installed NeqSim JAR outside one. It reuses an already running JVM, including the one started by a
SURF study. Java, JPype and an available NeqSim classpath are required; a separate `jneqsim` Python package
is not required.

## 3. Inputs

| File | Content |
|------|---------|
| `route.yaml` | assets with plan coordinates and elevation, segments with measured length, inside diameter, roughness, ambient, burial and layers (steel, coating, insulation) |
| `basis.yaml` | study and document data, fluids, solver settings, acceptance criteria with sources, mitigation options, inspected sources, assumptions, data gaps and an optional human review record |
| `cases.yaml` | cases (design, scenario, mitigation), topology variants, events |
| `standards_register.yaml` | applicable standards with status and what was verified (public metadata only) |

Rates are mass rates unless a standard density or the actual pressure and temperature are given. Inhibitor is
specified as a lean stream or as an aqueous **mass** fraction target; mole fractions are never accepted as weight
percent. The U-value is referenced to the inner pipe area, as the hydraulic solver uses pi x ID x L.

The inhibitor arithmetic is a pure function and is verified by the documentation test:

<!-- run -->
```python
import sys
sys.path.insert(0, "devtools")
import fa_units

lean = fa_units.lean_injection_rate(water_rate=100.0, target_wt=0.30, purity_wt=0.90)
assert abs(fa_units.aqueous_wt_after_injection(100.0, lean, 0.90) - 0.30) < 1e-12
assert abs(lean - 50.0) < 1e-9
# 5 mol MEG per 15 mol water is 53 wt % MEG, not 25 wt %
meg = 5.0 * fa_units.MW["MEG"]
water = 15.0 * fa_units.MW["water"]
assert abs(fa_units.aqueous_wt_fraction(water, meg) - 0.534) < 0.005
```

## 4. Outputs

| Folder | Content |
|--------|---------|
| `report/` | Word report, PDF, `validation_report.json` and `.md`, `report_facts.json` |
| `drawings/` | `svg/` (ten A3 sheets; nine for a single-path route, which has no second-path profile, DWG-04), `png/`, drawing PDF, `layout_spec.json` |
| `dataset/` | `flow_assurance_study.json` (schema `flow_assurance_study.v1`) |
| `handoff/` | external-simulator event packages |
| `figures/`, `cache/` | report figures; per-case solver cache keyed on the inputs and the NeqSim version |

Drawing sheets: network schematic, route plan, longitudinal profiles per path, riser and PLEM arrangement, pipe and
thermal cross-sections, velocity and holdup, hydrate margin, operating-limit summary and an event sheet.

## 5. Validation and readiness

About 30 checks run on the inputs, geometry, topology, solver, balances, boundary pressure, analytic benchmarks,
mesh sensitivity, threats, events, standards, drawings (layout, data agreement with the dataset) and the document
(every data token resolves with its unit, mandatory sections, open items visible). A failed check makes the package
a `NOT_READY` diagnostic. A package from synthetic or schematic inputs, or with open threats or data gaps, is at
most `SCREENING`.

## 6. Limits to keep in mind

- Beggs and Brill carries no general experimental qualification in this toolkit; the hydrate benchmarks use
  secondary-source points only.
- Use at least 10 increments per segment: in development a 6-increment mesh changed a riser outlet temperature by
  7.5 K in a low-flow case. The mesh gate checks the study mesh against a finer one.
- Inhibitor losses to the gas and condensate phases are not modelled.
- Standard texts are not read; standards are listed with their verified status and never as "complied with".
- The `TwoFluidPipeResponse` adapter `fa_handoff.two_fluid_profile` is a Python function; the command line does not
  ingest it.
