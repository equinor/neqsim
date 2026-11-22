---
title: "Discover NeqSim functionality for agentic work"
description: "Find current APIs, specialist agents, skills, tested usage and model limitations across every NeqSim Java domain."
---

# Discover NeqSim functionality for agentic work

Use source-backed discovery alongside specialist workflows. A short recipe list,
a curated MCP tool list or a remembered API is never the complete NeqSim surface.
Load only the relevant skills and sections, and inspect the selected implementation
before building a calculation around it.

## Choose the discovery surface

| Environment | Discovery and execution |
| --- | --- |
| NeqSim MCP server | Discover the deployed artifact with `runCapability` search and `inspectApi`; inspect tool schemas and validate inputs before invocation. Preserve the returned route and profile restrictions. |
| Source checkout | Run `uv run --project devtools --locked devtools/capability_search.py "<engineering need or API>" --json`. Read the returned source, guide and test references, then use the specialist workflow. |
| Installed toolkit/plugins | Use the installed agent/skill retrievers and runtime MCP discovery. The checkout-only capability search requires Java sources and its route manifest; do not assume it is an installed module. |
| No execution runtime | Inspect authoritative source and guides, provide a bounded implementation or assessment, and state that execution is pending. A missing wrapper is not a missing engine model. |

`capability_search.py` searches the current public top-level source types and
declared public member names. Exact class/FQN queries take precedence; CamelCase
names are also split into words. It returns bounded API matches and a separate
list of engineering workflow matches. Follow the most specific package route,
then use `agent_search.py` and `skill_search.py` to find additional community or
enterprise expertise installed on the machine. Package routes are entry points;
helpers, experimental types and infrastructure still require inspection.

The tool reads current sources rather than trusting the generated MCP snapshot.
It does not parse inherited overload signatures, run tests, qualify models or
grant invocation permission. Inspect source interfaces/superclasses or use runtime
`inspectApi` for the exact constructor/method contract. Test references match source
identifiers/imports; a reference need not contain an assertion about the operation.

## Natural-language reservoir-to-products models

Use `@network-energy-systems` and `neqsim-production-system-modeling` for a
connected reservoir, well, SURF, multiphase flowline/riser, facilities, utilities
and downstream model. Preserve the user's Python or Java choice; both use the
same Java engine and one case/fluid/topology/interface contract. Load the skill's
chain reference for exact stage inputs, APIs, specialist routes and acceptance.
An external reservoir forecast can be replayed; responsive coupled forecasting
also needs a verified control exchange and reservoir rerun/restart contract.

Public core/community workflows must work with public or synthetic inputs.
Community screening coordinators retain their declared fidelity and hand off to
core models for requested detail. Optional enterprise agents/skills add governed
data and policy in their private owning repos. The private plugin repository
owns distribution configuration; this core repository owns the shared plugin
builder and discovery contracts. Validate public plugin builds and optional
enterprise composition without copying private content into public artifacts.

## Coverage by domain

`devtools/agent_capability_routes.json` owns package-to-workflow assignments.
Specific package routes override these domain fallbacks:

| Domain | Entry point | Start with |
| --- | --- | --- |
| `thermo` | `@thermo-fluid` | `neqsim-api-patterns`; EOS, mixing rules, characterization |
| `thermodynamicoperations` | `@thermo-fluid` | `neqsim-api-patterns`; flashes, stability, phase boundaries |
| `physicalproperties` | `@thermo-fluid` | `neqsim-api-patterns` physical-property reference |
| `pvtsimulation` | `@pvt-simulation` | `neqsim-eos-regression`; laboratory protocols and supported fitting targets |
| `blackoil` | `@pvt-simulation` | `neqsim-api-patterns`; black-oil tables, conversion and export guide |
| `process` | `@process-model` | `neqsim-process-modeling`; specialized routes for equipment, wells, pipelines, networks, automation, dynamics, control, design, safety and economics |
| `fluidmechanics` | `@flow-assurance` | `neqsim-flow-assurance`; flow/transport solver implementation |
| `chemicalreactions` | `@reaction-engineering` | `neqsim-reaction-engineering` |
| `standards` | `@gas-quality` | `neqsim-standards-lookup`; distinguish screening from clause-level conformity |
| `statistics` | `@optimize` | `neqsim-optimization-and-doe`; fitting, samples and uncertainty |
| `mathlib` | `@neqsim-test` | `neqsim-regression-baselines`; numerical infrastructure and EJML |
| `integration` | `@field-development` | `neqsim-integrated-production-and-lifecycle`; external engine boundaries |
| `api` | `@process-model` | `neqsim-api-patterns`; high-level facades |
| `mcp` | `@capability-scout` | `neqsim-capability-map`; schemas, profiles and operation registry |
| `datapresentation` | `@notebook-example` | `neqsim-notebook-patterns`; output/presentation infrastructure |
| `util` | `@neqsim-test` | `neqsim-api-patterns`; units, validation and shared infrastructure |

## Verify how to use a selected capability

1. Record the source commit and deployed artifact version. Reconcile differences
   before claiming a source API exists in a particular deployed server/JAR.
2. Inspect constructors, overloads, inheritance, setup order, run entry points,
   units, enums, mutation/clone behavior and failure semantics. For example, CME
   needs `runCalc()`; inherited `BasePVTsimulation.run()` only resets its operations
   object. Do not infer execution semantics from a shared method name.
3. Read the matching guide and test assertions. Check model/parameter provenance,
   supported components, applicability, output basis and documented limitations.
   Do not infer regression support from an enum or MMP support from a class name.
4. Choose the existing runtime route if it covers the operation. Retain canonical
   stateful process/model objects for flowsheets, sweeps, controls and optimization;
   metadata discovery does not turn object APIs into generic static invocations.
5. Define the validation target before calculating: independent reference values,
   justified tolerances, balances, equilibrium/convergence checks and nearby-state
   robustness as relevant. Preserve warnings and rejected/nonconverged results.
6. Pass the selected API/source, agent, skill section, inputs with units, setup,
   outputs, limitations, validation target and unresolved gaps to the next agent.
   Keep hand-offs reproducible without preloading the entire knowledge catalog.
7. For numerical work, preserve or improve speed and stability. Measure representative
   caller workflows with warmed, paired baseline/candidate runs; report solver work
   where available. Do not weaken physical bounds or tolerances to obtain speed.

## Distinguish evidence levels

| Evidence | Permitted conclusion |
| --- | --- |
| Source/type or workflow route exists | An implementation/entry point can be inspected |
| Test source refers to the type | A candidate usage example exists; inspect assertions |
| Focused test executed on the exact version | The named software behavior passed that test |
| Independent reference benchmark with units, tolerances and applicability | The recorded engineering case/property has validation evidence |

The operation registry in `devtools/engineering_capabilities.json` and the
[MCP coverage contract](ENGINEERING_CAPABILITY_COVERAGE.md) remain separate.
Full package routing does not mean every method has a supported MCP operation,
nor does it mean every model is independently qualified. Keep uncovered or
unsupported operations visible and report the specific remaining work.

## Keep discovery current

Run `uv run --project devtools --locked devtools/capability_search.py --audit --json`
and the capability-search tests after changing routes, agents or skills. The audit
reports the current source denominator, domains, routed types, unmapped types and
empty routes. It fails on unresolved agent/skill/doc paths or a route skill missing
from its agent's required skills. New top-level domains must receive an explicit
entry point. New types within existing packages inherit a navigation route only.

Run agent/skill structure, API-reference, routing evaluation, plugin-build and map
freshness checks. Regenerate the engineering-coverage snapshot after relevant
agent/skill/source changes. The existing AI lint workflow runs the audit and tests
so routing drift is reviewable with the code change. Add representative discovery
and routing cases when introducing a new capability; avoid duplicating entire
API manuals in prompts or skills.
