# Canonical process-loop orchestration contract

Inventory 1.43 records `runProcessLoop=CONTRACT_TESTED` from direct Java and
real packaged-MCP evidence. This is a bounded software and transport contract,
not a scientific, optimization, control, or plant-operating claim.

## Qualified behavior

- The caller supplies a canonical `runProcess`-style definition. The runner
  builds and solves the native NeqSim `ProcessSystem` once.
- Trial batches retain request order and are delegated sequentially to
  `ProcessAutomation.evaluate`, reusing the preceding converged state.
- Each result preserves its zero-based `trialIndex`, the automation schema,
  feasibility flag, rejected setpoints, and requested readback evidence.
- A rejected address degrades its own trial instead of hiding the remaining
  sweep.
- Blank process/trial inputs and malformed JSON fail closed through the standard
  MCP error envelope.
- Normal profile/access enforcement, response standardization, and packaged
  JSON-RPC/STDIO transport remain in force.

Evidence sources are `AutomationRunner`, canonical `ProcessAutomation`,
`AutomationLoopRunnerTest`, the `NeqSimTools` facade,
`test_process_loop_protocol.py`, the comprehensive protocol suite, and this
document.

## Explicit exclusions

This classification does not establish:

- global or local optimization, optimality, or feasible-space completeness;
- correctness, suitability, or safety of caller-selected decision variables,
  bounds, units, objectives, constraints, or trial order;
- numerical or thermodynamic accuracy, convergence for arbitrary inputs,
  conservation, uncertainty, or model applicability;
- controller tuning or stability, equipment condition, facility fidelity, or
  safe operating limits;
- durability, restart recovery, distributed coordination, transactionality, or
  parallel execution;
- connection to, reading from, or writing to plant historians, control systems,
  field devices, or other live systems;
- standards conformance, certification, plant or control authority, or
  accountable engineering approval.

Qualified engineers remain responsible for model selection, units, bounds,
constraints, result interpretation, independent validation, process safety,
operations review, and every real-facility decision.
