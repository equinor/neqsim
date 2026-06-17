---
name: neqsim-trapped-liquid-fire-rupture
version: "1.0.0"
description: "Trapped-liquid fire rupture study workflow for blocked-in liquid-filled pipe segments. USE WHEN: a task asks for trapped liquid, blocked-in liquid, thermal expansion rupture, fire exposure without relief, PFP demand, flange/pipe rupture screening, or generating a Word/HTML safety study from P&IDs, STID, line lists, piping specs, material certificates, and fire documents. Anchors on neqsim.process.safety.rupture plus trapped inventory, document retrieval, and source-term handoff."
last_verified: "2026-05-10"
requires:
  java_packages:
    - neqsim.process.safety.rupture
    - neqsim.process.safety.inventory
    - neqsim.process.safety.release
    - neqsim.process.safety.barrier
---

# Trapped-Liquid Fire Rupture Study

This skill coordinates the full study workflow for blocked-in liquid-filled
segments exposed to fire: evidence retrieval, technical document extraction,
trapped inventory calculation, fire heat input, temperature-dependent material
strength, pipe/flange rupture screening, PFP demand, source-term handoff, and
professional report output.

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

## Skills to Load Together

- `neqsim-stid-retriever` for P&IDs/STIDs, line lists, piping specs, material
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
| P&ID / STID / isometric | Isolation boundary, valves, vents, drains, relief paths, line numbers |
| Line list / route table | NPS, internal diameter, wall thickness, length, design pressure/temperature |
| Piping specification | Material class, corrosion allowance, flange class, gasket/bolt family |
| Material certificate or material class sheet | SMYS, SMTS, grade, temperature limits, toughness notes |
| Flange/bolt/gasket data | Pressure-temperature rating and leakage/rupture limitations |
| Fire-zone / fire-study document | Exposed area, heat flux, pool/jet-fire basis, fire duration |
| PFP requirement or inspection record | Required endurance and actual protection condition |
| Relief/thermal relief/blowdown basis | Relief availability, set pressure, discharge path, creditability |
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

## Validation and Benchmarking

- Hand-check pressure rise using `deltaP = bulk_modulus * alpha * deltaT`.
- Compare API 521 heat flux or heat input against an independent spreadsheet or standard example.
- Verify material ambient strength against pipe specification or certificate.
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

## Related Documentation

- `docs/safety/trapped_liquid_fire_rupture.md`
- `docs/safety/trapped_inventory_calculator.md`
