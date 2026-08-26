# Phase 0 runtime API-inspection contract evidence

This note records bounded Phase 0 evidence for the existing `inspectApi` MCP tool. Inventory version 1.14 closes the transport-evidence prerequisite and marks the candidate promotion-ready; it does **not** change the tool's `CONFIRMED_GAP` coverage status in this increment and it is not a scientific benchmark.

## Evidence currently present

- `src/main/java/neqsim/mcp/runners/ApiKnowledgeRunner.java` performs version-matched reflection against the running NeqSim artifact. Resolution is restricted to fully qualified `neqsim.*` classes, a small explicit common-class map, and aliases accepted by the canonical `EquipmentFactory`.
- `src/test/java/neqsim/mcp/runners/ApiKnowledgeRunnerTest.java` proves representative equipment-alias and common-process-class resolution, member filtering, source-path reporting, and fail-closed rejection of `java.lang.Runtime`.
- `neqsim-mcp-server/src/main/java/neqsim/mcp/server/NeqSimTools.java` exposes the existing MCP `inspectApi(className, memberFilter)` facade through normal tool-access enforcement and the standard response envelope.
- `neqsim-mcp-server/test_inspect_api_protocol.py` starts the packaged server over STDIO and directly calls `inspectApi` through `tools/call`. It requires `ProcessModel` to resolve to the exact runtime class with a filtered public `run` method and source pointer, and requires `java.lang.Runtime` to fail closed with the accepted `neqsim.*` namespace boundary.
- `.github/workflows/mcp_protocol_qualification.yml` builds the exact NeqSim core and MCP uber-jar on pull requests and `master`, then runs the focused dependency-free protocol harness. It has read-only repository permissions.
- `neqsim-mcp-server/test_mcp_server.py` continues to freeze the complete 71-tool publication surface and the primary trust-accounting contract. Its current 20/9/42 accounting is intentionally unchanged in this transport-qualification increment.

## Qualification boundary

The evidence supports class and public-method discovery only. It does not execute the inspected method, prove that a returned API is suitable for a particular engineering calculation, validate thermodynamic/process behavior, establish source-document completeness, or authorize arbitrary JVM reflection. Non-NeqSim targets remain outside the accepted resolution boundary.

`inspectApi` therefore remains `CONFIRMED_GAP` in inventory version 1.14 while its promotion candidate reports `promotionReady=true`. Phase 0 accounting stays at `20 EXPLICIT_TRUST + 9 CONTRACT_TESTED + 42 CONFIRMED_GAP` until the primary packaged-protocol classification contract and the machine-readable coverage status are changed together.

## Remaining atomic gate

The remaining gate is deliberately narrow: update `test_mcp_server.py`'s frozen trust-classification accounting from 20/9/42 to 20/10/41 and change `inspectApi` to `CONTRACT_TESTED` on the same exact head, then pass all applicable hosted validation. No intermediate head should advertise the promoted classification while the primary packaged protocol still expects the old accounting.

## Owner-roadmap and safety boundary

This evidence work changes no thermodynamic model, process calculation, pipeline/dynamics solver, DEXPI/P&ID semantics, production optimization, tool input/output schema, deployment profile, security policy, plant data, or control path. `inspectApi` remains advisory discovery only and never constitutes accountable engineering approval.
