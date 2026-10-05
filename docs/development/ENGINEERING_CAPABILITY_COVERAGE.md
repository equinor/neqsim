---
title: "Agent and MCP engineering capability coverage"
description: "Source inventory, execution-route mappings, evidence boundaries and the completion plan for full agent and MCP exposure."
---

# Agent and MCP engineering capability coverage

This extends [campaign #3153](https://github.com/equinor/neqsim/issues/3153),
the existing MCP inventories and [agent coverage #4191](https://github.com/equinor/neqsim/pull/4191).
The merged test-layout PR [#4194](https://github.com/equinor/neqsim/pull/4194)
relocated the protocol harness; the coverage assertions use that canonical path.
This shared baseline remains coordinated with the autonomous campaign.

## Query the inventory

`getCapabilities` includes an `engineeringCoverage` summary. For bounded detail,
call the existing `runCapability` tool with this `capabilityJson` payload:

```json
{"action":"coverage","view":"capabilities","query":"sulfur","offset":0,"limit":20}
```

`capabilities` links workflow registrations to APIs, skills, agents, MCP tools,
test sources and limitations. `apis` inventories public primary Java types:

```json
{"action":"coverage","view":"apis","query":"compressor","limit":20}
```

Search is a case-insensitive substring of identifier/title. `domain` is exact:
top-level Java package for APIs, engineering discipline for workflows. Pass
`nextOffset` and `catalogDigest` on subsequent pages; stale digests are rejected.
Limits are 1–50. Negative, fractional and overflowing offsets fail explicitly.
Empty searches return success with zero entries. This operation is read-only.
The existing `runCapability` profile/access policy still applies; metadata never
grants invocation permission. The repository inventory is available when the
tool is not enabled in a deployment profile.

## Interpret counts honestly

| Field | Meaning | Not evidence of |
|---|---|---|
| `publicTypes` | Public top-level Java classes, interfaces and enums | Engineering-operation or supported-method counts |
| `withSkillMention` | Qualified or unambiguous simple-name mention in a core skill | Working instructions |
| `withAgentMention` | Mentioned skill is required by a core agent | Successful agent execution |
| `registeredCapabilities` | Explicit workflow records with verified references | All NeqSim functionality |
| `registeredAnchors` | Distinct API types named by workflow records | Full exposure of their methods |
| `reviewRequired` | No registered workflow or infrastructure classification | Proof of an unavailable execution route |

The source denominator includes helpers and examples pending classification;
it excludes nested/package-private types and annotation declarations. Skill
mention extraction covers core SKILL.md files only, not external repositories or
linked reference documents. Ambiguous simple names are not counted.

`complete` stays false. A registered route is `declared`, its test sources are
`present`, execution is `not_recorded`, and engineering qualification is
`not_assessed`. This does not negate existing tool benchmarks: their evidence
has not yet been reconciled into this finer inventory. Never promote source
presence automatically to successful execution or engineering qualification.

## Maintain the shared catalog

Add registrations to `devtools/engineering_capabilities.json`: stable operation
ID, existing MCP tool, exact API anchors, skills, agents, test paths and limits.
Run `python devtools/build_engineering_coverage.py` after Java formatting and
relevant source/agent/skill changes. Commit the generated resource at
`src/main/resources/neqsim/mcp/engineering-coverage.json`.

`--check` fails on stale output, including changes inside an existing Java type.
The generator rejects unknown API/tool/skill/agent/test references and duplicate
IDs. The catalog digest is the SHA-256 of the packaged inventory file, so it binds the generated inventory and source-type digests;
it is not an execution receipt or a digest of every repository file. The summary counts and
the digest are computed when `EngineeringCoverageCatalog` loads the resource and are not committed:
a global hash and aggregate counters change in every PR and would conflict between any two PRs.

This adds no reflection invocation permission and no second simulator. Stateful
models retain their canonical process/model runners; eligible static methods
retain their existing bounded execution policy.

## Explicit operation contracts

Package 2 now contains fourteen bounded, source-backed operations: sulfur vapour pressure, eight pure engineering-unit conversions, and five static slug impact-force screening calculations. Each contract records its classification, exact Java signature, units, applicability, existing MCP route, executable example, tolerance, and evidence sources. The generated summary reports classification totals, and the packaged MCP qualification retrieves these records through `runCapability action=coverage` before replaying every supported example through `action=invoke`.

A `supported` operation means only the recorded static signature is supported on the existing bounded runtime route. It does not qualify unlisted methods on the same class, create new reflection authority, or replace canonical stateful process/model runners. Pressure, pressure-difference, temperature, temperature-difference, length, time, power, and energy conversions are included; state-dependent rate conversions are intentionally excluded. The slug operations expose only the documented homogeneous screening equations and circular-area helper; they do not qualify slug prediction, structural response, fatigue, supports, or piping-code acceptance.

## Completion work packages under #3153

| Package | Deliverable and exit gate |
|---|---|
| 1. Baseline | Source inventory, paginated MCP access, agent links, freshness CI and explicit unreviewed entries |
| 2. Operation contracts | Classify supported operations versus internal/experimental/deprecated APIs; record exact signatures, units, applicability and examples using existing schemas/API inspection |
| 3. Stateful exposure | Qualify model construction, mutation, cloning, revision, serialization and replay; map supported properties and connections |
| 4. Domain completion | Cohesive PRs combining missing adapters, schemas, skills, examples, positive/negative tests and documentation |
| 5. End-to-end qualification | Actual MCP request-to-model-to-results-to-report tests at calculation, train, multi-area and large-facility scales; exact-build receipts |
| 6. Release enforcement | API/schema drift, numerical validity, transport, cancellation, response-size and compatibility gates; independent completion audit |

Domain PR order and coordination:

1. Thermodynamics/PVT: characterization, EOS/mixing rules, flashes, properties,
   phase boundaries, experiments and fitting (#2937, numerical guard #3792).
2. Equipment/facilities: supported equipment families, streams, recycles,
   utilities, topology and operating/design cases.
3. Chemistry/flow assurance: reactive/electrolyte systems, treating,
   hydrate/wax/scale/corrosion (#3144 and existing domain campaigns).
4. Wells/pipelines/networks: profiles, boundary conditions, steady/transient
   models, integrated production and network composition.
5. Dynamics/control/optimization: states, events, controllers, sensitivities,
   capacity and hard constraints (#2911/#3298, #3154, performance #2939).
6. Engineering studies: mechanical design, advisory safety, emissions,
   economics, DEXPI/diagrams (#2899) and reports.

Full exposure requires 100% of **classified supported engineering operations**
to have agent discovery and a documented MCP route, with explicit denominators.
Every exposed operation needs successful and invalid-input execution fixtures.
Engineering-qualified operations additionally need independent expected values,
units, justified tolerances, applicability, limitations and exact-build evidence.
Check conservation, convergence, meaningful finite outputs, constraints and nearby
operating points where applicable. Experimental availability stays distinct from
qualification. Unresolved classification or required routes prevent completion.
