# Simulation-variable write contract qualification

## Engineering question and maturity

Can the existing `setSimulationVariable` MCP surface mutate one declared input on the canonical NeqSim process,
rerun that process, retain requested engineering units and standard envelope evidence, and fail visibly on rejected
writes without implying numerical or plant-control authority?

This Phase 0 increment qualifies that bounded software contract. Inventory remains **1.26 / 20 explicit + 25
contract-tested + 26 confirmed gaps**, and `setSimulationVariable` remains `CONFIRMED_GAP` until a separate merged
promotion increment updates the inventory atomically. No production runner, public schema, canonical process
representation, thermodynamic model, deployment policy, or companion repository changes here.

## Authoritative behavior

`NeqSimTools.setSimulationVariable` applies the deployment-profile access gate, resolves an inline process definition or
a reusable `manageModel` handle, and delegates to `AutomationRunner.setVariableAndRun`. The runner builds and executes
the normal NeqSim `ProcessSystem`, resolves the declared address through `ProcessAutomation`, validates physical bounds,
writes only an input variable, reruns the same process, and returns the post-run report through the standard MCP
response envelope.

The qualification uses the catalogued `simple-separation` process and the writable `feed.temperature` address. A 35 °C
setpoint is represented as the explicit pair `35.0` and `C`. The test checks the returned address, value, unit, solver
report object, validation state, and quality-gate verdict. It compares only contract fields between inline and registered
model-handle calls; transient report ordering or metadata is not treated as numerical equality evidence.

## Rejection and state boundaries

A temperature below absolute zero is rejected by the existing physical-bound diagnostics. A separator outlet temperature
address is an output and must not be reported as a successful write. Empty process or address inputs fail closed. A
rejected call must not claim that a post-mutation simulation report was produced.

Each call reconstructs or resolves a canonical model and runs in the server process. This contract does not make the
mutation persistent across calls, processes, restarts, clients, or deployments. Persistence remains owned by
`manageState` and model/session lifecycle contracts. The distinct `saveSimulationState` and `compareSimulationStates`
snapshot-diff semantics are not qualified here.

## Acceptance evidence

- `AutomationVariableWriteContractTest` checks a bounded Celsius write, rerun/report handoff, physical-bound rejection,
  non-writable output rejection, standard envelope fields, and missing-input failures against
  `ExampleCatalog.processSimpleSeparation()`.
- `test_simulation_variable_write_protocol.py` obtains the same canonical fixture through packaged MCP, exercises inline
  and `manageModel`-handle routes, repeats rejection cases, and proves Phase 0 accounting remains unpromoted.
- `McpRunnerContractTest` retains the broad standard-response check for the public mutation surface.
- `test_mcp_server.py` remains the authoritative packaged-protocol registration and accounting regression.
- `mcp_protocol_qualification.yml` runs the focused Java and packaged-MCP mutation contracts before the comprehensive
  protocol suite.

Java and JSON/MCP views are applicable. Python is only a dependency-free protocol driver. Notebook, rendered-document,
whole-sheet, public thermodynamic benchmark, facility-wide conservation, and optimization-quality gates are not
applicable to this bounded software-contract qualification.

## Units, assumptions, and limitations

The setpoint uses degrees Celsius exactly as requested. The underlying fluid and process definition retain their
catalogue units and normal NeqSim conversion path. Returning the requested value proves transport and address-unit
handling; it does not independently prove the internal absolute-temperature state, phase equilibrium, equipment
performance, or facility-wide mass and energy balances.

The run assumes the public synthetic `simple-separation` fixture is sufficient to exercise one stream input mutation.
It does not establish that every equipment input, alias, fuzzy correction, unit system, multiphase state, recycle,
multi-area process, constraint, or operating envelope is feasible or correctly represented.

## Security and advisory boundary

`setSimulationVariable` is an engineering execution tool subject to `IndustrialProfile` access policy. Contract
qualification does not grant repository, model, plant, control-system, DCS, historian, or digital-twin write permission.
It does not create a closed-loop controller, bypass approval gates, issue operating instructions, or establish
cybersecurity, identity, tenant-isolation, or deployment-hardening claims.

The returned calculation remains advisory. Convergence, per-result provenance, warnings, validation maturity,
assumptions, limitations, measurements, facility constraints, and accountable engineering review remain authoritative.

## Documentation impact and stop boundary

Documentation impact: this new evidence page records the existing user-visible mutation/rerun contract and its
limitations; no public API guide or schema changes because production behavior and arguments are unchanged.

The increment stops at deterministic address resolution, one bounded input mutation, rerun/report sequencing,
structured rejection, inline/model-handle equivalence, and packaged STDIO transport. It does not promote inventory,
alter scientific trust metadata, change numerical behavior, qualify snapshot comparison, or claim model accuracy,
optimization quality, plant authority, design certification, or accountable engineering approval.
