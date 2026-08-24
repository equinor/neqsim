# MCP campaign traceability and maturity matrix

This document is the human-readable companion to `getCapabilities.phase0EvidenceInventory.campaignMatrix` for campaign #3153. The machine-readable matrix enumerates all 66 roadmap criteria from Phases 0–10 and classifies each as `MERGED_EVIDENCE`, `PARTIAL_EVIDENCE`, or `CONFIRMED_GAP`.

The matrix is deliberately conservative. It does not turn runtime availability, an existing Java class, a tool registration, or an open pull request into a completion or validation claim. Roadmap completion remains based on verified merged current-`master` evidence.

## Evidence-state semantics

| State | Meaning |
| --- | --- |
| `MERGED_EVIDENCE` | Merged current-tree source, tests, documentation, or campaign evidence directly supports the criterion. |
| `PARTIAL_EVIDENCE` | Relevant capability exists, but one or more requirements remain unproven, incomplete, or require merged-master audit. |
| `CONFIRMED_GAP` | Current evidence does not demonstrate the criterion. The gap remains explicit. |

The criterion-traceability and engineering-discipline maturity implementations are now merged on current `master` through #3203 (`364e8e72729c952a111d503d3aff38485c3b808a`) and are therefore recorded as `MERGED_EVIDENCE`. This does not make Phase 0 complete. The acceptance-baseline criterion remains `PARTIAL_EVIDENCE`: merged #3209 exposes solver-native multi-area `ProcessModel` mass closure, and merged #3218 exposes canonical per-unit `ProcessSystem` mass-balance evidence, but explicit component closure, energy closure, and complete facility-wide single-area feed/export closure remain gaps. The baseline harness records these limitations rather than inferring conservation from successful execution.

## Discipline maturity

The matrix groups representative published tools into ten engineering disciplines:

1. thermodynamics and PVT;
2. process simulation and facility studies;
3. flow assurance;
4. rotating equipment;
5. separation, treating and columns;
6. reservoir, wells and production technology;
7. dynamics and controls;
8. safety, integrity and materials;
9. engineering data, diagrams and interoperability;
10. reporting, lifecycle and governance.

For each discipline, maturity is derived from the actual published tool set and `BenchmarkTrust`:

- `TOOL_SPECIFIC_TRUST`: every published representative tool has tool-specific trust metadata;
- `PARTIAL_TOOL_SPECIFIC_TRUST`: some, but not all, published representative tools have tool-specific trust metadata;
- `CONFIRMED_TRUST_GAP`: none of the published representative tools have tool-specific trust metadata.

This is a discovery/evidence classification, not a discipline qualification. Every discipline row therefore explicitly reports `qualifiedForAccountableEngineeringApproval=false`.

## Ownership boundaries

The matrix preserves the campaign coordination boundaries rather than absorbing other roadmaps. Generic TP-flash internals remain #2937-owned; dynamic-engine behavior remains #2911-owned; DEXPI/P&ID semantics remain #2899-owned; process-execution performance remains #2939-owned; plant-wide optimization fidelity remains #3154/#2941-owned; column and reactive-absorber internals remain #2936/#205-owned; and pipeline solver physics remains with the relevant pipeline roadmaps.

The MCP campaign owns the transport, discovery, orchestration, evidence, bounded execution, result-delivery, and agent-facing contracts around those capabilities.

## Remaining Phase 0 work

Current-master re-audit confirms the traceability and discipline-maturity criteria as merged evidence. The acceptance-baseline criterion remains partial because the canonical response evidence does not yet establish component balance, energy balance, or complete facility-wide single-area feed/export closure. Current source inspection found `HeatMaterialBalance` stream/equipment reporting but no independent canonical component- or energy-closure API suitable for promotion through MCP without reconstructing a second balance calculation; those gaps therefore remain explicit.

Confirmed per-tool trust gaps also remain until supported by defensible source and benchmark evidence. No matrix row should be promoted merely because a tool executes. Scientific applicability, numerical convergence, provenance, units/bases, limitations, and owner-roadmap validation remain authoritative.
