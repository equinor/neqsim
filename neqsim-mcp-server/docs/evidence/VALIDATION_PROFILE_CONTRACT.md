# Phase 0 validation-profile contract evidence

This note records bounded Phase 0 software-contract evidence for the existing `manageValidationProfile` MCP tool. Inventory `1.16` now records it as a promotion-ready `CONTRACT_TESTED` candidate while deliberately retaining the current `CONFIRMED_GAP` coverage status until primary protocol accounting and coverage change atomically. This is not scientific, regulatory, authorization, or plant-control validation.

## Existing implementation boundary

`ValidationProfileRunner` exposes eight actions: `listProfiles`, `getProfile`, `setActiveProfile`, `createProfile`, `deleteProfile`, `validateWithProfile`, `getActiveProfile`, and `getStandardsForEquipment`.

The runner has five built-in profiles (`ncs`, `ukcs`, `gom`, `brazil`, and `generic`) plus process-local custom profiles. Custom profiles are stored in memory, the active profile is process-local mutable state, deleting the active custom profile recovers to `generic`, built-in profiles cannot be deleted, and unknown profile/action requests return structured error entries.

`IndustrialProfile` classifies `manageValidationProfile` as an experimental `PLATFORM` tool. It is therefore available in the default single-engineer desktop profile but blocked from deployment modes that exclude experimental/platform operations. This evidence does not relax those governance rules.

## Focused contract evidence

- `src/test/java/neqsim/mcp/runners/ValidationProfileRunnerTest.java` checks built-in discovery, profile details, equipment-standard retrieval, the `validateWithProfile` response shape and profile/standards/design-factor metadata, a create/activate/read/delete custom-profile lifecycle with state restoration, and fail-closed mutation errors.
- `neqsim-mcp-server/test_validation_profile_protocol.py` starts the packaged MCP server over STDIO and repeats the bounded discovery, validation-metadata, lifecycle, fail-closed, and promotion-candidate contracts through real `tools/call` requests.
- `.github/workflows/mcp_protocol_qualification.yml` builds the exact NeqSim core and MCP package with Java 21 and executes the focused protocol qualification on relevant PRs and `master` pushes with read-only repository permissions.
- Merged PR #3266 established this Java and packaged-MCP evidence on exact validated head `a7d6944621b9abc01b3ea221208f18228b360b76` before any trust-status promotion was attempted.

The packaged qualification uses an isolated synthetic profile name and restores/deletes its process-local state before server shutdown. It does not persist a profile to disk, write to a plant system, or alter another client or tenant.

The `validateWithProfile` checks are intentionally structural only: they prove that the underlying validator verdict remains visible and that the selected profile name, applicable-standards array, and required-design-factor object survive the runner and MCP response path. They do not assert that any listed standard, threshold, factor, or engineering verdict is correct for a real facility.

## Promotion boundary

The machine-readable candidate targets `CONTRACT_TESTED` with `benchmarkApplicability=NOT_APPLICABLE_NON_NUMERICAL_VALIDATION_PROFILE_GOVERNANCE` and cites the runner, deployment-profile classification, Java regression suite, MCP facade, real packaged-protocol harness, and this evidence note. `promotionReady=true` means only that the bounded software-contract prerequisite is assembled.

The current accounting remains **20 `EXPLICIT_TRUST` + 10 `CONTRACT_TESTED` + 41 `CONFIRMED_GAP`**. `manageValidationProfile` itself remains `CONFIRMED_GAP` in that accounting. A later promotion must update the primary packaged-protocol classification accounting and machine-readable coverage status together on one exact validated head; no intermediate state may advertise 20/11/40 inconsistently.

## Evidence that is not established

This contract evidence does **not** establish that the named standards are complete, current, legally applicable, licensed for redistribution, or sufficient for a particular facility or jurisdiction. It does not certify any engineering result produced by `validateWithProfile`, prove external identity or authorization, prove cross-process durability, prove multi-tenant isolation for a deployment, or grant accountable engineering approval.

Existing `BenchmarkTrust` numerical/scientific accounting is unaffected.

## Owner-roadmap boundary

No thermodynamic, process, pipeline, dynamics, DEXPI/P&ID, production-optimization, schema, response-envelope, or live-control behavior changes in this evidence increment. The canonical NeqSim model and all specialist owner roadmaps remain authoritative.
