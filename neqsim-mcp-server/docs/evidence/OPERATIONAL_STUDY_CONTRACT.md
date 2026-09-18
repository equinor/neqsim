# Bounded operational-study orchestration contract

This note records the qualification boundary for MCP `runOperationalStudy`.
The tool delegates to NeqSim's existing `JsonProcessBuilder`, `ProcessSystem`,
operational tag/scenario/evidence-package, controller-response, pipe-section, and
operating-envelope implementations. It does not create a parallel MCP-only
process model or simulator.

## Qualified software behavior

The qualification covers:

- at most 1,048,576 UTF-8 request bytes, with the normal MCP execution-policy
  and response-size guards still controlling runtime and returned payloads;
- fail-closed empty, malformed, oversized, and unknown-action requests with
  stable error codes and remediation;
- discovery of the eight operational actions and the request boundary;
- deterministic controller-response screening for identical supplied arrays;
- ordered valve and steady-state actions against a newly constructed canonical
  local `ProcessSystem`, with the resulting process report returned;
- normal MCP access enforcement, standard response validation and quality-gate
  evidence, and real packaged-STDIO transport;
- an invariant `screeningOnly=true`, `plantWritePerformed=false`, and
  `advisoryBoundary` response on success and failure.

The local-copy boundary is explicit. Caller-supplied logical or historian tag
names are inputs to the simulation mapping only; the tool performs no historian,
control-system, field-device, or other external plant write.

## Engineering and safety boundary

The tool does not establish causality from correlation; validate source-document
extraction, tag identity, P&ID completeness, instrumentation accuracy, controller
tuning or stability for a plant, process-model fidelity, thermodynamic accuracy,
convergence for arbitrary inputs, mass or energy closure, equipment condition,
operating-envelope validity, trip timing, mitigation effectiveness, standards
conformance, or safe operating limits.

Returned scenarios, controller metrics, margins, trends, trip predictions, and
mitigation suggestions are advisory screening evidence. They do not authorize a
plant action, controller change, alarm/trip change, bypass, override, operating
envelope, certification, or accountable engineering approval. Qualified
operations, control, process-safety, and process-engineering review remains
required.

## Qualification and promotion boundary

Focused Java tests cover discovery, the explicit request bound, malformed and
unknown actions, existing field-data/scenario/evidence/controller/pipe/envelope
behavior, and the invariant advisory boundary. The focused
`test_operational_study_protocol.py` runs six scenarios through the packaged
MCP server.

Merged PR #3747 established the direct Java and packaged-MCP qualification
recorded here. Inventory `1.42 / 20 explicit + 42 contract-tested + 10
confirmed gaps` atomically promotes `runOperationalStudy=CONTRACT_TESTED`.
The canonical machine-readable evidence record, Java assertions, focused
packaged protocol, authoritative comprehensive protocol accounting, and
current-state documentation move together on one exact head. No promotion
candidate remains queued, `scientificValidationComplete=false`, and every
engineering, safety, and advisory limitation above remains controlling.
