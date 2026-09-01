# Phase 0 runtime API-inspection contract evidence

This note records bounded Phase 0 evidence for the existing `inspectApi` MCP tool. Inventory version 1.15 promotes the tool to `CONTRACT_TESTED` after its machine-readable coverage record, focused packaged-MCP qualification, and primary packaged-protocol accounting were aligned on the same branch head. This is a software-contract classification, not a scientific benchmark or an engineering-accuracy claim.

## Evidence currently present

- `src/main/java/neqsim/mcp/runners/ApiKnowledgeRunner.java` performs version-matched reflection against the running NeqSim artifact. Resolution is restricted to fully qualified `neqsim.*` classes, a small explicit common-class map, and aliases accepted by the canonical `EquipmentFactory`.
- `src/test/java/neqsim/mcp/runners/ApiKnowledgeRunnerTest.java` proves representative equipment-alias and common-process-class resolution, member filtering, source-path reporting, and fail-closed rejection of `java.lang.Runtime`.
- `neqsim-mcp-server/src/main/java/neqsim/mcp/server/NeqSimTools.java` exposes the existing MCP `inspectApi(className, memberFilter)` facade through normal tool-access enforcement and the standard response envelope.
- `neqsim-mcp-server/test_inspect_api_protocol.py` starts the packaged server over STDIO and directly calls `inspectApi` through `tools/call`. It requires `ProcessModel` to resolve to the exact runtime class with a filtered public `run` method and source pointer, requires `java.lang.Runtime` to fail closed with the accepted `neqsim.*` namespace boundary, and freezes the promoted 20/10/41 Phase 0 accounting.
- `neqsim-mcp-server/test_mcp_server.py` independently freezes the complete 71-tool publication surface and now includes `inspectApi` in the ten bounded non-numerical `CONTRACT_TESTED` tools while requiring 41 remaining confirmed gaps.
- `.github/workflows/mcp_protocol_qualification.yml` builds the exact NeqSim core and MCP uber-jar on pull requests and `master`, then runs the focused dependency-free protocol harness. It has read-only repository permissions.

## Qualification boundary

The evidence supports class and public-method discovery only. It does not execute the inspected method, prove that a returned API is suitable for a particular engineering calculation, validate thermodynamic/process behavior, establish source-document completeness, or authorize arbitrary JVM reflection. Non-NeqSim targets remain outside the accepted resolution boundary.

`inspectApi` is therefore `CONTRACT_TESTED` in inventory version 1.15, with Phase 0 accounting at `20 EXPLICIT_TRUST + 10 CONTRACT_TESTED + 41 CONFIRMED_GAP`. The underlying `BenchmarkTrust` registry is unchanged: this promotion adds no numerical validation case, accuracy bound, maturity claim, or scientific trust page.

## Atomic promotion gate

The source-side atomic gate is complete on this branch: the machine-readable `inspectApi` record, focused packaged-MCP qualification, authoritative primary protocol accounting, acceptance-baseline inventory version, and evidence documentation all describe the same 1.15 / 20/10/41 state. Exact-head hosted validation is still required before the branch can be classified READY TO MERGE; no unrun check is implied to have passed.

## Owner-roadmap and safety boundary

This evidence work changes no thermodynamic model, process calculation, pipeline/dynamics solver, DEXPI/P&ID semantics, production optimization, tool input/output schema, deployment profile, security policy, plant data, or control path. `inspectApi` remains advisory discovery only and never constitutes accountable engineering approval.
