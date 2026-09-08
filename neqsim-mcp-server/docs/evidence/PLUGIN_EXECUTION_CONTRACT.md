# Process-local plugin execution contract qualification

This evidence note records the bounded `runPlugin` software contract established by
merged qualification #3534. Inventory 1.30 atomically promotes `runPlugin` from
`CONFIRMED_GAP` to `CONTRACT_TESTED` without changing production behavior,
public schemas, the canonical NeqSim model, or deployment policy.

## Qualified behavior

`PluginRegistry` stores `McpRunnerPlugin` instances in a process-local
concurrent map keyed by plugin name. Registration publishes the plugin name,
description, and declared input-schema string. A later registration with the
same name replaces that entry. Invocation forwards the supplied JSON string to
the selected plugin and returns the plugin's JSON output.

Unknown plugin names return a structured `PLUGIN_NOT_FOUND` diagnostic with
the currently available names. Exceptions thrown by plugin code are normalized
to `PLUGIN_ERROR` and include the plugin's declared input schema as
remediation. Unregister and clear operations remove only process-local registry
state.

The public `runPlugin` facade accepts `list` and `run` actions. A missing
action defaults to `list`; unknown actions and malformed JSON fail closed.
Normal tool-access enforcement and the standard MCP response envelope remain
in force.

## Evidence and validation

Direct evidence:

- `src/main/java/neqsim/mcp/runners/PluginRegistry.java`;
- `src/main/java/neqsim/mcp/runners/McpRunnerPlugin.java`;
- `src/test/java/neqsim/mcp/runners/PluginRegistryContractTest.java`;
- `neqsim-mcp-server/src/main/java/neqsim/mcp/server/NeqSimTools.java`;
- `neqsim-mcp-server/test_plugin_protocol.py`;
- `.github/workflows/mcp_protocol_qualification.yml`;
- `neqsim-mcp-server/test_mcp_server.py` as the comprehensive regression.

The Java contract covers registration, metadata discovery, exact input
forwarding, invocation output, same-name replacement, invalid registration,
absent-plugin diagnostics, exception normalization, unregister, and cleanup.
Assertions do not depend on iteration order. The packaged harness starts a
fresh server and verifies empty-catalog discovery, default-list behavior,
absent and empty plugin names, unknown actions, malformed input, standard
envelopes, and real STDIO transport.

Inventory `1.30 / 20 explicit + 30 contract-tested + 21 confirmed gaps`
recorded the promotion together with the machine-readable coverage record,
Java assertions, focused packaged-MCP accounting, and comprehensive protocol
accounting. Current inventory `1.32 / 20 explicit + 32 contract-tested + 19
confirmed gaps` retains that classification.
Coverage remains incomplete and `scientificValidationComplete=false`; this is a
bounded software-contract classification, not a scientific benchmark claim.

## Security and engineering boundary

A registered plugin executes application code inside the MCP server process.
This qualification does not establish:

- plugin discovery, installation, provenance, approval, or code signing;
- bytecode isolation, sandboxing, resource quotas, or denial-of-service
  resistance;
- tenant or principal isolation inside the shared static registry;
- persistence, restart recovery, distributed registry consistency, or audit
  durability;
- external IAM, transport authentication, network security, or secrets
  management;
- schema validity or enforcement beyond publishing the plugin-provided string;
- scientific accuracy, model validity, units, convergence, conservation, or
  engineering applicability;
- plant or control authority, certification, or accountable engineering
  approval.

Deployment operators remain responsible for deciding which plugin classes enter
the process, reviewing their dependencies and permissions, isolating the
runtime, validating every engineering result, and applying external security
controls.
