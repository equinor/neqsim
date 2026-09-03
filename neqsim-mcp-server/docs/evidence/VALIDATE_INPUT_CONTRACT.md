# Pre-flight input validation contract evidence

## Capability and engineering question

`validateInput` answers whether an explicit NeqSim flash/process definition or
an existing reusable model handle passes the validator's declared pre-flight
checks. The authoritative route is `NeqSimTools.validateInput` →
`ModelRegistry.resolve` → `Validator.validate`. Validation uses the canonical
component lookup, `EquipmentFactory`, and the same temperature and pressure
parsers used by the Java flash route. It does not execute a simulation.

Inputs are inline JSON definitions or caller-scoped `ModelRegistry` handles.
Flash definitions may carry unit-bearing temperature and pressure values and
dimensionless component mole fractions. The standardized MCP response preserves
the validator's Boolean `valid` field and ordered `issues`; every issue contains
`severity`, `code`, `message`, and `remediation`.

## Qualification evidence

The existing `ValidatorTest` directly covers flash and process definitions,
supported thermodynamic models and equipment, component-name remediation,
required flash specifications, broad temperature/pressure ranges, composition
warnings, malformed input, and error aggregation. The MCP qualification
workflow now runs this class explicitly under Java 21.

`test_validate_input_protocol.py` starts the real packaged MCP server over STDIO
and verifies six cross-layer scenarios:

- acceptance of the canonical `simple-separation` process and equivalence
  between its inline definition and reusable model handle;
- acceptance of a TP flash with explicit Celsius and bara inputs;
- fail-closed malformed JSON with stable `JSON_PARSE_ERROR` evidence;
- deterministic unknown-component diagnosis, `methane` suggestion, and
  remediation;
- warning-only composition-sum behavior that does not incorrectly mark the
  input invalid; and
- atomic inventory `1.23 / 20+21+30` promotion with `validateInput` classified
  as `CONTRACT_TESTED` from the merged prerequisite evidence.

The focused Java and packaged-MCP checks run in
`.github/workflows/mcp_protocol_qualification.yml` before the comprehensive MCP
regression.

## Scope and limitations

This evidence qualifies only deterministic pre-flight classification, issue
shape, severity semantics, remediation, model-handle routing, and packaged
transport. A `valid` response means that no declared validator error was found;
it does not guarantee that the model is complete, physically representative,
numerically convergent, conservative, performant, suitable for a facility, or
approved for an engineering decision.

Phase 0 inventory version `1.23` retains 20 tools with explicit trust, promotes
`validateInput` as the twenty-first bounded contract, and leaves 30 confirmed
gaps. The classification is software-contract evidence only and adds no
simulation-execution or scientific-validation claim.
