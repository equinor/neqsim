# Bounded task-solver contract evidence

## Scope

The `solveTask` MCP tool accepts a non-blank caller-authored task description
plus structured fluid and runner parameters. Supported keywords select one of
nine deterministic fixed plans backed by existing NeqSim runners. The runner
executes those steps in order, records each result, stops after a failed
required step, optionally validates the collected output, and returns explicit
plan and completion accounting.

Merged qualification PR #3575 established the direct Java and packaged-MCP
evidence recorded here. Inventory version `1.33` promotes `solveTask` from
`CONFIRMED_GAP` to `CONTRACT_TESTED` without changing the production runner,
public schema, canonical NeqSim models, policy, or numerical behavior.

## Contract-tested implementation boundary

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
preservation, and promoted-inventory continuity. The comprehensive MCP
regression includes `solveTask` in the authoritative 33-tool
`CONTRACT_TESTED` set on the 71-tool surface.

## Evidence boundary

This evidence does not establish general natural-language task understanding,
open-ended planning, arbitrary runner, tool, plugin, code, shell, class, or
network execution, semantic result chaining, numerical accuracy, convergence,
conservation, uncertainty, optimization quality, facility completeness,
persistence, distributed execution, standards compliance, production
hardening, transport or identity security, tenant isolation, plant or control
authority, certification, or engineering approval. Each underlying
calculation retains its own model, applicability, validation, and trust
boundaries.

## Inventory continuity

Current Phase 0 inventory version `1.35` records `20` explicit-trust tools,
`34` contract-tested tools, and `17` confirmed gaps. No promotion candidate
is queued. Phase 0 remains incomplete and
`scientificValidationComplete=false`.
