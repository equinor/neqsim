# MCP session lifecycle contract evidence

`manageSession` is the existing stateful MCP façade for incremental work on a canonical NeqSim `ProcessSystem`. This note records the bounded Phase 0 software-contract evidence added for issue #3153. It does not promote the tool's trust classification by itself.

## Qualified software behavior

The qualification exercises the existing implementation rather than introducing a second simulator:

- a session can be created from the canonical `simple-separation` process definition returned by `getExample`;
- the created session retains a non-empty canonical equipment inventory and solved-state metadata;
- caller-visible `list` and `getState` preserve the session identifier, owner and process metadata;
- an unknown action fails closed with a stable `UNKNOWN_ACTION` diagnostic;
- `close` removes the session and later lookup fails closed with `SESSION_NOT_FOUND`;
- authenticated Java callers cannot inspect, list or close a session owned by a different authenticated principal;
- the same owner can inspect and close its session after a denied cross-caller attempt.

The focused Java contract is `SessionRunnerContractTest`. Existing `SessionRunnerTest` continues to qualify process-backed create, evaluate, batch reads/writes and adjustable-parameter access. The packaged STDIO protocol contract is `neqsim-mcp-server/test_session_protocol.py`, and the comprehensive `test_mcp_server.py` retains the independent create/list/close route.

## Bounds and ownership

`SessionRunner` keeps sessions in memory, uses cryptographically strong identifiers, limits the in-process registry to 50 sessions and expires sessions after 30 minutes of inactivity. Authenticated ownership is derived from `McpRequestContext`; a client-supplied `ownerId` cannot impersonate another authenticated subject. Unauthenticated desktop STDIO use remains compatible with the `anonymous` owner when transport security is not enabled.

These are implementation and software-contract bounds, not tenant-capacity or service-level guarantees. The current store is process-local and is not durable across server restart. This qualification does not establish distributed coherence across server replicas or external persistence.

## Engineering and scientific boundary

No thermodynamic model, process equation, solver, equipment implementation or numerical tolerance changes in this increment. Session lifecycle qualification does **not** establish:

- numerical accuracy or model fidelity;
- convergence adequacy for a particular flowsheet;
- component, total-mass or energy closure for a particular executed case;
- facility completeness or fidelity to plant configuration;
- causal troubleshooting conclusions;
- live-plant write authority, control authority or accountable engineering approval.

Executed results remain governed by their own units, provenance, convergence, validation, warnings and limitations.

## Phase 0 accounting boundary

Inventory `1.19` remains `20 EXPLICIT_TRUST + 17 CONTRACT_TESTED + 34 CONFIRMED_GAP` in this qualification increment. `manageSession` deliberately remains `CONFIRMED_GAP` until this evidence is merged and a later atomic accounting change can promote the contract without implying scientific validation. The focused real-MCP test freezes that pre-promotion boundary to prevent accidental overclaiming.
