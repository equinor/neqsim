# Phase 0 progress-retrieval contract evidence

This note records bounded Phase 0 evidence for the existing `getProgress` MCP tool. It is an evidence artifact, not an additional MCP guide and not a scientific benchmark.

## Evidence currently present

- `src/main/java/neqsim/mcp/runners/ProgressTracker.java` implements bounded in-memory progress state, active-operation listing, point retrieval, milestone retention, completion, failure state, and old-operation eviction.
- `src/test/java/neqsim/mcp/runners/McpEvidenceInventoryFoundationTests.java` now exercises a complete progress lifecycle: active discovery, a 50% update, milestone retrieval, completion at 100%, removal from the active list, and an explicit error for an unknown operation ID.
- `neqsim-mcp-server/test_mcp_server.py` already invokes `getProgress` through the real MCP JSON-RPC transport with `action=listActive`.

## Trust boundary

The evidence supports only the software contract for advisory progress retrieval. It does not prove that an underlying calculation is correct, converged, scientifically validated, cancellable, or safe for plant control. It does not establish external authorization, cross-tenant isolation for a particular deployment, durability across process restarts, or delivery guarantees for every long-running runner.

`getProgress` remains a `CONFIRMED_GAP` in the current Phase 0 trust-coverage accounting. This increment deliberately does not change the frozen `20 EXPLICIT_TRUST + 6 CONTRACT_TESTED + 45 CONFIRMED_GAP` classification or the packaged protocol accounting. A future promotion to `CONTRACT_TESTED` must update the machine-readable inventory and exact protocol expectation together on one validated head.

## Owner-roadmap boundary

This evidence note changes no process, thermodynamic, pipeline, dynamic, optimization, DEXPI/P&ID, or facility-model behavior. It introduces no MCP-only simulator and no live-plant write or control path.
