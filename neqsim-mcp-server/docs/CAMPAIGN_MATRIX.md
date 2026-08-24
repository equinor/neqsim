# MCP campaign traceability and maturity matrix

This document is the human-readable companion to `getCapabilities.phase0EvidenceInventory.campaignMatrix` for campaign #3153. The machine-readable matrix enumerates all 66 roadmap criteria from Phases 0–10 and classifies each as `MERGED_EVIDENCE`, `PARTIAL_EVIDENCE`, or `CONFIRMED_GAP`.

The matrix is deliberately conservative. It does not turn runtime availability, an existing Java class, a tool registration, or an open pull request into a completion or validation claim. Roadmap completion remains based on verified merged current-`master` evidence.

## Evidence-state semantics

| State | Meaning |
| --- | --- |
| `MERGED_EVIDENCE` | Merged current-tree source, tests, documentation, or campaign evidence directly supports the criterion. |
| `PARTIAL_EVIDENCE` | Relevant capability exists, but one or more requirements remain unproven, incomplete, or require merged-master audit. |
| `CONFIRMED_GAP` | Current evidence does not demonstrate the criterion. The gap remains explicit. |

Phase 0 itself is not self-certified by this document. The criterion-traceability and discipline-maturity rows remain `PARTIAL_EVIDENCE` until the implementation is merged and re-audited on `master`. The acceptance-baseline criterion also remains partial because the current MCP responses do not expose explicit numeric mass/component/energy closure for every fixture; the baseline harness records that as a named gap rather than inferring closure.

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

After this matrix is merged and re-audited on current `master`, Phase 0 still retains explicit work where the matrix says `PARTIAL_EVIDENCE` or `CONFIRMED_GAP`. The most immediate evidence gap exposed by the four-scale harness is explicit numeric balance closure in MCP responses. Confirmed per-tool trust gaps also remain until supported by defensible source and benchmark evidence.

No matrix row should be promoted merely because a tool executes. Scientific applicability, numerical convergence, provenance, units/bases, limitations, and owner-roadmap validation remain authoritative.
