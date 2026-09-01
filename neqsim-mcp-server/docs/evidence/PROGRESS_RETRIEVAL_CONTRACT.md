# Phase 0 progress-retrieval contract evidence

This note records bounded Phase 0 evidence for the existing `getProgress` MCP tool. It is an evidence artifact, not an additional MCP guide and not a scientific benchmark.

## Evidence currently present

- `src/main/java/neqsim/mcp/runners/ProgressTracker.java` implements bounded in-memory progress state, active-operation listing, point retrieval, milestone retention, completion, failure state, and old-operation eviction.
- `src/test/java/neqsim/mcp/runners/McpEvidenceInventoryFoundationTests.java` exercises a complete progress lifecycle: active discovery, a 50% update, milestone retrieval, completion at 100%, removal from the active list, and an explicit error for an unknown operation ID.
- `neqsim-mcp-server/test_mcp_server.py` invokes `getProgress` through the real MCP JSON-RPC transport with `action=listActive` and freezes the promoted trust-coverage accounting on the same exact head.
- Phase 0 evidence inventory version `1.12` classifies `getProgress` as `CONTRACT_TESTED`, with the exact source, regression, protocol, and evidence-note paths above.

## Trust boundary

The evidence supports only the software contract for advisory progress retrieval. It does not prove that an underlying calculation is correct, converged, scientifically validated, cancellable, or safe for plant control. It does not establish external authorization, cross-tenant isolation for a particular deployment, durability across process restarts, or delivery guarantees for every long-running runner.

The promotion changes Phase 0 trust-coverage accounting from `20 EXPLICIT_TRUST + 8 CONTRACT_TESTED + 43 CONFIRMED_GAP` to `20 EXPLICIT_TRUST + 9 CONTRACT_TESTED + 42 CONFIRMED_GAP`. The machine-readable classification and exact packaged protocol expectation move together on one exact head, so no promotion candidate remains queued. The underlying `BenchmarkTrust` registry is unchanged; `getProgress` has no numerical or engineering-accuracy benchmark claim.

## Owner-roadmap boundary

This evidence note changes no process, thermodynamic, pipeline, dynamic, optimization, DEXPI/P&ID, or facility-model behavior. It introduces no MCP-only simulator and no live-plant write or control path.
