# Standards and applicability

Catalogue metadata checked 2026-10-25. This is an applicability guide, not a set
of assessed requirements. Recheck before issue. Public metadata establishes a
title, edition or scope; it does not establish clause compliance or access to
licensed text. Preserve the process report's standards register and verification
method; resolve differences explicitly rather than silently replacing its basis.

## Select the governing basis

Record jurisdiction, facility type, project phase, adopted regulation, contract
requirements and company requirements. A newer catalogue edition does not
automatically replace the legally or contractually adopted edition. Offshore
NORSOK scope does not automatically make a standard mandatory for an onshore plant.
Resolve conflicts with the responsible discipline; do not invent precedence.

For each applied reference retain: designation, adopted edition and amendments,
catalogue edition/status, source URL, date checked, applicability and rationale,
mandatory/recommended/informative classification, text availability, assessed
clause, paraphrased requirement, criterion/unit, case IDs, calculation/drawing
evidence, assessment status, deviations and responsible reviewer.

Use requirement statuses `met`, `partially met`, `not met`, `not applicable` and
`not assessed`, consistent with the process report. Keep evidence level separately:
`metadata-verified`, `clause-verified`, `project-assumption` or
`verification-pending`. A metadata-only row remains `not assessed` for compliance.
Do not copy copyrighted clauses into public examples.

## Main process-report standards

| Reference | Application and evidence boundary | Official metadata |
|---|---|---|
| NORSOK P-002:2023+AC:2024 | Offshore process, support and utility systems; consolidated corrigendum is listed. Establish project adoption and read applicable clauses before claiming compliance. | [Standards Norway](https://standard.no/en/sectors/petroleum/norsok-standards/p-process/) |
| ISO 10628-1:2014 and ISO 10628-2:2012 | Process diagram content and graphical symbols. Do not apply as an electrical drawing code. Existing report symbols follow conventions; they are not certified conforming symbols. | [Part 1](https://www.iso.org/standard/51840.html), [Part 2](https://www.iso.org/standard/51841.html) |
| ISO 14617 series | Use the applicable symbol parts. ISO 14617-1:2025 supersedes the 2005 edition previously listed in the process-report guidance; record the chosen edition and drawing-library assessment explicitly. | [Part 1](https://www.iso.org/standard/85641.html) |
| IEC 62424 and DEXPI | P&ID/control representation and optional data exchange, respectively. DEXPI is an exchange specification, not an ISO standard. Verify adopted versions via the existing standards-lookup workflow. | Use process-report register and issuing-body lookup |

## Utility-specific selection

These are candidate references, not blanket mandatory requirements. Where no
edition is stated, verify it before adding the reference to an issued register.

| System | Candidate basis | What the report must establish |
|---|---|---|
| Instrument/service air | [ISO 8573-1:2010](https://www.iso.org/standard/46418.html); [ISA-7.0.01-1996](https://www.isa.org/products/isa-7-0-01-1996-quality-standard-for-instrument-ai) | Required particles/water/oil classes and test basis; dew point at stated pressure; end-user conditions and loss-of-air behavior. Neither catalogue establishes one universal receiver duration or header pressure. |
| Utility piping | [ASME B31.3:2024](https://www.asme.org/codes-standards/find-codes-standards/b31-3-process-piping), where adopted; applicable power-piping code at boiler/power boundaries | Code boundaries, operating/design envelopes, hydraulic criteria and mechanical hand-off. A hydraulic calculation alone is not code mechanical verification. |
| Boilers, receivers and pressure vessels | Adopted pressure-equipment law and [ASME BPVC](https://www.asme.org/codes-standards/bpvc-standards), e.g. Sections I/VIII where applicable | Applicable equipment category/code, pressure/temperature envelope, relief and inspection responsibility. Catalogue lists 2025 BPVC; verify adopted edition. |
| Cooling/heating | [ISO 13706:2011](https://www.iso.org/standard/45638.html), [ISO 15547-1:2005](https://www.iso.org/standard/37293.html), [ISO 13709:2009](https://www.iso.org/standard/41612.html), for applicable equipment | Air-cooler/exchanger/pump specification, vendor rating, ambient/fouling/materials and terminal performance. Source water chemistry, boiler-water and steam quality from applicable project/OEM requirements. |
| Fuel gas | Adopted piping/safety basis plus burner/turbine OEM specification | Composition envelope, LHV/HHV/Wobbe definition, reference conditions, pressure, contaminant limits and liquid/dew-point margin over all cases. No universal H2S or Wobbe limit is imposed here. |
| Nitrogen/LIN | Adopted gas safety basis, [NFPA 55](https://www.nfpa.org/es/product/id/p0055code), [ISO 21009-1:2022](https://www.iso.org/standard/76098.html) for applicable cryogenic vessels | Service-specific oxygen/purity target, purge model, usable inventory, vent routes and oxygen-deficiency assessment. Verify effective edition: catalogue already advertises NFPA 55:2027; an ISO 21009-1 replacement is in development. |
| Relief/depressuring/flare | API 520, [API 521](https://www.api.org/products-and-services/standards/important-standards-announcements/standard521), [API 537](https://www.apiwebstore.org/standards/537), where applicable | Scenario-specific coincident loads, relief-device and disposal-system checks. API 537 catalogue lists edition 4 with addenda; verify all adopted amendments. API 521 excludes direct-fired steam boilers. |
| Firewater | Adopted fire strategy; [NORSOK S-001](https://standard.no/en/sectors/petroleum/norsok-standards/s-safety-she/) offshore where adopted; [NFPA 20](https://www.nfpa.org/codes-and-standards/nfpa-13-standard-development/20), [NFPA 22](https://www.nfpa.org/codes-and-standards/nfpa-22-standard-development/22?l=220), [NFPA 24](https://link.nfpa.org/all-publications/24/2025) where adopted | Fire scenarios, coincident deluge/hydrant demands, duration, pump availability and terminal hydraulic pressure. Do not infer demand from normal process flow. |
| Utility loss and safeguards | NORSOK S-001 where adopted; [IEC 61511-1](https://webstore.iec.ch/en/publication/24241) for SIS interfaces | Common-cause dependencies, safe-state response and credited independence. Equipment count does not establish SIL or independence. |
| Electrical interfaces | [IEC 61892-1:2019](https://webstore.iec.ch/en/publication/28085), [IEC 61892-7:2019](https://webstore.iec.ch/en/publication/27009) offshore as applicable; jurisdictional onshore electrical basis | Consumer/load, starting, emergency-power and hazardous-area interface schedules. Electrical installation/protection design remains a discipline hand-off. |
| Water, drains and discharges | Adopted P-002/safety basis, site water specification and actual environmental permits | Clean/contaminated and open/closed segregation, backflow prevention, surge and discharge limits. A general process standard is not discharge permission. |

Do not cite NORSOK U-001 as a utilities standard: it covers
[subsea production systems](https://standard.no/en/sectors/petroleum/norsok-standards/u-subsea/).
Do not inherit fixed margins, air dew points, purge volumes or backup arrangements
from older illustrative skill tables as normative requirements. Trace every
acceptance limit to the adopted project requirement or a clause actually read.

## Claim gate

Catalogue-only references may support 'design basis references' or 'conventions
used', not 'complies with'. List each unassessed mandatory requirement in the
open-action register and restrict readiness accordingly. An applied requirement
passes only when the assessed text, applicable case, numerical criterion and
reviewable evidence are all identified. Do not turn `not assessed` into `met`
because another report passed its separate validation.
