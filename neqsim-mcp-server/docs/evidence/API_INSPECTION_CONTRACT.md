# Phase 0 runtime API-inspection contract evidence

This note records bounded Phase 0 evidence for the existing `inspectApi` MCP tool. It qualifies a future non-numerical contract promotion; it does **not** promote the tool in this increment and it is not a scientific benchmark.

## Evidence currently present

- `src/main/java/neqsim/mcp/runners/ApiKnowledgeRunner.java` performs version-matched reflection against the running NeqSim artifact. Resolution is restricted to fully qualified `neqsim.*` classes, a small explicit common-class map, and aliases accepted by the canonical `EquipmentFactory`.
- `src/test/java/neqsim/mcp/runners/ApiKnowledgeRunnerTest.java` proves representative equipment-alias and common-process-class resolution, member filtering, source-path reporting, and fail-closed rejection of `java.lang.Runtime`.
- `neqsim-mcp-server/src/main/java/neqsim/mcp/server/NeqSimTools.java` exposes the existing MCP `inspectApi(className, memberFilter)` facade through normal tool-access enforcement and the standard response envelope.
- `neqsim-mcp-server/test_mcp_server.py` currently freezes `inspectApi` in the exact 71-tool publication inventory, but does not yet call it through `tools/call`. That missing transport execution is intentionally retained as the promotion gate.

## Qualification boundary

The evidence supports class and public-method discovery only. It does not execute the inspected method, prove that a returned API is suitable for a particular engineering calculation, validate thermodynamic/process behavior, establish source-document completeness, or authorize arbitrary JVM reflection. Non-NeqSim targets remain outside the accepted resolution boundary.

`inspectApi` therefore remains `CONFIRMED_GAP` in inventory version 1.13 even though it is recorded as a promotion candidate for `CONTRACT_TESTED`. Phase 0 accounting stays at `20 EXPLICIT_TRUST + 9 CONTRACT_TESTED + 42 CONFIRMED_GAP`.

## Remaining atomic gate

Before promotion, the packaged real-MCP harness must directly invoke `inspectApi` and verify a representative successful NeqSim target plus the fail-closed non-NeqSim boundary. The same exact head must update the frozen protocol classification accounting and pass hosted validation. No intermediate head should advertise `CONTRACT_TESTED` without that transport evidence.

## Owner-roadmap and safety boundary

This evidence work changes no thermodynamic model, process calculation, pipeline/dynamics solver, DEXPI/P&ID semantics, production optimization, tool input/output schema, deployment profile, security policy, plant data, or control path. `inspectApi` remains advisory discovery only and never constitutes accountable engineering approval.
