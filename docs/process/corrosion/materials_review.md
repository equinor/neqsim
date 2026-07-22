---
title: Process-Wide Materials Review
description: Process-wide materials selection, corrosion, degradation, integrity, and remaining-life review using NeqSim. Covers STID and technical database JSON input, CO2 corrosion, sour service, chloride SCC, oxygen corrosion, dense CO2, hydrogen, ammonia, CUI, MIC, erosion, fatigue, and MCP integration.
---

The `neqsim.process.materials` package provides a process-wide review layer for material selection, corrosion and degradation screening, integrity checks, and lifetime considerations. It orchestrates the existing point calculators in `neqsim.process.corrosion` and mechanical-design utilities into one auditable report per equipment or line-list item.

The main use case is to support and challenge projects or producing assets using normalized data from STID, piping classes, equipment registers, inspection databases, material certificates, and process simulations. The examples below are synthetic and must not be replaced with real asset names, document IDs, or internal database rows in public documentation.

## What It Does

| Capability | Implementation |
|------------|----------------|
| CO2 corrosion rate | `NorsokM506CorrosionRate` |
| Material selection and corrosion allowance | `NorsokM001MaterialSelection` |
| Sour service | `SourServiceAssessment` |
| Chloride SCC | `ChlorideSCCAssessment` |
| Dissolved oxygen corrosion and pitting | `OxygenCorrosionAssessment` |
| Dense phase CO2 impurity and free-water risk | `DensePhaseCO2Corrosion` |
| Hydrogen embrittlement and HTHA | `HydrogenMaterialAssessment`, `NelsonCurveAssessment` |
| Ammonia service compatibility | `AmmoniaCompatibility` |
| Corrosion under insulation | `CUIRiskAssessment` |
| Erosion-corrosion, MIC, galvanic corrosion, fatigue/FIV | Screening rules in `MaterialsReviewEngine` |
| Remaining life | Wall-thickness and corrosion-rate screening in `IntegrityLifeAssessment` |

## Core Classes

| Class | Purpose |
|-------|---------|
| `MaterialsReviewEngine` | Runs the review from normalized input or a `ProcessSystem` plus material register |
| `MaterialsReviewInput` | Holds project metadata, design life, and review items |
| `MaterialReviewItem` | One tag, line, vessel, exchanger, or other asset to assess |
| `MaterialServiceEnvelope` | Flexible service-condition map for process and integrity data |
| `StidMaterialsDataSource` | Normalized STID/technical database JSON bridge |
| `MaterialReviewResult` | Item-level findings, mechanisms, recommendation, and life result |
| `MaterialsReviewReport` | Process-wide JSON report |

## STID and Technical Database Workflow

NeqSim does not connect directly to STID from the Java core. Retrieval, OCR, and document parsing remain in `devtools` and skills. The Java engine consumes normalized JSON, which keeps the core deterministic and testable.

Typical workflow:

1. Extract or retrieve STID, line-list, material-class, inspection, and certificate data.
2. Normalize rows into `materialsRegister`, `lineList`, `equipment`, `inspectionData`, or `materialCertificates` arrays.
3. Include source references such as document names, row IDs, tag IDs, or database keys.
4. Optionally provide `processJson` so NeqSim can calculate pressure, temperature, and composition before the review.
5. Run `MaterialsReviewEngine` directly or the MCP tool `runMaterialsReview`.

Records with the same tag are merged, so material data and inspection data can come from different extracts.

Before committing review inputs or outputs to an open repository, strip or pseudonymize source document names, tag numbers, database IDs, asset names, and sensitive inspection comments. The engine echoes item tags, source references, metadata, and service values in its JSON report for traceability.

## JSON Input Shape

```json
{
  "projectName": "Synthetic materials review",
  "designLifeYears": 25,
  "materialsRegister": [
    {
      "tag": "DEMO-LINE-001",
      "equipmentType": "Pipeline",
      "existingMaterial": "Carbon Steel API 5L X65",
      "sourceReferences": ["synthetic STID line-list row 1"],
      "service": {
        "temperature_C": 85.0,
        "pressure_bara": 95.0,
        "co2_mole_fraction": 0.04,
        "h2s_mole_fraction": 0.0008,
        "free_water": true,
        "chloride_mg_per_l": 55000.0,
        "pH": 5.2,
        "flow_velocity_m_per_s": 7.5,
        "nominal_wall_thickness_mm": 18.0,
        "current_wall_thickness_mm": 15.2,
        "minimum_required_thickness_mm": 11.0
      }
    }
  ]
}
```

## Java Usage

```java
MaterialsReviewInput input = new MaterialsReviewInput();
MaterialServiceEnvelope service = new MaterialServiceEnvelope()
    .set("temperature_C", 85.0)
    .set("pressure_bara", 95.0)
    .set("co2_mole_fraction", 0.04)
    .set("h2s_mole_fraction", 0.0008)
    .set("free_water", Boolean.TRUE)
    .set("chloride_mg_per_l", 55000.0);

input.addItem(new MaterialReviewItem()
    .setTag("DEMO-LINE-001")
    .setEquipmentType("Pipeline")
    .setExistingMaterial("Carbon Steel API 5L X65")
    .setServiceEnvelope(service));

MaterialsReviewReport report = new MaterialsReviewEngine().evaluate(input);
System.out.println(report.toJson());
```

This pattern is covered by `MaterialsReviewEngineTest`.

## Process Simulation Overlay

When a `ProcessSystem` is supplied, the engine extracts each unit operation's temperature, pressure, and selected component mole fractions. The material register is then merged by tag.

```java
MaterialsReviewReport report = new MaterialsReviewEngine().evaluate(processSystem, materialRegister);
```

Use this when the process model is the source of operating conditions and STID is the source of material, wall-thickness, coating, insulation, and inspection data.

## MCP Tool

The MCP server exposes the same workflow as `runMaterialsReview`.

```json
{
  "materialsReviewJson": "{\"projectName\":\"Synthetic materials review\",\"materialsRegister\":[...]}"
}
```

The MCP output includes:

- `overallVerdict`: `PASS`, `PASS_WITH_WARNINGS`, or `FAIL`
- `items`: item-level verdicts, mechanisms, details, recommendation, and remaining-life screening
- `standardsApplied`: standards and recommended practices referenced by the mechanisms
- `limitations`: screening assumptions and review boundaries
- `provenance`: MCP calculation provenance

## Output Interpretation

The report is intended for screening, challenge, and decision-support workflows. A `FAIL` means at least one mechanism or integrity-life check needs engineering attention. A `PASS_WITH_WARNINGS` means the material may be acceptable, but the review found inspection, operating-envelope, data-quality, or mitigation actions to close.

Final material selection, corrosion management strategy, and integrity decisions still require discipline engineer review, project technical requirements, and verified STID/source data.

## Related Documentation

- [Corrosion Module Overview](index.md)
- [NORSOK M-506 Corrosion Rate](norsok_m506_corrosion_rate.md)
- [NORSOK M-001 Material Selection](norsok_m001_material_selection.md)
- [Sour Service Assessment](sour_service_assessment.md)
- [Pipeline Corrosion Integration](pipeline_corrosion_integration.md)
- [Pipeline Mechanical Design](../pipeline_mechanical_design.md)
