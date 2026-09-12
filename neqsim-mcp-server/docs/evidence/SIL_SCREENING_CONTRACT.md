# Bounded SIF PFD screening contract

## Purpose

`runSIL` is a deterministic screening calculation for caller-supplied Safety
Instrumented Function (SIF) reliability inputs. It uses NeqSim's canonical
`SafetyInstrumentedFunction` and `SILVerificationResult`. This qualification
freezes the bounded MCP request, response, and failure behavior. Inventory
`1.38 / 20 explicit + 38 contract-tested + 13 confirmed gaps` promotes that
software contract to `CONTRACT_TESTED`; it does not perform a functional-safety
lifecycle assessment or verify standards conformance.

## Request contract

The request is one UTF-8 JSON object no larger than 16,384 bytes. It contains:

- optional non-blank `name` and `description` strings of at most 256
  characters;
- an optional integer `claimedSIL` from 1 through 4;
- an optional `architecture` of `1oo1`, `1oo2`, or `2oo3`;
- an optional finite `proofTestInterval_hours` greater than zero and at most
  87,600; and
- exactly one calculation mode: a finite direct `pfdAvg` greater than zero and
  at most one, or a non-empty `components` array with at most 100 entries.

Each component has a non-blank bounded `name`, a `type` of `sensor`, `logic`, or
`finalElement`, and exactly one finite `pfd` or `lambdaDU_per_hr`. Both numeric
forms must be greater than zero and at most one. A component may override the
top-level architecture for its failure-rate calculation. Component order is
preserved.

The proof-test interval and failure-rate ceilings are computational admission
bounds, not engineering recommendations. Malformed, conflicting, non-finite,
out-of-range, oversized, unsupported, and numerically invalid inputs fail
closed with stable error codes. Parser and exception details are not returned.

## Canonical calculation

For direct mode, the caller-supplied PFD average is passed to the canonical SIF
model. For component mode, an explicit component PFD is retained, while a
failure-rate input is converted using the existing canonical architecture
formulas:

\[
PFD_{1oo1} = \lambda_{DU} T / 2,
\quad
PFD_{1oo2} = (\lambda_{DU} T)^2 / 3,
\quad
PFD_{2oo3} = (\lambda_{DU} T)^2.
\]

The bounded runner rejects individual or aggregate probabilities outside the
finite interval `(0, 1]`. `SafetyInstrumentedFunction` remains authoritative
for PFD, risk-reduction factor, proof-test interval conversion, and the
supported architecture formulas. `SILVerificationResult` remains authoritative
for the returned PFD-based SIL band and hardware-fault-tolerance field. No
parallel SIF, PFD, architecture, or SIL model is introduced.

## Output evidence

A successful response includes:

- `screeningOnly: true` and `standardConformanceClaimed: false`;
- an explicit caller-supplied input basis;
- the PFD-based `screening` summary, with the SIL result labelled indicative;
- ordered component contributions when component mode is used;
- the canonical calculation payload; and
- explicit assumptions and an independent-assessment boundary.

Architecture suitability, diagnostic coverage, and systematic capability are
explicitly marked unverified. IEC 61508 and IEC 61511 are named only as
context; the response does not claim conformance.

## Engineering and safety boundary

The caller owns the SIF definition, claimed SIL, reliability data, architecture,
proof-test interval, independence, common-cause assumptions, diagnostic
coverage, proof-test coverage and effectiveness, repair assumptions, systematic
capability, device qualification, operating context, and lifecycle evidence.

This capability does not:

- identify hazards, define a safety function, or establish SRS completeness;
- validate failure-rate or PFD source data;
- verify independence, common cause, architecture suitability, diagnostic
  coverage, proof-test effectiveness, or systematic capability;
- select, assign, approve, or independently verify a SIL;
- demonstrate IEC 61508, IEC 61511, NORSOK, regulatory, or project conformance;
- certify a design or authorize plant, operating, maintenance, or lifecycle
  action; or
- replace independent functional-safety assessment, qualified engineering
  judgment, and accountable approval.

## Executable evidence

- `src/test/java/neqsim/mcp/runners/SILRunnerTest.java` exercises canonical
  direct and component calculations, deterministic order, input modes and
  types, numeric and text bounds, non-finite values, aggregate range, stable
  errors, and the safety boundary.
- `neqsim-mcp-server/test_sil_protocol.py` exercises discovery and the same
  contract through the packaged real-MCP STDIO transport.
- `neqsim-mcp-server/test_mcp_server.py` protects the public discovery boundary
  and preserves the Phase 0 inventory classification.

Merged #3664 established the direct Java and packaged-MCP qualification.
Inventory version `1.38 / 20 explicit + 38 contract-tested + 13 confirmed gaps`
atomically promotes `runSIL` to `CONTRACT_TESTED`. Machine-readable coverage,
Java assertions, focused protocol expectations, comprehensive accounting,
acceptance baselines, and documentation move together without production,
schema, canonical-model, policy, or numerical changes. No promotion candidate
remains queued.
