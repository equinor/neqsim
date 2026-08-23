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
- DEXPI/P&ID remains owned by #2899, dynamics by #2911, flash/stability/performance by #2937, and production optimization by #2941.

## Remaining Phase 0 work

The full Phase 0 audit is still incomplete. The next dependency is to turn the current generic benchmark-trust fallbacks into evidence-backed capability records or explicit confirmed gaps, then define the four acceptance scales and the campaign traceability/maturity matrices. Measured runtime, memory, payload size, convergence, balance closure, and report-usefulness baselines also remain to be frozen.

This document and the `mergedFoundations` object therefore must not be interpreted as campaign completion or as accountable engineering approval.
