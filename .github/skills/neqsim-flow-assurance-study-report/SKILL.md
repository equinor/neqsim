---
name: neqsim-flow-assurance-study-report
version: "1.0.0"
description: "Produces a validated flow-assurance study report (Word + PDF, 16 chapters with appendices A-G) and a ten-sheet A3 pipeline drawing package (network, route plan, longitudinal P/T profiles, riser/PLEM arrangement, cross-sections, velocity/holdup, hydrate margin, limits, events) from a route, an engineering basis and a case set, with a threat-applicability matrix, method qualification register, mitigation comparison, external-simulator hand-offs and a fail-closed validation report. USE WHEN: a task asks for a flow-assurance study report, pipeline or tie-back drawings, hydrate/cooldown/MEG screening documentation, turndown or late-life case comparison, or a reviewable flow-assurance deliverable from NeqSim. Distinct from neqsim-flow-assurance (calculation patterns)."
last_verified: "2026-10-25"
requires:
  python_packages: [python-docx, matplotlib, pymupdf, pyyaml, jpype1]
---

# Flow-assurance study report

Turns a **route + basis + cases** into a reviewable flow-assurance report and drawing package with one
command. Sibling of `process-study-report`: the same architecture (one canonical dataset, data-bound
narrative tokens, drawings as data, fail-closed validation) with flow-assurance content. Calculation
patterns for hydrate, wax, corrosion and so on stay in `neqsim-flow-assurance`; this skill documents,
checks and draws a study. Nothing is invented: a value nobody sourced is `unavailable - TO BE CONFIRMED`.

## Quick start

```powershell
neqsim flow-assurance-report --init study_inputs                      # scaffold basis, route, cases, standards register
neqsim flow-assurance-report route.yaml --basis basis.yaml --cases cases.yaml --out report --strict
neqsim flow-assurance-report --demo --out demo                        # synthetic two-template tie-back (about 1 min)
neqsim flow-assurance-report study.json --basis basis.yaml            # rebuild from a canonical dataset (no solve)
```

Without the `neqsim` command: `<python> -m neqsim_cli flow-assurance-report ...` or
`<python> devtools/flow_assurance_report.py ...`. Options: `--no-word`, `--no-mesh`, `--strict`
(exit 1 on a validation FAIL), `--task DIR` (merge a summary block into `results.json`),
`--external-results DIR` (attach results from an external simulator), `--standards FILE`.
Exit codes: 0 ok, 1 strict validation failure, 2 input or usage error (nothing is written).

## Inputs

| File | Holds | Rules |
|------|-------|-------|
| `route.yaml` | assets (id, kind, x/y/elevation), segments (length, ID, roughness, ambient, burial, layers), `route_status` | measured length; unique IDs; every number sourced; dz and the 3D chord are checked; one junction at most |
| `basis.yaml` | study and document metadata, fluids, solver, acceptance criteria, mitigation options, sources, assumptions, data gaps, optional `review` record | criteria carry quantity, operator, value, unit, source and `applies_to` |
| `cases.yaml` | cases (role design/scenario/mitigation), topology variants, events | mass rates by default; source pressures are CALCULATED, never inputs; MEG as lean stream or aqueous target wt |
| `standards_register.yaml` | standard, status, assessment status | public metadata only; text not read means not assessed |

An unfilled template (`unavailable - TO BE CONFIRMED` where a number is required) is rejected with
exit 2. Templates: `devtools/fa_templates/`; synthetic example: `devtools/fa_examples/offshore_tieback/`.

## What is calculated and what is not

| Analysis | Method | Status |
|----------|--------|--------|
| Steady pressure, temperature, holdup, velocity, regime label | `PipeBeggsAndBrills` chained per segment, SRK, backward solve for source pressures | implemented; analytic checks only; **no general experimental qualification** |
| Hydrate equilibrium margin | SRK-CPA `hydrateFormationTemperature` on a pressure grid (ln p interpolation), inhibited and uninhibited | implemented; only secondary-source benchmarks; no field or laboratory validation |
| Inhibitor balance | lean-MEG mass balance, aqueous mass fraction (never mole fraction) | implemented; losses to gas and condensate NOT modelled |
| Cooldown to hydrate temperature | lumped exponential (fluid plus steel) | screening; not time to plug; insulation mass excluded |
| Erosional velocity ratio | project C-factor criterion | not a DNV-RP-O501 assessment |
| Wax, asphaltene, scale, CO2 corrosion | threat matrix entry, open unless inputs supplied | open with reason (never "safe") |
| Startup/restart, pigging, severe and terrain slugging, transients | **not supported**; external-event JSON hand-off written | unsupported - open |

Open or unsupported threats and unmet criteria stay visible in the executive summary, the action
register and the drawings. The only transient path is `fa_handoff.two_fluid_profile` (Python adapter for
a `TwoFluidPipeResponse`); the CLI does not ingest it - run the event in an external simulator and give
the result back through `--external-results`.

## Outputs (`--out`)

```
report/   FA-...docx, .pdf (Word or LibreOffice; WARN FA-DOC-PDF when neither exists), validation_report.json/.md, report_facts.json
drawings/ svg/ (10 A3 sheets), png/, <doc>_drawings.pdf, layout_spec.json
dataset/  flow_assurance_study.json (schema flow_assurance_study.v1, hash in every title block)
handoff/  external-event JSON per open transient event
figures/  report figures      cache/  per-case solver cache (keyed on inputs and NeqSim version)
```

Drawing sheets: 01 network schematic, 02 route plan, 03/04 longitudinal profiles per path, 05 riser and
PLEM arrangement, 06 pipe and thermal cross-sections, 07 velocity/erosional ratio/holdup, 08 hydrate
margin and critical sections, 09 operating-limit and threat summary, 10 transient and event sheet.
Every plotted series and annotation carries a data reference; `FA-DRAW-DATA` re-reads the dataset and
fails on disagreement.

## Validation gates (`validation_report.md`)

Inputs, IDs, geometry, topology, criteria, fluid, solver convergence, finite values, mass balance,
boundary pressure, analytic benchmarks, validation status, mesh sensitivity (>= 10 increments per
segment; a 6-increment mesh changed a riser outlet temperature by 7.5 K in development), hydrate grid,
extrema, threats, events, standards verification, drawing QA/data/alignment/files/front-page, and
document checks (every `[[token]]` resolves with its unit, facts match the dataset, mandatory sections
present, open items shown, no unreplaced placeholder, figures, PDF). A FAIL makes the package a
`NOT_READY` diagnostic. WARN is acceptable only when explained in the report.

Readiness is `NOT_READY` / `SCREENING` / `REVIEWED`. `REVIEWED` needs a human review record in
`basis.yaml`, non-synthetic data and no open threat or data gap; **the software never assigns it.**

## Agent procedure

1. Read the task folder, then the route, basis and cases. Ask in ONE message only for what is missing:
   objective, maturity, cases and rates, criteria and their sources, fluid/PVT, insulation and burial data.
2. `--init`, fill from sources, record every inspected source and gap. Never fill from memory.
3. Run `--strict`; open the PNG of every sheet and fix overlaps, clipped text and wrong direction arrows.
4. Interpret each WARN. Do not suppress a gate to get a pass; fix the input or state the limit.
5. Write narrative only from `report_facts.json` and tokens; verify any standard before citing it
   (`neqsim-standards-lookup`); never copy standard text.
6. Hand off: open threats and unsupported transients to the community `flow-assurance-study-agent` /
   `olga-simulation-agent` (external), fluid issues to `@thermo-fluid` / `@pvt-simulation`, review to `@review`.

## Pitfalls

- **Mass vs mole**: MEG "25 wt %" is a mass fraction in water + MEG; 5 mol MEG per 15 mol water is 53 wt %.
  Use `fa_units.aqueous_inhibitor_moles`.
- **Backward solve**: specify the host pressure and rates; source pressure is a result. A source
  pressure above the available limit is a failed criterion, not an input to change.
- **U-value basis**: `SPECIFIED_U` in `PipeBeggsAndBrills` refers to the INNER area (pi x ID x L); the
  toolkit converts layered build-ups accordingly. Do not paste an outer-area U-value.
- **Hydrate fluid**: use light components plus water; C7+ pseudo-components can stall the hydrate solver.
- **Rates**: standard-volume rates need the standard conditions and a standard density; actual-volume rates need p and T.
- **Extrapolation**: inhibitor concentrations outside the verified range are not validated.
- **Topology variants** support `isolate_sources` and `isolate_segments` only; bypass, reroute or added
  segments raise an error (extension point: `fa_geometry.apply_variant`).
- **Branching**: one junction (common trunk) per study; nested junctions are rejected.
- Tokens need a format slot: `[[crit:C-HYD-01:BASE:value|.1f]]`, `[[seg:FL-A-01:length_m|.0f|unit=km]]`.

## References

- [references/study_contract.md](references/study_contract.md) - dataset schema, token kinds, readiness rules
- [references/standards_register_notes.md](references/standards_register_notes.md) - what was verified and how
- Doc page: `docs/development/FLOW_ASSURANCE_STUDY_REPORT.md` (snippets are test-verified)
- Related: `neqsim-flow-assurance`, `process-study-report`, `neqsim-professional-reporting`,
  `neqsim-standards-lookup`, `neqsim-pipeline-survey-processing`, `neqsim-water-hammer`
