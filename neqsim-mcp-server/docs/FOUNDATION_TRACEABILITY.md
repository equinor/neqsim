# MCP Phase 0 merged-foundation traceability

This document reconciles the three merged foundations named by campaign #3153 against the current MCP implementation. It records what each prerequisite established, representative current source and regression evidence, and the boundary that remains. It is evidence metadata, not a second simulator, a new public MCP tool, or an engineering-validation claim.

The same structured reconciliation is exposed under `getCapabilities.phase0EvidenceInventory.mergedFoundations` so protocol clients can discover it without scraping pull-request history.

| Foundation | Merged capability | Representative current source | Regression evidence | Remaining boundary |
| --- | --- | --- | --- | --- |
| #2874 (`0894b7820b6317c64ccaaaee5a3326f5bbdf5d77`) | Caller identity, recoverable security enforcement, principal-scoped approvals, fail-closed privileged actions | `McpRequestContext`, `SecurityRunner`, `IndustrialProfile`, server `McpIdentityResolver` | `McpSecurityEnforcementTest` | Security is disabled by default for local desktop use; governed deployments still require transport identity and configured admin policy. |
| #2875 (`7dac75744ebf25cfbe2b4ccd763bb30c3d14cbdf`) | Tenant-scoped model handles, solved-model reuse, response-size protection, execution bounds, complete catalog coverage | `ModelRegistry`, `ResponseSizeGuard`, `McpExecutionPolicy`, `CapabilitiesRunner` | `ModelRegistryTest`, `ResponseSizeGuardTest`, `McpToolSurfaceContractTest` | Bounded execution and retrieval are operational safeguards, not proof of scientific accuracy for every tool. |
| #3152 (`bd07729f105efb48b14c641697e0f99fe9af6898`) | Runtime capability discovery/execution, canonical replayable `ProcessSystem`/`ProcessModel` definitions, design/capacity evidence, typed two-fluid results | `GeneralCapabilityRunner`, `ProcessRunner`, `JsonProcessBuilder`, `TwoFluidPipeResponse` | `CapabilitiesRunnerTest`, `ProcessRunnerTest` | Generic execution remains narrower than discovery; stateful work stays behind curated runners and domain validation remains authoritative. |

## What this completes

This closes the narrow Phase 0 reconciliation dependency: the campaign no longer has to infer the purpose of #2874, #2875, and #3152 from historical PR prose alone. Their durable capability contracts and representative source/test locations are now discoverable from the running capability manifest.

The reconciliation is intentionally conservative:

- a merged foundation is recorded as a software contract, not as universal validation of every downstream calculation;
- current source and tests remain authoritative when historical PR prose and current implementation differ;
- per-result provenance, convergence, warnings, assumptions, validation maturity, and limitations remain authoritative for engineering use;
- DEXPI/P&ID remains owned by #2899, dynamics by #2911, flash/stability/performance by #2937, generic process performance by #2939, and production optimization by #2941/#3154.

## Current Phase 0 trust evidence

Every published tool has an explicit coverage record under `phase0EvidenceInventory.knownLimitations.coverageRecords`.

- 20 tools have tool-specific `BenchmarkTrust` pages and remain `EXPLICIT_TRUST`.
- `getCapabilities`, `getSchema`, `getExample`, `getBenchmarkTrust`, `checkToolAccess`, `manageIndustrialProfile`, `searchComponents`, `queryDataCatalog`, and `getProgress` are `CONTRACT_TESTED`: they are non-numerical discovery, catalog, lookup, progress-retrieval, trust-retrieval, and governance contracts with exact source/test/protocol evidence, so an engineering-accuracy benchmark is not applicable.
- 42 tools remain `CONFIRMED_GAP` and must not inherit scientific validation from the generic `TESTED` compatibility fallback.

The underlying `BenchmarkTrust` registry itself is intentionally unchanged at 20 explicit pages and 51 generic fallbacks. Contract-tested catalog/discovery/progress evidence is a separate Phase 0 classification and does not certify any thermodynamic, process, pipeline, dynamic, safety, or optimization calculation advertised by those surfaces.

The discovery/catalog classification is backed by current source plus the packaged MCP protocol suite: all 142 canonical input/output schema resources resolve as JSON schema objects, all 114 example-catalog entries resolve through MCP resources, component search covers exact and partial matches, empty enumeration and no-match behavior through the public MCP tool, and read-only data-catalog calls exercise component-family and EOS-model discovery. `CapabilitiesRunnerTest` cross-checks advertised schemas/examples against the current catalogs, while `ComponentQueryTest` and `DataCatalogRunnerTest` directly exercise the two promoted lookup implementations. The trust/governance classification additionally uses the complete `IndustrialProfileTest` access matrix, fail-closed security and principal-scoping regressions, and real-protocol trust/profile/access calls. Exact paths are carried in each record's `contractEvidenceSources` array. These are software-policy and transport-contract checks, not validation of database contents, EOS accuracy, standards applicability, scientific trust-page claims, external authorization, or a configured facility deployment.

Inventory version `1.12` promotes `getProgress` using the bounded `ProgressTracker` implementation, the focused lifecycle regression in `McpEvidenceInventoryFoundationTests`, the existing real-MCP `getProgress(action=listActive)` call, and `docs/evidence/PROGRESS_RETRIEVAL_CONTRACT.md`. Its machine-readable coverage record and packaged protocol expectation moved together on the same exact head, changing the Phase 0 coverage accounting from 20/8/43 to 20/9/42. The evidence is deliberately limited to active-operation discovery, point retrieval, milestone visibility, completion state, missing-operation errors, and real-protocol list retrieval; it does not validate the underlying calculation, convergence, cancellation, durability, deployment isolation, authorization, or plant authority.

Inventory version `1.13` qualifies `inspectApi` as the next bounded non-numerical promotion candidate without changing its current `CONFIRMED_GAP` status or the 20/9/42 accounting. `ApiKnowledgeRunner` restricts reflection to `neqsim.*`, explicit common NeqSim classes, and canonical `EquipmentFactory` aliases; `ApiKnowledgeRunnerTest` verifies representative alias/class resolution, member filtering/source pointers, and fail-closed rejection of `java.lang.Runtime`; and the existing `NeqSimTools` facade preserves normal access enforcement and response standardization. The remaining gate is direct execution through the packaged real-MCP protocol plus an atomic classification-accounting update on that same validated head. `docs/evidence/API_INSPECTION_CONTRACT.md` records the detailed boundary.

## Remaining Phase 0 work

The four public synthetic acceptance scales, bounded baseline harness, 66-criterion campaign traceability matrix, and ten-discipline maturity matrix are already merged and discoverable. Phase 0 is still incomplete because the acceptance baseline retains explicit component, energy, and complete facility-wide single-area closure gaps, and 42 published tools still lack a defensible tool-specific or bounded non-numerical trust classification.

Complete the explicit `inspectApi` transport gate before promoting it. Then audit the remaining trust gaps against current source and promote only where concrete tests, public benchmark evidence, authoritative data, or a clearly non-numerical contract supports the classification. Do not manufacture accuracy bounds for discovery/catalog tools and do not reconstruct a second MCP-side conservation model when canonical NeqSim does not expose independent evidence.

This document and the `mergedFoundations` object therefore must not be interpreted as campaign completion, plant authority, design certification, or accountable engineering approval.
