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
| Standard | Edition metadata | Lifecycle | Publisher source | Category | Registry factory | Calculation path | Maturity | Current kernel | Boundary |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| NORSOK-L-001 | 2017 | CURRENT | [publisher](https://standard.no/en/sectors/energi-og-klima/petroleum/norsok-standard-categories/l-piping--layout/) (checked 2026-07-21) | pipeline design codes | PipelineDesignStandard | None | CATALOGUED | no | Catalogued pipeline selections fail closed because no edition-specific wall-thickness calculation is connected. |
| NORSOK-P-001 | Rev 5 | SUPERSEDED | [publisher](https://standard.no/en/sectors/petroleum/norsok-standards/p-process) (checked 2026-07-21) | separator process design | SeparatorDesignStandard | SeparatorDesignStandard | SCREENING | no | Preliminary K-factor and sizing inputs only; standard-specific requirements are not independently validated. |
| NORSOK-P-002 | 2023+AC:2024 | CURRENT | [publisher](https://standard.no/en/sectors/petroleum/norsok-standards/p-process) (checked 2026-07-21) | separator process design | SeparatorDesignStandard | StandardRequirementPackRegistry (3 capabilities) | SCREENING | no | Mapped calculations and review workflows are discoverable as a versioned requirement pack; this is not a complete conformity assessment and is intentionally separate from the legacy factory. |
| NORSOK-M-001 | 2025 | CURRENT | [publisher](https://standard.no/en/sectors/energi-og-klima/petroleum/norsok-standard-categories/m-material/) (checked 2026-07-21) | material plate design codes | MaterialPlateDesignStandard | StandardRequirementPackRegistry (2 capabilities) | SCREENING | no | Mapped calculations and review workflows are discoverable as a versioned requirement pack; this is not a complete conformity assessment and is intentionally separate from the legacy factory. |
| NORSOK-M-630 | 2020 | CURRENT | [publisher](https://standard.no/en/sectors/energi-og-klima/petroleum/norsok-standard-categories/m-material/) (checked 2026-07-21) | material pipe design codes | MaterialPipeDesignStandard | MaterialPipeDesignStandard | SCREENING | no | Material-property lookup only; material selection, qualification, and code acceptance are not implemented. |
| NORSOK-S-001 | 2020+AC:2021 | CURRENT | [publisher](https://standard.no/en/sectors/energi-og-klima/petroleum/norsok-standard-categories/s-safety-she/) (checked 2026-07-21) | process safety requirements | DesignStandard | StandardRequirementPackRegistry (2 capabilities) | SCREENING | no | Mapped calculations and review workflows are discoverable as a versioned requirement pack; this is not a complete conformity assessment and is intentionally separate from the legacy factory. |
| NORSOK-I-002 | 2021 | CURRENT | [publisher](https://standard.no/en/sectors/petroleum/norsok-standards/i-instrumentation) (checked 2026-07-21) | functional safety requirements | DesignStandard | None | CATALOGUED | no | No category-specific calculation is connected. |
| ASME-VIII-Div1 | 2025 | CURRENT | [publisher](https://www.asme.org/codes-standards/bpvc-standards/bpvc-2025) (checked 2026-07-21) | pressure vessel design code | PressureVesselDesignStandard | PressureVesselDesignStandard | SCREENING | no | Generic thin-wall separator screening only; edition-specific clauses and complete vessel checks are not implemented. |
| ASME-VIII-Div2 | 2025 | CURRENT | [publisher](https://www.asme.org/codes-standards/bpvc-standards/bpvc-2025) (checked 2026-07-21) | pressure vessel design code | PressureVesselDesignStandard | None | CATALOGUED | no | No Division 2 pressure-vessel calculation is implemented; the legacy generic fallback is blocked for this selection. |
| ASME-B31.3 | 2024 | CURRENT | [publisher](https://www.asme.org/codes-standards/find-codes-standards/b313-2018-process-piping) (checked 2026-07-21) | pipeline design codes | PipelineDesignStandard | None | CATALOGUED | no | Catalogued pipeline selections fail closed because no edition-specific wall-thickness calculation is connected. |
| ASME-B31.4 | 2025 | CURRENT | [publisher](https://www.asme.org/codes-standards/find-codes-standards/b31-4-pipeline-transportation-systems-liquids-slurries) (checked 2026-07-21) | pipeline design codes | PipelineDesignStandard | None | CATALOGUED | no | Catalogued pipeline selections fail closed because no edition-specific wall-thickness calculation is connected. |
| ASME-B31.8 | 2025 | CURRENT | [publisher](https://www.asme.org/codes-standards/find-codes-standards/b31-8-gas-transmission-distribution-piping-systems) (checked 2026-07-21) | pipeline design codes | PipelineDesignStandard | None | CATALOGUED | no | Catalogued pipeline selections fail closed because no edition-specific wall-thickness calculation is connected. |
| API-617 | 9th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | compressor design codes | CompressorDesignStandard | Api617CompressorDesignKernel | SCREENING | no | Compressor-casing pressure containment, flange, nozzle-load allowance, and thermal-growth screening only; rotor dynamics, package integration, and vendor conformity are not evaluated. |
| API-610 | 12th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | pump design codes | DesignStandard | PumpApi610DesignKernel | SCREENING | no | API 610 screening is connected through a pure engineering-workflow adapter; purchased-standard, project, and vendor verification remain required. |
| API-650 | 13th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | pressure vessel design code | PressureVesselDesignStandard | StandardRequirementPackRegistry (1 capabilities) | SCREENING | no | Mapped calculations and review workflows are discoverable as a versioned requirement pack; this is not a complete conformity assessment and is intentionally separate from the legacy factory. |
| API-620 | 12th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | pressure vessel design code | PressureVesselDesignStandard | None | CATALOGUED | no | The registry maps this tank standard to a separator-oriented pressure-vessel class; no edition-specific common calculation is implemented. |
| API-660 | 10th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | heat exchanger design codes | DesignStandard | StandardRequirementPackRegistry (1 capabilities) | SCREENING | no | Mapped calculations and review workflows are discoverable as a versioned requirement pack; this is not a complete conformity assessment and is intentionally separate from the legacy factory. |
| API-661 | 8th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | heat exchanger design codes | DesignStandard | None | CATALOGUED | no | No standard-specific heat-exchanger mechanical calculation is connected. |
| API-521 | 7th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | relief system design codes | ValveDesignStandard | Api521ReliefDesignKernel | SCREENING | yes | Scenario aggregation, governing-case selection, relief-area sizing, and accumulated-pressure screening only; scenario completeness, installation, and conformity require independent review. |
| API-526 | 8th Ed | CURRENT | [publisher](https://www.api.org/-/media/files/publications/2025-catalog/06_refining_2025.pdf) (checked 2026-07-21) | relief valve design codes | ValveDesignStandard | Api526OrificeSelectionKernel | SCREENING | no | Standard-orifice area selection only; valve pressure class, dimensions, materials, installation, and vendor certification are not evaluated. |
| API-520-Part-1 | 10th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | relief valve design codes | DesignStandard | StandardRequirementPackRegistry (2 capabilities) | SCREENING | no | Mapped calculations and review workflows are discoverable as a versioned requirement pack; this is not a complete conformity assessment and is intentionally separate from the legacy factory. |
| API-520-Part-2 | 7th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | relief valve design codes | DesignStandard | None | CATALOGUED | no | No category-specific calculation is connected. |
| API-527 | 5th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | relief valve design codes | DesignStandard | None | CATALOGUED | no | No category-specific calculation is connected. |
| API-2000 | 8th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | tank venting design codes | DesignStandard | None | CATALOGUED | no | No category-specific calculation is connected. |
| API-614 | 6th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | rotating equipment design codes | DesignStandard | None | CATALOGUED | no | No category-specific calculation is connected. |
| API-618 | 6th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | compressor design codes | CompressorDesignStandard | CompressorDesignStandard | SCREENING | no | Preliminary compressor-factor screening only; package and vendor requirements are not implemented. |
| API-625 | 3rd Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | pressure vessel design code | PressureVesselDesignStandard | None | CATALOGUED | no | The registry maps this tank standard to a separator-oriented pressure-vessel class; no edition-specific common calculation is implemented. |
| API-670 | 5th Ed | UNVERIFIED | unverified | rotating equipment design codes | DesignStandard | None | CATALOGUED | no | No category-specific calculation is connected. |
| API-676 | 4th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | pump design codes | DesignStandard | None | CATALOGUED | no | No category-specific calculation is connected. |
| API-685 | 3rd Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | pump design codes | DesignStandard | None | CATALOGUED | no | No category-specific calculation is connected. |
| API-5L | 47th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | material pipe design codes | MaterialPipeDesignStandard | MaterialPipeDesignStandard | SCREENING | no | Material-property lookup only; material selection, qualification, and code acceptance are not implemented. |
| API-12J | 9th Ed | CURRENT | [publisher](https://www.api.org/products-and-services/standards/digital-catalog) (checked 2026-07-21) | separator process design | SeparatorDesignStandard | Api12JSeparatorDesignKernel | SCREENING | no | Gravity cut-diameter, K-factor, and liquid residence-time screening only; service applicability, vessel construction, internals, and performance guarantees require independent review. |
| DNV-ST-F101 | 2021 | CURRENT | [publisher](https://www.dnv.com/energy/standards-guidelines/dnv-st-f101-submarine-pipeline-systems/) (checked 2026-07-21) | pipeline design codes | PipelineDesignStandard | StandardRequirementPackRegistry (1 capabilities) | SCREENING | no | Mapped calculations and review workflows are discoverable as a versioned requirement pack; this is not a complete conformity assessment and is intentionally separate from the legacy factory. |
| DNV-OS-F101 | 2013 | UNVERIFIED | unverified | pipeline design codes | PipelineDesignStandard | None | CATALOGUED | no | Catalogued pipeline selections fail closed because no edition-specific wall-thickness calculation is connected. |
| DNV-RP-F105 | 2021 | UNVERIFIED | unverified | pipeline design codes | PipelineDesignStandard | None | CATALOGUED | no | Catalogued pipeline selections fail closed because no edition-specific wall-thickness calculation is connected. |
| ISO-13623 | 2017 | UNVERIFIED | unverified | pipeline design codes | PipelineDesignStandard | None | CATALOGUED | no | Catalogued pipeline selections fail closed because no edition-specific wall-thickness calculation is connected. |
| ISO-15649 | 2001 | UNVERIFIED | unverified | pipeline design codes | PipelineDesignStandard | None | CATALOGUED | no | Catalogued pipeline selections fail closed because no edition-specific wall-thickness calculation is connected. |
| ISO-16812 | 2019 | UNVERIFIED | unverified | heat exchanger design codes | DesignStandard | None | CATALOGUED | no | No standard-specific heat-exchanger mechanical calculation is connected. |
| ISO-23251 | 2019 | CURRENT | [publisher](https://www.iso.org/standard/75144.html) (checked 2026-07-21) | relief system design codes | DesignStandard | None | CATALOGUED | no | No category-specific calculation is connected. |
| ISO-4126-1 | 2013 | CURRENT | [publisher](https://www.iso.org/standard/50826.html) (checked 2026-07-21) | relief valve design codes | DesignStandard | None | CATALOGUED | no | No category-specific calculation is connected. |
| ISO-10418 | 2019 | CURRENT | [publisher](https://www.iso.org/standard/55440.html) (checked 2026-07-21) | process safety requirements | DesignStandard | StandardRequirementPackRegistry (2 capabilities) | SCREENING | no | Mapped calculations and review workflows are discoverable as a versioned requirement pack; this is not a complete conformity assessment and is intentionally separate from the legacy factory. |
| IEC-61511 | 2016+AMD1:2017 | CURRENT | [publisher](https://webstore.iec.ch/en/publication/5527) (checked 2026-07-21) | functional safety requirements | DesignStandard | StandardRequirementPackRegistry (2 capabilities) | SCREENING | no | Mapped calculations and review workflows are discoverable as a versioned requirement pack; this is not a complete conformity assessment and is intentionally separate from the legacy factory. |
| IEC-60534 | Current series | UNVERIFIED | unverified | valve design codes | ValveDesignStandard | StandardRequirementPackRegistry (2 capabilities) | SCREENING | no | Mapped calculations and review workflows are discoverable as a versioned requirement pack; this is not a complete conformity assessment and is intentionally separate from the legacy factory. |
| ASTM-A106 | 2022 | UNVERIFIED | unverified | material pipe design codes | MaterialPipeDesignStandard | MaterialPipeDesignStandard | SCREENING | no | Material-property lookup only; material selection, qualification, and code acceptance are not implemented. |
| ASTM-A516 | 2022 | UNVERIFIED | unverified | material plate design codes | MaterialPlateDesignStandard | MaterialPlateDesignStandard | SCREENING | no | Material-property lookup only; material selection, qualification, and code acceptance are not implemented. |
| ASTM-A333 | 2022 | UNVERIFIED | unverified | material pipe design codes | MaterialPipeDesignStandard | MaterialPipeDesignStandard | SCREENING | no | Material-property lookup only; material selection, qualification, and code acceptance are not implemented. |
| EN-13480 | 2017 | UNVERIFIED | unverified | pipeline design codes | PipelineDesignStandard | None | CATALOGUED | no | Catalogued pipeline selections fail closed because no edition-specific wall-thickness calculation is connected. |
| EN-13445 | 2021 | UNVERIFIED | unverified | pressure vessel design code | PressureVesselDesignStandard | PressureVesselDesignStandard | SCREENING | no | Generic thin-wall separator screening only; edition-specific clauses and complete vessel checks are not implemented. |
| PD-5500 | 2021 | UNVERIFIED | unverified | pressure vessel design code | PressureVesselDesignStandard | PressureVesselDesignStandard | SCREENING | no | Generic thin-wall separator screening only; edition-specific clauses and complete vessel checks are not implemented. |
<!-- END GENERATED STANDARD SUPPORT MATRIX -->

## Typed standard selection

New code can select an explicit current edition without depending on the registry's process-wide
version overrides:

```java
StandardSelection selection = StandardSelection.strict(StandardType.API_521);
mechanicalDesign.setDesignStandard(selection);
```

`StandardSelection.strict(...)` requires a publisher-verified current edition and an exact typed
kernel. It fails closed with a `StandardSelectionException` when the lifecycle is unverified, the
edition is superseded or not current, the exact kernel is missing, the equipment context is missing,
or the standard is not listed for that equipment type. The exception exposes a machine-readable
reason so applications do not need to parse its message.

Use `StandardSelection.historical(...)` only for a controlled compatibility calculation whose exact
non-current edition is implemented. It retains applicability and kernel checks but makes the
historical basis explicit. `StandardSelection.strictRegistry(...)` is the transitional factory
path; it verifies the current lifecycle, implementation maturity, registry connection, and
applicability without claiming an edition-specific typed kernel.

Executable strict and historical selections reject project amendments until the selected kernel or
requirement pack explicitly implements them. `StandardSelection.legacy(...)` can retain amendment
metadata during migration, but doing so does not apply the amendments to a calculation.

`StandardRegistry.assessApplicability(...)` provides the applicability decision without creating a
standard.

`StandardSelection.legacy(...)` is available for migrations that need an explicit edition while
retaining the permissive factory behavior. It may create a metadata-only `DesignStandard`; it does
not add calculation support.

Process-global `setVersionOverride(...)` and `clearVersionOverrides()` are deprecated. New code
should retain a `StandardEdition` in each `StandardSelection`, then call
`StandardRegistry.requireDesignKernel(selection)` when an executable implementation is required.
The require call distinguishes missing kernel support from an unsupported edition. See the
[typed-kernel migration guide](standard_design_kernel_migration.md) for a staged compatibility path.

## Consolidated design kernels

`EquipmentDesignKernel<I, O>` extends the existing typed engineering-calculation contract with the
implemented standard, audited maturity, and structured applicability. Kernels must not mutate their
input or a `ProcessSystem`. Compatibility adapters defensively copy legacy mutable calculators.

`StandardRegistry.getDesignKernel(...)` returns an explicit lookup status. API 617, API 610, API
521, API 526, and API 12J have connected adapters and return `IMPLEMENTED`; standards that have not
been adapted return `NOT_IMPLEMENTED`, never an empty or implied success. Each kernel returns an
immutable assessment snapshot and always requires engineering review because its maturity remains
`SCREENING`. Unsupported editions fail closed as `EDITION_NOT_IMPLEMENTED` until separately
implemented and validated.

Only API 521 currently has both a publisher-verified current lifecycle and a matching exact kernel.
API 526, API 617, API 610, and API 12J adapters implement legacy edition labels that do not match
the current catalog editions; they are available only through an explicit historical selection
until current-edition kernels and independent benchmark evidence are added.

The API 521 adapter defensively copies the mutable protected-item basis, requires at least one
complete credible scenario, selects the governing rate, and records the sizing and accumulated
pressure checks. The API 526 adapter accepts an explicitly unit-tagged required area and reports an
inadequate result when a single standard orifice cannot cover it. These adapters do not establish
scenario completeness or qualify valve construction, installation, reaction loads, flare-network
effects, or vendor certification.

The API 617 adapter defensively copies a compressor-casing configuration before evaluating pressure
containment, hydrotest, flange-rating, nozzle-load allowance, and thermal-growth screens. The API
12J adapter uses explicitly unit-tagged cut diameter together with K-factor and liquid residence
time. Passing either result is not a package, vessel, or performance certification.

## Cross-equipment requirement packs

Some standards express requirements across equipment, calculations, documents, and lifecycle
activities and cannot honestly be represented by one `DesignStandard` subclass. Immutable
`StandardRequirementPack` records make the existing NeqSim calculation and review capabilities
discoverable without copying licensed standard text or implying complete conformity.

```java
StandardSelection selection = StandardSelection.strictRequirements(StandardType.NORSOK_P_002);
StandardRequirementPack requirements = StandardRegistry.requireRequirementPack(selection);
```

Each capability declares whether it is a calculation screen or a review workflow, its implementation
class, and its engineering boundary. Packs currently map NORSOK P-002, NORSOK S-001, NORSOK M-001,
API 520 Part 1, API 650, API 660, DNV-ST-F101, ISO 10418, IEC 61511, and IEC 60534. Strict lookup
still fails when the lifecycle is unverified; IEC 60534 therefore remains discoverable in the audit
but cannot be selected as a verified current requirements basis.

Requirement packs are navigation and orchestration metadata. They do not contain publisher
requirements, prove coverage, execute every mapped capability, or replace a project requirements
register and independent conformity assessment.

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
- Use the [standards implementation program](process_design_standards_program.md) to prioritize
  current-edition kernels, benchmark evidence, and requirement coverage.

See the [engineering capability statement](../engineering/current-capabilities.md) for the broader
process-to-engineering workflow and its qualification boundary.
