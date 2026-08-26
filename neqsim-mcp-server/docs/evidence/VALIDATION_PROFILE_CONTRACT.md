# Phase 0 validation-profile contract evidence

This note records bounded Phase 0 software-contract evidence for the existing `manageValidationProfile` MCP tool. It does not promote the tool's trust classification and it is not scientific, regulatory, authorization, or plant-control validation.

## Existing implementation boundary

`ValidationProfileRunner` exposes eight actions: `listProfiles`, `getProfile`, `setActiveProfile`, `createProfile`, `deleteProfile`, `validateWithProfile`, `getActiveProfile`, and `getStandardsForEquipment`.

The runner has five built-in profiles (`ncs`, `ukcs`, `gom`, `brazil`, and `generic`) plus process-local custom profiles. Custom profiles are stored in memory, the active profile is process-local mutable state, deleting the active custom profile recovers to `generic`, built-in profiles cannot be deleted, and unknown profile/action requests return structured error entries.

`IndustrialProfile` classifies `manageValidationProfile` as an experimental `PLATFORM` tool. It is therefore available in the default single-engineer desktop profile but blocked from deployment modes that exclude experimental/platform operations. This evidence does not relax those governance rules.

## Focused contract evidence

- `src/test/java/neqsim/mcp/runners/ValidationProfileRunnerTest.java` checks built-in discovery, profile details, equipment-standard retrieval, a create/activate/read/delete custom-profile lifecycle with state restoration, and fail-closed mutation errors.
- `neqsim-mcp-server/test_validation_profile_protocol.py` starts the packaged MCP server over STDIO and repeats the bounded discovery/lifecycle/fail-closed contract through real `tools/call` requests.
- `.github/workflows/mcp_protocol_qualification.yml` builds the exact NeqSim core and MCP package with Java 21 and executes the focused protocol qualification on relevant PRs and `master` pushes with read-only repository permissions.

The packaged qualification uses an isolated synthetic profile name and restores/deletes its process-local state before server shutdown. It does not persist a profile to disk, write to a plant system, or alter another client or tenant.

## Evidence that is not established

This contract evidence does **not** establish that the named standards are complete, current, legally applicable, licensed for redistribution, or sufficient for a particular facility or jurisdiction. It does not certify any engineering result produced by `validateWithProfile`, prove external identity or authorization, prove cross-process durability, prove multi-tenant isolation for a deployment, or grant accountable engineering approval.

The `manageValidationProfile` trust record remains unchanged until the machine-readable Phase 0 inventory and the primary packaged-protocol accounting are updated atomically on one validated exact head. Existing `BenchmarkTrust` numerical/scientific accounting is unaffected.

## Owner-roadmap boundary

No thermodynamic, process, pipeline, dynamics, DEXPI/P&ID, production-optimization, schema, response-envelope, or live-control behavior changes in this evidence increment. The canonical NeqSim model and all specialist owner roadmaps remain authoritative.
