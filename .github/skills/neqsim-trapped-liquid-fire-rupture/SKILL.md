---
name: neqsim-trapped-liquid-fire-rupture
version: "1.0.0"
description: "Fire rupture study workflow for blocked-in liquid-filled pipe segments and blowdown pipe fire heat-up / time-to-rupture strain-rate screening. USE WHEN: a task asks for trapped liquid, blocked-in liquid, thermal expansion rupture, fire exposure without relief, PFP demand, flange/pipe rupture screening, supplied blowdown pressure profile, pipe wall heat-up, strain-rate rupture time, or generating a Word/HTML safety study from P&IDs, line lists, piping specifications, material certificates, and fire documents. Anchors on neqsim.process.safety.rupture plus trapped inventory, document retrieval, pressure-profile handoff, and source-term handoff."
last_verified: "2026-06-23"
requires:
  java_packages:
    - neqsim.process.safety.rupture
    - neqsim.process.safety.inventory
    - neqsim.process.safety.release
    - neqsim.process.safety.barrier
---

# Fire Rupture Study

This skill coordinates two related fire-rupture workflows:

- blocked-in liquid-filled segments exposed to fire, where thermal expansion can
  overpressure the pipe/flange system; and
- blowdown pipe segments exposed to fire, where a supplied pressure profile is
  combined with wall heat-up, temperature-dependent material data, and a
  Sellars-Tegart strain-rate model to estimate time to rupture.

For both workflows, gather evidence, preserve assumptions and gaps, run the
appropriate NeqSim safety.rupture calculation, hand off any rupture source term,
and produce a professional report output.

For source-document-driven blowdown pipe-fire studies, use the governed handoff
layer instead of calling the low-level solver directly: build
`SafetyEvidenceReference` entries, assemble a `PipeFireRuptureDataSource`, run
`PipeFireRuptureStudyRunner`, and report the returned
`PipeFireRuptureStudyHandoff`. This preserves calculation readiness, standards
readiness, deterministic uncertainty cases, and the post-rupture source-term
handoff in one JSON-safe package.

When the blowdown pressure profile has not yet been generated, first use the
dynamic flare/blowdown layer from `neqsim-depressurization-mdmt`: assemble
`LineEquipmentListEvidence` and `DynamicBlowdownFlareStudyDataSource`, run
`DynamicBlowdownFlareStudyRunner`, and use the returned source pressure profiles
and `dynamic_blowdown_flare_load_handoff.v1` as the governed basis for the
pipe-fire rupture profile and flare/consequence handoff.

Use the workflow generically. Do not encode operator-specific criteria in public
examples. If a private task has project-specific acceptance criteria, keep them
inside the task folder and cite the private basis only in that task deliverable.

## When to Use

- A Word report, technical note, or user request asks for a similar study on
  trapped liquid, blocked-in liquid, or no pressure relief on liquid-filled piping.
- A fire case can heat a liquid-filled segment isolated by closed valves or PBB.
- Thermal expansion may raise pressure above pipe, flange, gasket, or relief limits.
- The study needs PFP endurance, rupture time, source-term handoff, or an evidence
  matrix showing missing project data.
- A blowdown/depressurization pressure profile already exists and must be used
  to screen pipe wall heat-up, accumulated strain, rupture time, and release rate.
- P&ID, line-list, and piping-specification data must be assembled into pipe cases and reviewed by
  an engineer before calculation.

## Skills to Load Together

- Document-retrieval tools for P&IDs, line lists, piping specs, material
  certificates, fire-zone/PFP documents, relief studies, and design basis documents.
- `neqsim-technical-document-reading` for structured extraction from Word, PDF,
  Excel, P&ID images, line lists, and material certificates.
- `neqsim-process-safety` for barrier/PFP demand and risk register context.
- `neqsim-relief-flare-network` when a thermal relief path or PSV/flare load is
  part of the mitigation.
- `neqsim-consequence-analysis` when the rupture source term must be carried into
  dispersion, fire, explosion, or QRA calculations.
- `neqsim-professional-reporting` for the final evidence-based report.

## Evidence Retrieval Checklist

Before running the calculation, search the task references folder and any
configured document backend for:

| Evidence | Purpose in calculation |
|----------|------------------------|
| P&ID / isometric | Isolation boundary, valves, vents, drains, relief paths, line numbers |
| Line list / route table | NPS, internal diameter, wall thickness, length, design pressure/temperature |
| Piping specification | Material class, corrosion allowance, flange class, gasket/bolt family |
| Material certificate or material class sheet | SMYS, SMTS, grade, temperature limits, toughness notes |
| Flange/bolt/gasket data | Pressure-temperature rating and leakage/rupture limitations |
| Fire-zone / fire-study document | Exposed area, heat flux, pool/jet-fire basis, fire duration |
| PFP requirement or inspection record | Required endurance and actual protection condition |
| Relief/thermal relief/blowdown basis | Relief availability, set pressure, discharge path, creditability |
| Blowdown pressure profile | Time/pressure table, units, absolute/gauge convention, source calculation |
| Pipe fire material curve | Temperature-dependent UTS, strain effect, rupture strain limit, Sellars-Tegart constants |
| Pipe fluid basis | Fluid density, heat capacity, gas molecular weight, gas/liquid release basis |
| Design basis / technical requirements | Acceptance criteria, required margins, standards, reporting basis |
| Consequence or layout study | Source-term destination, escalation, radiation, dispersion context |

If any item is missing, do not invent it. Use a clearly labelled screening
default only when the result can still support a preliminary decision, and put
the gap in the assumptions/gaps register.

## Extraction Schema

Technical document readers should return a block like this to the solver:

```json
{
  "study_type": "trapped_liquid_fire_rupture",
  "segments": [
    {
      "segment_id": "TL-001",
      "line_numbers": ["..."],
      "isolation_boundary": {"upstream": "XV-...", "downstream": "XV-..."},
      "fluid": {"description": "...", "composition_source": "..."},
      "operating_pressure_bara": {"value": 10.0, "source": "..."},
      "operating_temperature_C": {"value": 25.0, "source": "..."},
      "pipe_internal_diameter_m": {"value": 0.10, "source": "..."},
      "wall_thickness_m": {"value": 0.003, "source": "..."},
      "exposed_length_m": {"value": 10.0, "source": "..."},
      "material_grade": {"value": "API 5L X52", "source": "..."},
      "flange_class": {"value": 900, "source": "..."},
      "fire_basis": {"type": "api521_pool_fire", "heat_flux_W_m2": null, "source": "..."},
      "pfp_requirement_s": {"value": 1800.0, "source": "..."},
      "relief": {"available": false, "set_pressure_bara": null, "source": "..."},
      "acceptance_criteria": [{"criterion": "...", "source": "..."}],
      "evidence_gaps": ["..."]
    }
  ]
}
```

Every numeric field should preserve original value, original unit, converted SI
value, source document, page/sheet, and confidence when available.

For blowdown pipe fire rupture, technical document readers or source-document agents
should return a block like this:

```json
{
  "study_type": "blowdown_pipe_fire_rupture",
  "segment_id": "BD-001",
  "pressure_profile": {
    "time_unit": "minute",
    "pressure_unit": "bara",
    "basis": "absolute pressure profile from governed blowdown calculation",
    "points": [[0.0, 61.3], [0.083333333, 59.7053]]
  },
  "pipes": [
    {
      "pipe_id": "3DD100",
      "pipe_class": "DD100",
      "nps_in": 3.0,
      "outside_diameter_mm": 88.9,
      "wall_thickness_mm": 3.7,
      "corrosion_allowance_mm": 0.0,
      "wall_undertolerance_fraction": 0.125,
      "weld_factor": 1.0,
      "material": "22Cr duplex",
      "fluid_density_kg_m3": 23.75,
      "fluid_heat_capacity_J_kgK": 2283.35,
      "gas_molecular_weight_kg_kmol": 18.2,
      "initial_temperature_C": 20.0,
      "exposed_length_m": 1.0,
      "source": "reviewed piping specification / workbook input"
    }
  ],
  "fire_scenarios": ["Small jet fire 250 kW/m2", "Pool fire 250 kW/m2", "Large jet fire 350 kW/m2"],
  "evidence_gaps": []
}
```

### Governed Source-Document Handoff Schema

When pipe data comes from source drawings and piping-specification rows, normalize it into a
source-traceable package before NeqSim calculation:

```json
{
  "schemaVersion": "pipe_fire_rupture_data_source.v1",
  "studyId": "BD-001",
  "input": {"segmentId": "3DD100", "evidenceReferences": []},
  "material": {"materialName": "22Cr duplex"},
  "scenario": {"name": "Large jet fire 350 kW/m2"},
  "pressureProfile": {"pressureUnit": "bara", "timeUnit": "seconds"},
  "pidTopologyEvidence": {
    "schemaVersion": "pid_topology_evidence.v1",
    "drawingId": "P-ID-001",
    "revision": "A",
    "simulationReady": false,
    "boundaryVerified": false,
    "nodes": [],
    "edges": [],
    "missingTags": []
  },
  "sourceDocumentEvidence": [],
  "pipingSpecificationEvidence": [],
  "processEvidence": [],
  "fireScenarioEvidence": [],
  "sourceDiagramsReviewed": true,
  "pidTopologyVerified": false,
  "pipingSpecificationRowsReviewed": true,
  "materialCertificateReviewed": false,
  "blowdownProfileVerified": true,
  "fireScenarioReviewed": true,
  "standardsReviewed": false,
  "humanReviewRequired": true,
  "readiness": {"verdict": "SCREENING"}
}
```

Readiness semantics:

- `NOT_READY`: missing calculation-critical input (`input`, material, scenario,
  or pressure profile). Do not run the calculation.
- `SCREENING`: calculation may run, but evidence gaps or unreviewed assumptions
  prevent design-grade use.
- `DESIGN_GRADE`: controlled source drawing, piping-specification, material, fire, and depressurization evidence
  has been reviewed and the package is ready for formal engineering review.

## Java Calculation Pattern

```java
SystemInterface oil = new SystemSrkEos(298.15, 10.0);
oil.addComponent("n-heptane", 100.0);
oil.setMixingRule("classic");

InventoryResult inventory = new TrappedInventoryCalculator()
    .setFluid(oil)
    .setOperatingConditions(10.0, "bara", 25.0, "C")
    .addPipeSegment("TL-001", 0.10, 10.0, 1.0, null)
    .calculate();

TrappedLiquidFireRuptureResult result = TrappedLiquidFireRuptureStudy.builder()
    .segmentId("TL-001")
    .fluid(oil)
    .inventory(inventory)
    .pipeGeometry(0.10, "m", 3.0, "mm", 10.0, "m")
    .api5lMaterial("X52")
    .fireScenario(FireExposureScenario.api521PoolFire(3.4, 1.0))
    .flangeClass(900)
    .timeControls(1800.0, 2.0)
    .build()
    .run();
```

Key classes:

- `MaterialStrengthCurve`: ambient SMYS/SMTS plus temperature derating.
- `FireExposureScenario`: API 521 pool fire, fixed heat flux, or radiative fire.
- `TrappedLiquidFireRuptureStudy`: transient pressure, wall temperature, pipe stress,
  flange rating, vapor-pocket, relief-set, and rupture checks.
- `TrappedLiquidFireRuptureResult`: event times, time histories, JSON map, PFP demand,
  and source-term handoff.

For supplied-pressure-profile blowdown pipe fire rupture:

```java
BlowdownPressureProfile profile = BlowdownPressureProfile.fromMinutesAndBara(
  new double[] {0.0, 0.083333333, 0.166666667},
  new double[] {61.3, 59.7053, 58.6349});

PipeFireRuptureInput pipe = PipeFireRuptureInput.builder("3DD100")
  .pipeClass("DD100")
  .nominalDiameterInches(3.0)
  .outsideDiameter(88.9, "mm")
  .nominalWallThickness(3.7, "mm")
  .corrosionAllowance(0.0, "mm")
  .wallThicknessUndertoleranceFraction(0.125)
  .weldFactor(1.0)
  .fluidDensityKgPerM3(23.75)
  .fluidHeatCapacityJPerKgK(2283.35)
  .gasMolecularWeightKgPerKmol(18.2)
  .initialTemperatureC(20.0)
  .exposedLength(1.0, "m")
  .build();

PipeFireRuptureResult pipeResult = PipeFireRuptureStudy
  .builder(pipe, PipeFireRuptureMaterial.fromSpreadsheetMaterialName("22Cr duplex"),
    PipeFireRuptureScenario.spreadsheetLargeJetFire(), profile)
  .timeStepSeconds(5.0)
  .maxTimeSeconds(1800.0)
  .build()
  .run();
```

For governed agentic studies, prefer the runner pattern:

```java
SafetyEvidenceReference pipingSpecWall = SafetyEvidenceReference
  .builder("PIPING_SPEC", "nominal_wall_thickness_mm")
  .documentId("pipe-class=DD100;rev=D")
  .valueText("3.7")
  .unit("mm")
  .status("fetched_joined")
  .confidence(0.95)
  .build();

PipeFireRuptureInput governedPipe = pipe.toBuilder()
  .evidenceReference(pipingSpecWall)
  .build();

PipeFireRuptureDataSource dataSource = PipeFireRuptureDataSource.builder("BD-001")
  .input(governedPipe)
  .material(PipeFireRuptureMaterial.fromSpreadsheetMaterialName("22Cr duplex"))
  .scenario(PipeFireRuptureScenario.spreadsheetLargeJetFire())
  .pressureProfile(profile)
  .addPipingSpecificationEvidence(pipingSpecWall)
  .sourceDiagramsReviewed(true)
  .pidTopologyVerified(false)
  .pipingSpecificationRowsReviewed(true)
  .materialCertificateReviewed(false)
  .blowdownProfileVerified(true)
  .fireScenarioReviewed(true)
  .standardsReviewed(false)
  .build();

PipeFireRuptureStudyHandoff handoff = PipeFireRuptureStudyRunner.builder()
  .timeStepSeconds(5.0)
  .maxTimeSeconds(1800.0)
  .runUncertainty(true)
  .build()
  .run(dataSource);
```

Key pipe-fire classes:

- `BlowdownPressureProfile`: absolute pressure profile with exact tabulated-point lookup, step or linear mode, and barg conversion.
- `PipeFireRuptureInput`: one pipe case with geometry, wall allowance, fluid, and exposed-length data.
- `PipeFireRuptureMaterial`: workbook-style material curves for 22Cr duplex, SS316, CS235, CS360/API 5L-X52, superduplex, and 6Mo.
- `PipeFireRuptureScenario`: small jet, pool fire, large jet, and custom radiative plus convective fire exposure.
- `PipeFireRuptureStudy`: heat-up, thick-wall stress, Sellars-Tegart strain rate, accumulated strain, rupture event, and screening release estimate.
- `PipeFireRuptureResult`: time series, rupture summary, warnings, recommendations, release estimate, and JSON map.
- `SafetyEvidenceReference`: compact source reference for source drawings, piping specifications, process, fire, and material inputs.
- `SafetyStudyReadiness`: `NOT_READY` / `SCREENING` / `DESIGN_GRADE` verdict with findings and actions.
- `PidTopologyEvidence`: typed P&ID topology graph, boundary status, missing-tag register, and drawing-overlay readiness.
- `PipeFireRuptureDataSource`: governed data-source package binding inputs to evidence and review flags.
- `PipeFireRuptureStudyRunner`: readiness-gated orchestration of solver, standards check, uncertainty, and source-term handoff.
- `PipeFireRuptureStudyHandoff`: versioned package containing data source, readiness, result, uncertainty, and source term.
- `PipeFireRuptureStandardsValidator`: API 521 / ISO 23251 / NORSOK S-001 / piping-specification evidence-quality gate.
- `PipeFireRuptureUncertaintyRunner`: deterministic one-at-a-time perturbation screening of wall, corrosion, heat-flux, and initial-temperature assumptions.
- `LineEquipmentListEvidence`, `DynamicBlowdownFlareStudyDataSource`, `DynamicBlowdownFlareStudyRunner`, and
  `DynamicBlowdownFlareStudyHandoff`: governed dynamic depressurization, PSV, and flare-load setup used to create a
  source-traceable pressure profile before pipe-fire rupture screening.

## Reusable Safety Report Template

For governed source-document pipe-fire studies, the Word/HTML report should use a
repeatable evidence-first structure. At minimum include:

1. **Executive verdict** with `NOT_READY`, `SCREENING`, or `DESIGN_GRADE`, plus
  the human-review status.
2. **Evidence matrix** with source system, document id, revision, page/sheet,
  field, extracted value, unit, status, confidence, and notes.
3. **Source drawing table** with drawing id, revision, embedded-text/OCR
  status, topology nodes/edges count, missing tags, and overlay/annotation link.
4. **Piping specification table** with applicable revision, class/material references, NPS,
  outside diameter, wall thickness, corrosion allowance, undertolerance, and
  row-review status.
5. **NeqSim input lineage** mapping each solver input to its `SafetyEvidenceReference`.
6. **Standards-applied table** covering API 521 / ISO 23251, NORSOK S-001,
  piping/material basis, and consequence/source-term handoff status.
7. **Assumptions and gaps register** with severity, effect on result, and required
  action before design use.
8. **Calculation results and uncertainty** including rupture time, rupture pressure,
  wall temperature, release estimate, deterministic perturbation cases, and
  P10/P50/P90 where available.
9. **Source-term handoff** using `pipe_fire_rupture_source_term_handoff.v1` when
  rupture is predicted.
10. **Calculation lineage** from document field to NeqSim input to reported result.

Before the report is considered complete, apply these hard QA gates:

- The executive summary and problem description must be populated from the
  current `task_spec.md` and `results.json`; no placeholder text may remain.
- The report front page must state the readiness label (`NOT_READY`,
  `SCREENING`, or `DESIGN_GRADE`) and whether design-grade use is blocked.
- The method in `task_spec.md`, source scripts, generated `results.json`, and
  report must agree on the pressure-profile basis. If the task moved from a
  reconstructed profile to a direct dynamic NeqSim profile, update all four.
- Script-backed studies are acceptable when `study_config.yaml` explicitly sets
  notebooks to not required; do not create false notebook execution warnings.
- `analysis.md` and `neqsim_improvements.md` must be filled for safety-critical
  workflow/code gaps, including reporting, evidence-readiness, pressure-profile
  export, plant-data evidence, and governed handoff gaps.
- The evidence gaps/design blockers and recommendations must appear before or
  alongside the conclusions, not only in appendix-style detail.

## Results to Save

Save a `trapped_liquid_fire_rupture` section in `results.json`:

```json
{
  "trapped_liquid_fire_rupture": {
    "segments": [
      {
        "segment_id": "TL-001",
        "limiting_failure_mode": "PIPE_RUPTURE",
        "time_to_pipe_rupture_s": 420.0,
        "time_to_flange_failure_s": null,
        "minimum_failure_time_s": 420.0,
        "final_pressure_bara": 145.0,
        "final_wall_temperature_C": 530.0,
        "pfp_required_endurance_s": 1800.0,
        "pfp_margin_s": -1380.0,
        "evidence_gaps": ["Material certificate not found"],
        "recommendations": ["Provide thermal relief or documented PFP upgrade"]
      }
    ],
    "standards_applied": ["API 521", "ISO 23251", "ASME B31.3", "ASME B16.5"],
    "assumptions": ["Generic API 5L X52 screening curve used pending certificate"],
    "evidence_matrix": []
  }
}
```

For Standard/Comprehensive studies, include:

- Segment summary table with event times and limiting mode.
- Evidence matrix with document, page/sheet, extracted value, confidence, and gap status.
- Assumptions/gaps register ranked by impact.
- Time histories for pressure, wall temperature, material allowable stress, and flange rating.
- PFP demand table from `toPassiveFireProtectionDemand(...)`.
- Source-term handoff from `createRuptureSourceTerm(...)` if rupture is predicted.
- Risk register using `neqsim-process-safety` when consequences are material.

For blowdown pipe fire rupture, save a `pipe_fire_rupture` section in
`results.json`:

```json
{
  "pipe_fire_rupture": {
    "segment_id": "BD-001",
    "pressure_profile_basis": "absolute bara profile from governed blowdown model",
    "pipes": [
      {
        "pipe_id": "3DD100",
        "fire_scenario": "Large jet fire 350 kW/m2",
        "rupture_predicted": true,
        "time_to_rupture_s": 110.0,
        "rupture_pressure_barg": 32.03,
        "rupture_wall_temperature_C": 760.0,
        "release_estimate_kg_s": 21.9,
        "evidence_gaps": []
      }
    ],
    "standards_applied": ["API 521", "ASME B31.3"],
    "assumptions": ["Spreadsheet material curve used pending certificate review"]
  }
}
```

For governed studies, also persist the runner handoff:

```json
{
  "pipe_fire_rupture_handoff": {
    "schemaVersion": "pipe_fire_rupture_study_handoff.v1",
    "calculationReadiness": {"verdict": "SCREENING"},
    "standardsReadiness": {"verdict": "SCREENING"},
    "result": {},
    "uncertainty": {"schemaVersion": "pipe_fire_rupture_uncertainty.v1"},
    "sourceTermHandoff": {"schemaVersion": "pipe_fire_rupture_source_term_handoff.v1"}
  }
}
```

## Validation and Benchmarking

- Hand-check pressure rise using `deltaP = bulk_modulus * alpha * deltaT`.
- Compare API 521 heat flux or heat input against an independent spreadsheet or standard example.
- Verify material ambient strength against pipe specification or certificate.
- For pipe-fire studies, benchmark one representative case against the source
  workbook or an independent spreadsheet before scaling to all pipe cases.
- Verify pressure-profile absolute/gauge convention. The workbook-style stress
  and release calculations use barg, while the pressure profile is often supplied
  as bara and converted by subtracting 1 bar.
- For high-consequence segments, treat the NeqSim result as screening and recommend
  specialist flange/gasket assessment, FEA, or consequence modelling as needed.

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Using a generic flange class as final proof | Replace with project flange, bolt, and gasket pressure-temperature data |
| Crediting relief without a discharge path | Verify relief/thermal relief path on P&ID and relief design basis |
| Ignoring vents/drains | Include them in the isolation boundary and trapped-volume assessment |
| Assuming PFP is installed and intact | Require PFP specification and inspection/condition evidence |
| Reporting only rupture time | Also report assumptions, evidence gaps, PFP margin, and source-term consequence handoff |
| Treating pressure-profile units casually | Record whether the profile is bara or barg and convert explicitly |
| Letting superduplex map to 22Cr duplex | Use `PipeFireRuptureMaterial.fromSpreadsheetMaterialName` or a reviewed material curve |
| Running plant-wide pipe-fire cases without review | Ask the engineer to verify source-document, piping-specification, and user overrides before calculation |

## Related Documentation

- `docs/safety/trapped_liquid_fire_rupture.md`
- `docs/safety/trapped_inventory_calculator.md`
