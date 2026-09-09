# Bounded streaming-simulation contract evidence

## Scope

The `streamSimulation` MCP tool starts bounded in-process parametric,
transient, and Monte Carlo operations using existing NeqSim runners. A caller
receives an opaque operation identifier, polls paginated results, may request
cooperative cancellation, and can list only that caller's operations.

This qualification keeps Phase 0 inventory at `1.33 / 20+33+18`.
`streamSimulation` remains `CONFIRMED_GAP` until a separate post-merge
promotion atomically records this direct evidence.

## Contract-tested implementation boundary

`NeqSimTools.streamSimulation` applies normal Tier 3 access enforcement and
the standard MCP response envelope before delegating to
`StreamingRunner`. The runner accepts only documented actions and applies
fixed bounds before registering background work:

- 1–1000 parametric sweep points;
- 1–10,000 dynamic time steps;
- 1–1000 Monte Carlo iterations;
- a dynamic process definition no larger than 256 KiB; and
- at most 100 result records per poll.

Sweeps require a non-empty, finite, non-negative component map with a positive
total, an explicit temperature or pressure variable, documented units, finite
conditions, temperature above absolute zero, and positive absolute pressure.
Monte Carlo requests apply the same composition rules and require finite
distribution parameters with non-negative standard deviations. Dynamic
requests require a nested process object or non-blank JSON string plus finite
positive timing and a bounded derived step count. Poll cursors are
non-negative; continuation uses `nextPollIndex` and `hasMoreResults`.
Transport responses keep the standard top-level `status` contract
(`success` or `error`) and expose asynchronous lifecycle state separately
as `operationStatus`.

Retained terminal results do not count against the 20-operation global
active-work allowance. Per-principal concurrency is still enforced by
`McpExecutionPolicy`. Every terminal path releases its acquired slot exactly once, including a
dynamic process-build failure. A timeout outcome cannot be overwritten by a
later worker exit, and cancellation cannot overwrite an existing terminal
outcome. Timeouts and caller cancellation remain cooperative.

Operation identifiers use 144 bits of secure randomness. Poll, cancel, and
list resolve only operations owned by the current principal. Unknown and
out-of-scope identifiers return the same non-disclosing response.

## Direct evidence

The focused Java contract in
`src/test/java/neqsim/mcp/runners/StreamingRunnerTest.java` covers fixed-limit
discovery, malformed and unknown actions, missing or invalid compositions,
non-positive and oversized work, invalid variables, units, physical ranges,
distributions and timing, negative cursors, unknown cancellation, and a real
two-point SRK sweep lifecycle. Existing
`McpPrincipalScopingTest` independently verifies that one principal cannot
poll, cancel, or list another principal's operation.

The packaged `neqsim-mcp-server/test_streaming_protocol.py` harness starts
the shaded server over STDIO and repeats seven transport-level scenarios. It
checks discovery language, standard response evidence, fixed limit reporting,
fail-closed requests, a canonical two-point NeqSim sweep, pagination metadata,
and non-disclosing cancellation. The comprehensive MCP regression remains the
authoritative 71-tool surface check.

## Evidence and advisory boundary

The contract proves bounded request admission, in-process lifecycle
accounting, principal-scoped lookup, canonical runner delegation, structured
errors, standard MCP envelopes, and packaged transport. It does not establish
EOS or process-model numerical accuracy, convergence for arbitrary inputs,
statistical or uncertainty validity, completeness of sampled distributions,
real-time deadlines, durability, recovery after restart, multi-instance
coordination, distributed execution, external queues, hard process isolation,
cooperative interruption of every numerical kernel, external IAM or transport
security, tenant isolation beyond the request context, plant control or
write-back, standards compliance, certification, or accountable engineering
approval. Each result retains the applicability and validation boundary of the
underlying NeqSim model and must be independently reviewed.
