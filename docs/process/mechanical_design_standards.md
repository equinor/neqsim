---
title: Mechanical Design Standards in NeqSim
description: Audited catalog of mechanical-design standards, implementation maturity, calculation paths, and engineering limitations in NeqSim.
---

NeqSim catalogs standards that may be relevant to process-equipment design. Catalog inclusion is
not a statement that every requirement or calculation in a published standard has been implemented.
The matrix below reports the calculation evidence that currently exists in NeqSim.

## Maturity definitions

| Maturity | Meaning |
| --- | --- |
| `CATALOGUED` | Standard identity, edition metadata, category, and equipment applicability are available, but no standard calculation is exposed. |
| `SCREENING` | A preliminary engineering calculation is available with the boundary stated in the matrix. It is not a conformity assessment. |
| `VALIDATED` | The stated calculation range has independent numerical validation and controlled regression evidence. |
| `QUALIFIED` | A controlled implementation and evidence package has been released for a stated use. Accountable engineering approval is still required. |

No current entry is classified as `VALIDATED` or `QUALIFIED`. Existing calculations remain useful
for preliminary screening, but project criteria, purchased standards, vendor data, and engineering
review remain governing.

## Current support matrix

The table between the generated markers is produced by
`StandardSupportAudit.generateMarkdownTable()`. A regression test fails if the published table and
the source catalog diverge.

<!-- BEGIN GENERATED STANDARD SUPPORT MATRIX -->
| Standard | Edition metadata | Category | Registry factory | Calculation path | Maturity | Boundary |
| --- | --- | --- | --- | --- | --- | --- |
| NORSOK-L-001 | Rev 6 | pipeline design codes | PipelineDesignStandard | PipelineDesignStandard | SCREENING | Preliminary category screening with fixed fallback values; not a complete edition-specific wall-thickness calculation. |
| NORSOK-P-001 | Rev 5 | separator process design | SeparatorDesignStandard | SeparatorDesignStandard | SCREENING | Preliminary K-factor and sizing inputs only; standard-specific requirements are not independently validated. |
| NORSOK-P-002 | Rev 5 | separator process design | SeparatorDesignStandard | SeparatorDesignStandard | SCREENING | Preliminary K-factor and sizing inputs only; standard-specific requirements are not independently validated. |
| NORSOK-M-001 | Rev 6 | material plate design codes | MaterialPlateDesignStandard | MaterialPlateDesignStandard | SCREENING | Material-property lookup only; material selection, qualification, and code acceptance are not implemented. |
| NORSOK-M-630 | Rev 7 | material pipe design codes | MaterialPipeDesignStandard | MaterialPipeDesignStandard | SCREENING | Material-property lookup only; material selection, qualification, and code acceptance are not implemented. |
| ASME-VIII-Div1 | 2021 | pressure vessel design code | PressureVesselDesignStandard | PressureVesselDesignStandard | SCREENING | Generic thin-wall separator screening only; edition-specific clauses and complete vessel checks are not implemented. |
| ASME-VIII-Div2 | 2021 | pressure vessel design code | PressureVesselDesignStandard | PressureVesselDesignStandard | SCREENING | Generic thin-wall separator screening only; edition-specific clauses and complete vessel checks are not implemented. |
| ASME-B31.3 | 2022 | pipeline design codes | PipelineDesignStandard | PipelineDesignStandard | SCREENING | Preliminary category screening with fixed fallback values; not a complete edition-specific wall-thickness calculation. |
| ASME-B31.4 | 2022 | pipeline design codes | PipelineDesignStandard | PipelineDesignStandard | SCREENING | Preliminary category screening with fixed fallback values; not a complete edition-specific wall-thickness calculation. |
| ASME-B31.8 | 2022 | pipeline design codes | PipelineDesignStandard | PipelineDesignStandard | SCREENING | Preliminary category screening with fixed fallback values; not a complete edition-specific wall-thickness calculation. |
| API-617 | 8th Ed | compressor design codes | CompressorDesignStandard | CompressorDesignStandard | SCREENING | Preliminary compressor-factor screening only; package and vendor requirements are not implemented. |
| API-610 | 13th Ed | pump design codes | DesignStandard | PumpApi610DesignKernel | SCREENING | API 610 screening is connected through a pure engineering-workflow adapter; purchased-standard, project, and vendor verification remain required. |
| API-650 | 13th Ed | pressure vessel design code | PressureVesselDesignStandard | None | CATALOGUED | The registry maps this tank standard to a separator-oriented pressure-vessel class; no tank-code calculation is implemented. |
| API-620 | 13th Ed | pressure vessel design code | PressureVesselDesignStandard | None | CATALOGUED | The registry maps this tank standard to a separator-oriented pressure-vessel class; no tank-code calculation is implemented. |
| API-660 | 9th Ed | heat exchanger design codes | DesignStandard | None | CATALOGUED | No standard-specific heat-exchanger mechanical calculation is connected. |
| API-661 | 7th Ed | heat exchanger design codes | DesignStandard | None | CATALOGUED | No standard-specific heat-exchanger mechanical calculation is connected. |
| API-521 | 7th Ed | valve design codes | ValveDesignStandard | None | CATALOGUED | The mapped valve class does not implement relief-system or relief-valve standard calculations. |
| API-526 | 7th Ed | valve design codes | ValveDesignStandard | None | CATALOGUED | The mapped valve class does not implement relief-system or relief-valve standard calculations. |
| API-5L | 46th Ed | material pipe design codes | MaterialPipeDesignStandard | MaterialPipeDesignStandard | SCREENING | Material-property lookup only; material selection, qualification, and code acceptance are not implemented. |
| API-12J | 8th Ed | separator process design | SeparatorDesignStandard | SeparatorDesignStandard | SCREENING | Preliminary K-factor and sizing inputs only; standard-specific requirements are not independently validated. |
| DNV-ST-F101 | 2021 | pipeline design codes | PipelineDesignStandard | PipelineDesignStandard | SCREENING | Preliminary category screening with fixed fallback values; not a complete edition-specific wall-thickness calculation. |
| DNV-OS-F101 | 2013 | pipeline design codes | PipelineDesignStandard | PipelineDesignStandard | SCREENING | Preliminary category screening with fixed fallback values; not a complete edition-specific wall-thickness calculation. |
| DNV-RP-F105 | 2021 | pipeline design codes | PipelineDesignStandard | PipelineDesignStandard | SCREENING | Preliminary category screening with fixed fallback values; not a complete edition-specific wall-thickness calculation. |
| ISO-13623 | 2017 | pipeline design codes | PipelineDesignStandard | PipelineDesignStandard | SCREENING | Preliminary category screening with fixed fallback values; not a complete edition-specific wall-thickness calculation. |
| ISO-15649 | 2001 | pipeline design codes | PipelineDesignStandard | PipelineDesignStandard | SCREENING | Preliminary category screening with fixed fallback values; not a complete edition-specific wall-thickness calculation. |
| ISO-16812 | 2019 | heat exchanger design codes | DesignStandard | None | CATALOGUED | No standard-specific heat-exchanger mechanical calculation is connected. |
| ASTM-A106 | 2022 | material pipe design codes | MaterialPipeDesignStandard | MaterialPipeDesignStandard | SCREENING | Material-property lookup only; material selection, qualification, and code acceptance are not implemented. |
| ASTM-A516 | 2022 | material plate design codes | MaterialPlateDesignStandard | MaterialPlateDesignStandard | SCREENING | Material-property lookup only; material selection, qualification, and code acceptance are not implemented. |
| ASTM-A333 | 2022 | material pipe design codes | MaterialPipeDesignStandard | MaterialPipeDesignStandard | SCREENING | Material-property lookup only; material selection, qualification, and code acceptance are not implemented. |
| EN-13480 | 2017 | pipeline design codes | PipelineDesignStandard | PipelineDesignStandard | SCREENING | Preliminary category screening with fixed fallback values; not a complete edition-specific wall-thickness calculation. |
| EN-13445 | 2021 | pressure vessel design code | PressureVesselDesignStandard | PressureVesselDesignStandard | SCREENING | Generic thin-wall separator screening only; edition-specific clauses and complete vessel checks are not implemented. |
| PD-5500 | 2021 | pressure vessel design code | PressureVesselDesignStandard | PressureVesselDesignStandard | SCREENING | Generic thin-wall separator screening only; edition-specific clauses and complete vessel checks are not implemented. |
<!-- END GENERATED STANDARD SUPPORT MATRIX -->

## Typed standard selection

New code can select an explicit edition and any project amendments without depending on the
registry's process-wide version overrides:

```java
StandardEdition edition = StandardEdition.of(StandardType.API_12J, "8th Ed",
    Arrays.asList("Project amendment A", "Corrigendum 1"));
mechanicalDesign.setDesignStandard(StandardSelection.strict(edition));
```

Strict selection fails closed with a `StandardSelectionException` when the entry is catalog-only,
its calculation is not connected to the registry, the equipment context is missing, or the
standard is not listed for that equipment type. The exception exposes a machine-readable reason so
applications do not need to parse its message. `StandardRegistry.assessApplicability(...)` provides
the same applicability decision without creating a standard.

`StandardSelection.legacy(...)` is available for migrations that need an explicit edition while
retaining the permissive factory behavior. It may create a metadata-only `DesignStandard`; it does
not add calculation support.

## Consolidated design kernels

`EquipmentDesignKernel<I, O>` extends the existing typed engineering-calculation contract with the
implemented standard, audited maturity, and structured applicability. Kernels must not mutate their
input or a `ProcessSystem`. Compatibility adapters defensively copy legacy mutable calculators.

`StandardRegistry.getDesignKernel(...)` returns an explicit lookup status. API 610 is the first
connected adapter and returns `IMPLEMENTED`; standards that have not been adapted return
`NOT_IMPLEMENTED`, never an empty or implied success. The API 610 kernel returns an immutable
assessment snapshot and always requires engineering and vendor review because its maturity remains
`SCREENING`. The current kernel explicitly supports 13th edition; a different edition fails closed
as `EDITION_NOT_IMPLEMENTED` until separately implemented and validated.

## How to interpret the registry

`StandardRegistry.createStandard(...)` remains backward compatible. The factory class shown in the
matrix records what it currently creates; it does not prove that the resulting class implements the
selected edition. `StandardRegistry.getMappedImplementationClass(...)` allows applications and
audits to inspect that mapping without constructing equipment or accessing the design database.

The API 610 pump screen remains available through `PumpMechanicalDesign`. The consolidated
`PumpApi610DesignKernel` is a pure adapter over the same calculator, so existing callers are not
removed or redirected. The generic legacy factory still creates a base `DesignStandard`; executable
kernel support is exposed explicitly through `StandardRegistry.getDesignKernel(...)`.

## Engineering boundary

- Treat `CATALOGUED` entries as discovery metadata only.
- Treat `SCREENING` results as preliminary design information with the stated boundary.
- Do not infer certification, code compliance, vendor acceptance, or construction readiness.
- Record the purchased edition, project amendments, input provenance, and accountable approval
  outside the current legacy registry.

See the [engineering capability statement](../engineering/current-capabilities.md) for the broader
process-to-engineering workflow and its qualification boundary.
