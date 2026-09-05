# `validateResults` software-contract qualification

## Engineering question and maturity

Can the MCP surface deterministically apply NeqSim's existing engineering-result rules, preserve structured findings and
fail closed on malformed result JSON without presenting the validator as new scientific evidence?

Phase 0 inventory 1.26 retains this bounded behavior as **`CONTRACT_TESTED`** after merged PR #3406 established the
prerequisite Java and packaged-MCP qualification and merged PRs #3416/#3421 restored the capability-response transport
contract. The classification changes no production validator behavior or schema.

## Authoritative behavior

`NeqSimTools.validateResults` delegates to `EngineeringValidator.validate(resultsJson, context)` and then applies the
standard MCP response envelope and response-size guard. No MCP-only calculation or validation model is introduced.

The input is a JSON object containing a result package plus a context string. The validator recursively selects the
first numeric value for each implemented field name and applies deterministic rules for:

- temperature and pressure bounds;
- compressor efficiency, ratio, discharge temperature and power;
- separator residence time and gas velocity;
- heat-exchanger approach and duty;
- pipeline velocity and pressure-drop ratio;
- water-dew-point indication;
- supplied mass- and energy-balance error fields; and
- supplied convergence status.

The output reports `validationContext`, finding counts, `passed`, `verdict`, and findings with stable code, severity,
message and remediation fields. Parse failure is blocking. Warnings and information remain non-blocking.

## Units, assumptions and validity range

The validator does not infer or convert unit metadata. Implemented bare numeric fields use the rule's documented basis:
temperature in degrees Celsius, pressure in bara, velocity in m/s, power in kW, residence time in seconds, and mass- or
energy-balance errors as fractions. Callers must normalize producer output to those field names and bases before relying
on a rule. Missing fields are skipped; validation is therefore not proof that a result package is complete.

The mass- and energy-balance checks assess only supplied aggregate error values. They do not independently recompute
component, total-mass, enthalpy or facility-wide closure from a canonical `ProcessSystem`.

## Acceptance evidence

- `EngineeringValidatorTest` covers clean, warning-only, blocking, nested and malformed inputs.
- `test_validate_results_protocol.py` repeats those boundaries through the real packaged STDIO server and proves that
  inventory 1.26 records 20 explicit + 25 contract-tested + 26 confirmed gaps.
- `test_mcp_server.py` retains the broad real-protocol `validateResults` call.
- `mcp_protocol_qualification.yml` runs the focused Java and packaged-MCP contracts before the comprehensive suite.

The contract requires deterministic findings, fail-closed malformed JSON, standard envelope fields, no mutation, and
successful focused plus comprehensive protocol checks. The one production compatibility change is intentional:
malformed/non-object JSON changes from warning-pass to error-fail while retaining the existing `PARSE_ERROR` code,
message and remediation.

## Explicit limitations and stop boundary

This evidence does **not** establish simulation execution, thermodynamic or equipment-model fidelity, convergence,
component or facility-wide conservation, numerical robustness of an upstream calculation, standards currency, causal
diagnosis, safe operating limits, plant or control authority, design certification, or accountable engineering
approval. The supplied result's units, provenance, assumptions, convergence evidence, limitations and independent
benchmarks remain authoritative.

Java and JSON/MCP views are applicable and qualified by this increment. A separate Python calculation and notebook are
not applicable because the capability is a Java-backed transport/software contract. No schema, tool-name, agent/skill,
deployment-profile, companion-repository or live-system integration change is included.
