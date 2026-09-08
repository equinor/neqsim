# Bounded task-solver qualification evidence

## Scope

The `solveTask` MCP tool accepts a non-blank caller-authored task description
plus structured fluid and runner parameters. Supported keywords select one of
nine deterministic fixed plans backed by existing NeqSim runners. The runner
executes those steps in order, records each result, stops after a failed
required step, optionally validates the collected output, and returns explicit
plan and completion accounting.

This page records qualification evidence only. `solveTask` remains a
`CONFIRMED_GAP` in the Phase 0 evidence inventory until a separate promotion
increment atomically updates all inventory surfaces.

## Qualified implementation boundary

`NeqSimTools.solveTask` applies the normal Tier 3 tool-access policy and
standard response envelope, then delegates to
`TaskSolverRunner.solveTask`. The bounded router recognizes keyword families
for compression, separation, dehydration, pipeline, PVT, flow assurance,
reservoir, economics, and dynamic studies. Descriptions outside those
families fail with `UNSUPPORTED_TASK`; absent or blank descriptions fail
with `MISSING_TASK`.

The shared `fluid` object is preserved in the report and its canonical model
and components are exposed to runner inputs that use top-level fields.
Caller-supplied `parameters` and step overrides remain authoritative.
Results are collected under named step records; the solver does not infer
semantic transformations between one runner's output and the next runner's
input.

A task report exposes `status`, `success`, `taskType`, `totalSteps`,
`completedSteps`, the planned steps, executed step results, optional
validation, and combined collected data. A required runner failure produces a
top-level `TASK_STEP_FAILED` diagnostic and preserves the first underlying
runner error code as `causeCode`.

## Direct evidence

The focused Java contract in
`src/test/java/neqsim/mcp/runners/TaskSolverRunnerTest.java` covers:

- a real PVT saturation-pressure calculation using a shared canonical fluid;
- deterministic task classification and fixed-plan accounting;
- stop-on-required-failure behavior and explicit error status;
- missing, blank, malformed, and unsupported task rejection.

The packaged `neqsim-mcp-server/test_solve_task_protocol.py` harness starts
the shaded server over STDIO and repeats seven transport-level scenarios,
including discovery text, standard envelopes, the real PVT route, diagnostic
preservation, and inventory continuity. The comprehensive MCP regression keeps
a supported PVT task on the authoritative 71-tool surface.

## Evidence boundary

This evidence does not establish general natural-language task understanding,
open-ended planning, arbitrary runner or code execution, semantic result
chaining, numerical accuracy, convergence, conservation, facility
completeness, standards compliance, production hardening, transport or
identity security, plant or control authority, certification, or engineering
approval. Each underlying calculation retains its own model, applicability,
validation, and trust boundaries.

## Inventory continuity

Phase 0 stays at inventory version `1.32` with `20` explicit-trust tools,
`32` contract-tested tools, and `19` confirmed gaps. No promotion candidate
is created. Phase 0 remains incomplete and
`scientificValidationComplete=false`.
