# Bounded LOPA screening contract

## Purpose

`runLOPA` is a deterministic screening calculation for caller-supplied Layer of
Protection Analysis inputs. It uses NeqSim's canonical `LOPAResult` and
`SafetyInstrumentedFunction` calculations. This contract qualifies the MCP
request, response, and failure boundaries; it is not a process-safety study or
an assertion that the supplied layers are valid independent protection layers.

## Request contract

The request is one UTF-8 JSON object no larger than 16,384 bytes. It contains:

- an optional non-blank `scenario` of at most 256 characters;
- finite, strictly positive `initiatingEventFrequency_per_year` and
  `targetFrequency_per_year` values;
- a non-empty `layers` array with at most 100 entries; and
- for every layer, a non-blank `name` of at most 256 characters and a finite
  `pfd` greater than zero and at most one.

Layer order is significant and is preserved. Missing, malformed, non-finite,
out-of-range, oversized, and numerically unrepresentable inputs fail closed
with stable error codes. Parser or exception details are not returned.

## Canonical calculation

For initiating frequency \(f_0\) and caller-supplied layer PFDs \(p_i\), the
ordered frequency trace is

\[
f_i = f_{i-1} p_i,
\qquad
f_{mitigated} = f_0 \prod_i p_i.
\]

`LOPAResult` remains the authority for the ordered layer trace, mitigated
frequency, target comparison, gap, total risk-reduction factor, and additional
risk-reduction factor. `SafetyInstrumentedFunction.calculateRequiredPfd`
remains the authority for the additional PFD calculation. The returned
additional SIL band is explicitly labelled indicative; it is not SIL
verification or design.

The MCP runner adds validation and advisory metadata but does not introduce an
alternative risk, LOPA, or SIL model.

## Output evidence

A successful response includes:

- `screeningOnly: true` and `standardConformanceClaimed: false`;
- `inputBasis: CALLER_SUPPLIED_FREQUENCIES_AND_LAYER_PFDS`;
- the canonical `lopa` evidence, including ordered layers and frequency trace;
- `gapAnalysis`, including target status and canonical additional-reduction
  evidence when the target is not met; and
- explicit caller-assumption and advisory-boundary statements.

IEC 61511 and CCPS LOPA are named only as context. The response does not claim
compliance with either source.

## Engineering and safety boundary

The caller owns the initiating-event frequency, target frequency, PFD values,
layer claims, independence claims, common-cause assumptions, human-reliability
assumptions, proof-test assumptions, consequence basis, and project risk
criteria.

This capability does not:

- identify hazards or initiating events;
- validate frequencies, consequences, safeguards, IPL independence, common
  cause, or human reliability;
- verify a SIF, SIL, proof-test interval, architecture, or lifecycle;
- determine tolerable risk or accept a scenario;
- certify standards or regulatory compliance;
- authorize plant, operating, maintenance, or design action; or
- replace a qualified process-safety study and accountable approval.

## Executable evidence

- `src/test/java/neqsim/mcp/runners/LOPARunnerTest.java` exercises canonical
  calculations, deterministic order, input types and ranges, request/layer/text
  bounds, non-finite values, underflow, and stable errors.
- `neqsim-mcp-server/test_lopa_protocol.py` exercises discovery and the same
  contract through the packaged real-MCP STDIO transport.
- `neqsim-mcp-server/test_mcp_server.py` protects the public tool inventory and
  direct discovery boundary within the comprehensive protocol regression.

This qualification does not promote `runLOPA` in the Phase 0 evidence
inventory. Promotion requires a later, separately accepted increment after this
contract has merged and remained green on its exact published head.
