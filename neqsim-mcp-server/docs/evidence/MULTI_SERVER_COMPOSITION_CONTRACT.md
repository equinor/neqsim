# Bounded multi-server composition metadata contract

## Scope

`composeMultiServerWorkflow` is a process-local metadata and planning surface. It
lists five built-in external-server types, exposes four fixed workflow templates,
accepts bounded descriptive metadata for custom server types, and returns
keyword-routed suggestions that an authorized MCP host may choose to execute.

The runner does not open a network connection, launch a command, call an
external MCP tool, authenticate to another service, persist an endpoint or
credential, transfer plant data, or execute the suggested steps. Responses mark
this boundary with `metadataOnly=true`, `executionPerformed=false`, and, for
plans and the capability manifest, `hostExecutionRequired=true`.

## Qualified actions

| Action | Qualified behavior |
| --- | --- |
| `listServers` | Deterministic name-sorted metadata for the five protected built-ins and bounded custom records |
| `registerServer` | Process-local descriptive metadata with a 64-character name, bounded strings, at most 64 unique tools and formats, and a maximum of 32 custom records |
| `removeServer` | Removal of a named custom record; built-in records fail closed |
| `listWorkflows` | Deterministic discovery of the four fixed templates |
| `getWorkflow` | Exact retrieval of one fixed template or a structured not-found error |
| `planComposition` | Deterministic keyword routing for one non-blank task of at most 4096 characters, with consecutive step numbers |
| `describeCapabilities` | Static NeqSim provider/consumer/format metadata and the explicit host-execution boundary |

The complete JSON request is capped at 16,384 UTF-8 bytes. Malformed JSON,
non-object input, missing actions, invalid names, non-string metadata, oversized
strings or arrays, unknown actions, and missing records fail closed with
structured errors. Built-in server metadata cannot be replaced or removed.

Registration accepts metadata only. Endpoint or URL fields, executable commands,
arguments, environment blocks, headers, credentials, tokens, API keys, and
secrets are rejected. Tool and data-format entries are trimmed, bounded, and
deduplicated while preserving caller order.

## Canonical-model and execution boundary

The composition runner contains no thermodynamic or process model. It does not
replace NeqSim fluids, streams, `ProcessSystem`, `ProcessModel`, runners, or
result objects. A suggested NeqSim step names an existing canonical tool; only a
later, separately executed call to that tool can produce an engineering result.

Suggested external server names and tools are descriptive labels, not evidence
that a server is installed, reachable, authenticated, compatible, licensed, or
approved. The host application must resolve the actual server and tool, obtain
authorization, validate schemas and units, control data movement, enforce
timeouts and resource limits, and preserve result provenance.

## Security, persistence, and tenancy boundary

Custom records live only in the current Java process. They are not durable,
distributed, encrypted, authenticated, or tenant-scoped. Any deployment that
shares a process between principals must prevent untrusted callers from using
the mutation actions or provide isolation outside this runner.

The runner deliberately stores no connection material. It does not establish
external IAM, TLS, certificate validation, secret management, audit durability,
network allowlisting, sandboxing, or supply-chain trust. The normal
`IndustrialProfile` access check remains authoritative at the MCP facade.

The built-in `plant-historian` metadata includes a descriptive `writeTags`
capability because a host may know such a tool. This runner never calls it and
grants no plant write-back or control authority.

## Engineering and advisory limitations

Plans are deterministic keyword suggestions, not natural-language
understanding, dependency resolution, semantic result chaining, topology
validation, unit reconciliation, feasibility analysis, causal diagnosis,
optimization, or workflow execution. The fixed templates are examples, not
validated facility designs, operating procedures, safety studies, or design.

This qualification establishes software input, state, response, and transport
behavior only. It does not validate EOS or process-model fidelity, convergence,
mass or energy conservation, equipment design, economic estimates, historian
quality, document extraction, CAD results, HAZOP/SIL/relief analysis, standards
applicability, uncertainty, facility completeness, certification, or accountable
engineering approval.

Every planned step and every result later returned by another server requires
independent schema, unit, provenance, security, applicability, and engineering
review before use.

## Evidence

- `src/main/java/neqsim/mcp/runners/CompositionRunner.java`
- `src/test/java/neqsim/mcp/runners/CompositionRunnerTest.java`
- `neqsim-mcp-server/src/main/java/neqsim/mcp/server/NeqSimTools.java`
- `neqsim-mcp-server/test_composition_protocol.py`
- `neqsim-mcp-server/test_mcp_server.py`
- `neqsim-mcp-server/docs/evidence/MULTI_SERVER_COMPOSITION_CONTRACT.md`

The focused Java suite covers deterministic discovery, fixed workflows,
sequential planning, bounded metadata lifecycle, rejection of connection and
credential material, protected built-ins, malformed and oversized inputs, and
the fixed custom-registry ceiling. The packaged harness repeats the public
contract over the real STDIO MCP transport and requires standard response
evidence.

Inventory `1.35 / 20 explicit + 35 contract-tested + 16 confirmed gaps`
promoted `composeMultiServerWorkflow` to `CONTRACT_TESTED` after the
qualification merged. Current inventory `1.38 / 20 explicit + 38 contract-tested
+ 13 confirmed gaps` retains that classification. Machine-readable coverage,
Java assertions, the focused packaged harness, authoritative comprehensive
protocol accounting, and documentation move atomically on one exact head. This
classification records only the bounded software contract above; every
engineering, external-server, security, persistence, scientific, numerical,
plant-authority, certification, and accountable-approval exclusion remains in
force.
