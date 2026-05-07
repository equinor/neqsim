---
title: "Barrier Management and SCE Traceability"
description: "Guide to evidence-linked barrier management in NeqSim. Covers PSFs, SCEs, performance standards, document evidence, and handoffs to LOPA, SIL, bow-tie, and QRA workflows."
---

# Barrier Management and SCE Traceability

NeqSim supports a traceable barrier-management workflow for technical safety and process-safety studies. The core idea is to keep the calculation inputs tied to the technical documents that justify them: P&IDs, C&E charts, safety requirement specifications, SIL verification reports, inspection reports, vendor datasheets, and operating procedures.

The workflow is intended for agent-assisted engineering where a document-reading agent extracts evidence and a safety runner turns that evidence into auditable inputs for LOPA, SIL, bow-tie, QRA, and layout safety reviews.

## Data Model

| Object | Purpose | Typical source |
|--------|---------|----------------|
| `DocumentEvidence` | Stores document id, revision, source reference, excerpt, and confidence. | P&ID tag, table row, SRS clause, inspection finding |
| `PerformanceStandard` | Defines the required PSF/SCE performance, such as target PFD, availability, proof-test interval, response time, and acceptance criteria. | SRS, performance standard, SIL report |
| `SafetyBarrier` | Represents a preventive, mitigative, or combined barrier with status, PFD/effectiveness, owner, equipment tags, hazard ids, and evidence. | Bow-tie, HAZOP action register, C&E, SIL report |
| `SafetyCriticalElement` | Groups barriers under an SCE and links them to process equipment tags. | SCE register, safety strategy, P&ID |
| `BarrierRegister` | Top-level register for validation, audit export, and safety-analysis handoff. | Compiled study register |

## MCP Workflow

Use `runBarrierRegister` when you have extracted or authored a barrier register in JSON. The tool returns:

- `summary`: counts of SCEs, barriers, available barriers, impairments, evidence, and performance standards.
- `validation`: findings with remediation hints for missing evidence, missing performance standards, unknown status, missing equipment tags, and unlinked barriers.
- `lopaHandoff`: protection layers with PFD and RRF values, excluding barriers that are unavailable or lack traceable evidence/performance standards.
- `silHandoff`: SIF-like candidates with target PFD, claimed PFD, achieved SIL band, proof-test interval, response time, and evidence summary.
- `bowTieHandoff`: barrier records ready to copy into bow-tie workflows.
- `qraHandoff`: transparent screening multipliers grouped by hazard id for event-tree or QRA review.
- `registerExport`: normalized JSON export of the full register.

## Input Pattern

Get the full validated template from the example catalog:

```json
{
  "category": "safety",
  "name": "barrier-register"
}
```

The essential input shape is:

```json
{
  "action": "audit",
  "register": {
    "registerId": "BR-HP-SEP-001",
    "name": "HP separator overpressure barrier register",
    "evidence": [
      {
        "evidenceId": "EV-SRS-001",
        "documentId": "SRS-HP-101",
        "documentTitle": "HIPPS safety requirements specification",
        "revision": "1",
        "section": "Performance requirements",
        "page": 12,
        "sourceReference": "SIF-HIPPS-101 table",
        "excerpt": "SIF-HIPPS-101 shall achieve PFDavg <= 1E-3.",
        "confidence": 0.95
      }
    ],
    "performanceStandards": [
      {
        "id": "PS-HIPPS-101",
        "title": "HIPPS overpressure protection",
        "safetyFunction": "Prevent HP separator overpressure from blocked outlet",
        "demandMode": "LOW_DEMAND",
        "targetPfd": 0.001,
        "requiredAvailability": 0.99,
        "proofTestIntervalHours": 8760,
        "responseTimeSeconds": 2.0,
        "acceptanceCriteria": [
          "Close inlet ESD valve before separator MAWP is exceeded"
        ],
        "evidenceRefs": ["EV-SRS-001"]
      }
    ],
    "barriers": [
      {
        "id": "B-HIPPS-101",
        "name": "HIPPS inlet shutdown",
        "type": "PREVENTION",
        "status": "AVAILABLE",
        "pfd": 0.001,
        "performanceStandardId": "PS-HIPPS-101",
        "equipmentTags": ["V-101", "ESDV-101"],
        "hazardIds": ["HAZ-OP-001"],
        "evidenceRefs": ["EV-SRS-001"]
      }
    ],
    "safetyCriticalElements": [
      {
        "id": "SCE-V-101",
        "tag": "V-101",
        "name": "HP separator pressure protection",
        "type": "PROCESS_EQUIPMENT",
        "equipmentTags": ["V-101"],
        "barrierRefs": ["B-HIPPS-101"],
        "evidenceRefs": ["EV-SRS-001"]
      }
    ]
  }
}
```

## Agent Extraction Guidance

When agents read technical documentation, extract evidence before assigning numerical credit. A claimed PFD or effectiveness should be traceable to a document excerpt, table, standard, vendor certificate, or explicit engineering assumption. If the source is uncertain, set a lower `confidence` and allow `validation.findings` to carry the uncertainty into the review.

Recommended extraction sequence:

1. Identify SCEs and equipment tags from P&IDs, safety strategies, and SCE registers.
2. Extract PSF/SIF performance standards from SRS, SIL verification, C&E, and operations documents.
3. Extract barriers, status, owners, and tags from HAZOP/HAZID, bow-tie, C&E, and inspection sources.
4. Link every quantitative claim with `evidenceRefs`.
5. Run `runBarrierRegister` before using the barriers in LOPA, SIL, bow-tie, or QRA calculations.

## Review Rules

A barrier should not be credited in LOPA unless it is available, has a valid PFD, has a linked performance standard, and has traceable document evidence. The runner returns excluded barriers with reasons so reviewers can close the evidence gaps instead of silently losing safety credit.

QRA multipliers from `qraHandoff` are screening values. They should be reviewed in an event-tree model before use in final individual-risk or societal-risk reporting.

## Related Documentation

- [Safety Systems](README.md)
- [HAZOP Worksheet](HAZOP.md)
- [Dispersion and Consequence Analysis](dispersion_and_consequence.md)
- [Depressurization per API 521](depressurization_per_API_521.md)
