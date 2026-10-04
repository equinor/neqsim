# Bounded risk-matrix screening contract

## Scope

`runRiskMatrix` assigns deterministic 5×5 scores to caller-supplied risk
events. It uses the existing
`neqsim.process.safety.risk.RiskMatrix` probability, consequence, and risk
level categories; it does not create a second safety or process model.

The tool accepts either explicit ordinal levels or numeric frequency and
production-loss inputs. It returns the selected categories, product score,
display level and colour, the input basis, and the event with the highest
score.

## Bounded request contract

| Dimension | Limit |
| --- | ---: |
| Complete UTF-8 request | 16,384 bytes |
| Events | 1–100 |
| Event name | 256 characters |
| Mitigation text | 2,048 characters |
| Explicit probability/consequence level | Integer 1–5 |
| Failure frequency | Finite and non-negative, failures/year |
| Production loss | Finite 0–100 percent |

Each event must use exactly one complete mode:

1. `probabilityLevel` plus `consequenceLevel`; or
2. `failuresPerYear` plus `productionLossPercent`.

Missing halves, mixed modes, non-object events, malformed JSON, fractional
levels, invalid ranges, empty arrays, and exceeded limits fail closed with
stable `INVALID_INPUT`, `INVALID_EVENT`, `TOO_MANY_EVENTS`, or
`REQUEST_TOO_LARGE` codes. Parser and internal exception details are not
returned.

## Qualified behavior

The explicit-level route maps integers directly to the existing NeqSim
categories. The numeric route uses the existing
`ProbabilityCategory.fromFrequency` and
`ConsequenceCategory.fromProductionLoss` thresholds after validating their
physical ranges. Both routes calculate the existing probability × consequence
score and `RiskLevel.fromScore` display band.

Responses identify `CALLER_SUPPLIED_LEVELS` or
`CALLER_SUPPLIED_FREQUENCY_AND_PRODUCTION_LOSS` for every event. Missing names
receive deterministic one-based labels. Repeated equal requests produce equal
screening content.

## Standards, engineering, and authority boundary

The built-in thresholds are generic defaults. They are not a project risk
matrix and do not demonstrate ISO 31000 or NORSOK Z-013 conformity. The tool
does not identify hazards, generate scenarios, estimate frequencies, model
consequences, validate safeguards, determine risk acceptance, select risk
reduction, approve work, or issue plant commands.

`screeningOnly=true` and `standardConformanceClaimed=false` make this
boundary machine-readable. The legacy `standard` response key is retained for
client compatibility, but its value identifies generic screening and requires
project-specific verification instead of claiming conformance. A qualified
safety team remains responsible for
project-specific definitions, evidence quality, scenario completeness,
independence and performance of safeguards, applicable regulations and
standards, ALARP or other acceptance decisions, actions, and accountable
approval.

Names and mitigation text are returned as inert JSON strings. They are not
executed, interpreted as instructions, or treated as proof that a mitigation
exists or is effective. Normal MCP profile access and response guards remain
authoritative.

## Evidence

- `src/main/java/neqsim/mcp/runners/RiskMatrixRunner.java`
- `src/main/java/neqsim/process/safety/risk/RiskMatrix.java`
- `src/test/java/neqsim/mcp/runners/RiskMatrixRunnerTest.java`
- `neqsim-mcp-server/src/main/java/neqsim/mcp/server/NeqSimTools.java`
- `neqsim-mcp-server/test_risk_matrix_protocol.py`
- `neqsim-mcp-server/test_mcp_server.py`

The focused Java suite freezes input validation, limits, threshold boundaries,
error codes, deterministic names, input-basis evidence, and advisory fields.
The packaged suite repeats discovery, valid routes, boundary cases, fail-closed
inputs, limits, deterministic behavior, and the standard MCP envelope over the
real JSON-RPC/STDIO transport.

Merged #3645 established this direct source and packaged-MCP qualification.
Inventory version `1.36 / 20 explicit + 36 contract-tested + 15 confirmed gaps`
atomically promotes `runRiskMatrix` to `CONTRACT_TESTED`. Machine-readable
coverage, Java assertions, the focused packaged protocol, synchronized protocol
expectations, authoritative comprehensive accounting, and documentation move
together without production, schema, canonical-model, policy, or numerical
changes. No promotion candidate remains queued.
