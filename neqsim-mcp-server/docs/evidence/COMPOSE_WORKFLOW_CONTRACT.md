# Bounded composed-workflow contract evidence

## Scope

The `composeWorkflow` MCP tool accepts a caller-authored ordered list of
supported NeqSim runner steps, executes them sequentially, records each
result, and stops after the first failed required step. This page defines the
bounded software contract under direct Java and packaged-MCP qualification.
It does not certify the scientific calculations selected by a workflow.

## Qualified implementation boundary

`NeqSimTools.composeWorkflow` applies the normal tool-access policy and
standard response envelope, then delegates to
`TaskSolverRunner.composeWorkflow`. The runner accepts an explicit workflow
name, optional shared fluid, and a finite ordered `steps` array. Each step
selects one of the existing curated runner names and supplies explicit input.

The shared-fluid normalization is additive. The original `fluid` object is
preserved in `combinedData` and as the nested step field, while its
model/components are also copied to the step-input root used by calculation
runners. Step-specific input is applied afterward and therefore remains the
explicit override. No thermodynamic object, equation, parameter, or result is
reimplemented by the orchestration layer.

## Direct evidence

- `TaskSolverRunnerTest` executes a real methane TP flash through the
  composed route and verifies workflow identity, total/completed step counts,
  successful step output, shared-fluid preservation, and combined result
  evidence.
- The Java contract verifies exact `UNKNOWN_RUNNER` diagnostics,
  stop-on-first-failure behavior, and rejection of missing or malformed step
  input.
- `test_compose_workflow_protocol.py` repeats those visible contracts through
  the packaged server's JSON-RPC/STDIO transport and standard response
  evidence.
- `mcp_protocol_qualification.yml` runs the focused Java and packaged suites
  before the comprehensive MCP regression.

The calculation case is a routing and data-shape fixture. It is not
independent validation of SRK methane properties.

## Qualified contract

The candidate evidence covers:

1. Explicit workflow identity and ordered caller-authored steps.
2. The existing curated runner-name dispatch table; unknown runners fail
   closed.
3. Shared-fluid preservation and additive root-field normalization for
   calculation-runner compatibility.
4. Step-specific input precedence.
5. Per-step output, duration, runner identity, and success accounting.
6. Stop after the first failed required step.
7. Structured missing-step and malformed-input errors.
8. Normal MCP access enforcement, standard response evidence, and packaged
   STDIO transport.

## Security, numerical, and engineering boundary

This qualification does not establish:

- natural-language planning, inferred workflow design, or arbitrary code,
  shell, class, plugin, MCP-tool, or network execution;
- semantic compatibility or unit conversion between one step's output and the
  next step's input;
- transactionality, rollback, persistence, restart recovery, distributed
  execution, scheduling, quotas, or tenant isolation;
- scientific accuracy, numerical fidelity, convergence, component or energy
  conservation, uncertainty, or optimization quality;
- completeness or suitability of the selected workflow for a real facility;
- external identity, IAM, authorization, transport security, or a hardened
  deployment boundary;
- plant connectivity, control authority, safety certification, or accountable
  engineering approval.

Callers remain responsible for selecting compatible runner inputs, inspecting
every step's provenance, validation, quality gate, warnings, assumptions, and
limitations, and obtaining qualified engineering review.

## Inventory status

`composeWorkflow` remains `CONFIRMED_GAP` in inventory version `1.31`
with accounting `20 explicit + 31 contract-tested + 20 confirmed gaps`.
Qualification and promotion are separate atomic increments. A future
promotion may classify the tool only after this direct evidence merges and
all authoritative exact-head gates pass.
