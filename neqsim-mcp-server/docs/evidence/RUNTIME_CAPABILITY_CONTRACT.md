# Bounded runtime-capability contract evidence

## Scope

The `runCapability` MCP tool indexes eligible methods from the running NeqSim
artifact, returns explicit routing metadata, and can invoke a narrowly bounded
subset of public static methods. This page records the directly exercised software contract and its atomic
inventory promotion. It is not a general claim about every NeqSim calculation.

## Direct evidence

- `GeneralCapabilityRunnerTest` exercises runtime discovery for a public
  static sulfur calculation and a stateful process-equipment capability.
- The Java contract invokes the exact
  `SulfurThermodynamics.calculateVapourPressureBar(double)` signature and
  verifies JSON serialization of its result.
- Search tests verify deterministic ordering, result-limit clamping, source
  paths, and the `static-json`, `process-json`, and inspection routing model.
- Negative Java paths reject external classes, instance methods, MCP-internal
  runners, unsupported generic containers, unknown actions, malformed JSON,
  and oversized requests.
- `test_capability_protocol.py` repeats the user-visible discovery,
  invocation, and rejection paths through the packaged server's real
  JSON-RPC/STDIO transport and standard response envelope.
- `mcp_protocol_qualification.yml` runs both focused suites before the
  comprehensive packaged-MCP regression.

The sulfur invocation is a stable routing and serialization fixture. Its
numerical value is not presented as independent validation of the underlying
scientific correlation.

## Qualified contract

The direct evidence covers:

1. Runtime indexing of eligible `neqsim.*` methods visible to the server
   classloader.
2. Deterministic relevance ordering and a clamped result count.
3. Explicit source path and execution route metadata.
4. Exact class, method, arity, and optional parameter-type selection.
5. Invocation only for policy-eligible public static methods with bounded
   primitive, enum, string, or array inputs and JSON-serializable output.
6. Fixed request, argument, array, string, result, and timeout limits.
7. Fail-closed diagnostics for malformed, ambiguous, unavailable, or unsafe
   requests.
8. Normal MCP access enforcement, standard response evidence, and packaged
   STDIO transport.
9. Routing of stateful process equipment to `runProcess` instead of direct
   reflective execution.

## Security and engineering boundary

This qualification does not establish:

- complete discovery across arbitrary classloaders, modules, or shaded
  packages;
- semantic correctness of search intent or completeness of search results;
- scientific validity, uncertainty, units, or operating ranges for discovered
  methods;
- purity, thread safety, idempotence, or side-effect freedom of every eligible
  static method;
- cooperative interruption by invoked code after a timeout;
- an operating-system or process sandbox, memory/CPU quotas, or tenant
  isolation;
- plugin provenance, external IAM, authentication, authorization, or transport
  security;
- arbitrary instance construction or stateful equipment execution;
- plant connectivity, control authority, safety certification, or accountable
  engineering approval.

Callers must inspect the selected API, its documentation, units, assumptions,
warnings, and domain-specific validation before relying on a calculation.
Long-running and stateful simulations remain on curated process runners.

## Inventory state

Inventory version 1.31 atomically promotes `runCapability` to
`CONTRACT_TESTED` after merged PR #3554 established the direct evidence
above. Machine-readable coverage, Java assertions,
`test_capability_protocol.py`, synchronized focused protocol expectations,
authoritative `test_mcp_server.py` accounting, and documentation move
together from 20/30/21 to 20/31/20.

The focused packaged suite adds an inventory-promotion assertion and retains
all seven behavioral scenarios. Current inventory 1.39 retains the
classification under 20/39/12 accounting. No promotion candidate remains
queued, Phase 0 remains incomplete, and no scientific benchmark or
facility-suitability claim is introduced.
