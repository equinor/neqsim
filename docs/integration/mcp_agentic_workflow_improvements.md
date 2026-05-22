---
title: "MCP Agentic Workflow Improvements"
description: "Explains the NeqSim MCP updates that make process simulation and thermodynamic workflows easier for agents to discover, validate, execute, and review. Covers standard response contracts, capability maps, setup templates, schemas, examples, lifecycle metadata, benchmark trust, and safety gates."
---

# MCP Agentic Workflow Improvements

This update makes NeqSim easier to use as a process-simulation and fluid-behaviour tool from
agentic MCP clients. The main change is not a new thermodynamic model. It is a stronger contract
between agents and NeqSim: agents can now discover what is available, fetch schemas and examples,
build valid inputs, understand units, inspect validation and benchmark maturity, and route results
through review gates.

The external-simulator interoperability item is intentionally not included in this update. CAPE-OPEN,
FMI, and commercial simulator bridge work remains a separate future task.

## What Improved

| Area | Before | After this update |
|------|--------|-------------------|
| Response parsing | Tools returned useful JSON, but response shape varied by runner | Calculation responses expose a standard MCP envelope with `apiVersion`, `tool`, `data`, `provenance`, `validation`, `qualityGate`, and `warnings` |
| Process execution | Agents could call process simulations directly and see runtime failures | `runProcess` can run validation first and return validation context with the process result |
| Tool discovery | Capability metadata focused on the highest-use runners | `getCapabilities` describes all 56 MCP tools, including detailed descriptors for high-use tools and generic descriptors for the rest |
| Schemas | Detailed schemas covered the high-use tool set | Every advertised MCP tool has an input and output schema URI; high-use tools keep detailed schemas, remaining tools get generic contract schemas |
| Examples | Core examples covered common flash, process, validation, and safety patterns | Every advertised MCP tool can now return at least a contract-level starter example through the `tool` category |
| Setup guidance | Agents had to infer workflow setup from prose | `getCapabilities` and `neqsim://setup-templates` expose 26 setup templates across flash, process, PVT, flow assurance, lifecycle, safety, reporting, and uncertainty workflows |
| Units and JSON fields | Process JSON and units were documented but not fully machine-readable | The capability manifest now includes `processJsonContract`, `unitSystem`, and `equipmentPropertyOntology` sections |
| Trust and review | Benchmark maturity existed but was not tied into a larger agent policy | The manifest now includes `benchmarkRegistry`, `modelLifecycle`, `optimizationUncertaintyWorkflows`, and `safetyGatePolicy` metadata |
| Regression coverage | Tests checked selected schemas and capability descriptors | Tests assert that every advertised tool has schemas, examples, validation metadata, response-contract metadata, and capability coverage |

## New Capability Sections

`getCapabilities` now includes these machine-readable sections in addition to the existing domain,
equipment, trust, and workflow metadata:

| Section | Why it matters for agents |
|---------|---------------------------|
| `toolCapabilities` | Maps MCP tool names to schema names, runner classes, categories, required fields, units, examples, templates, limitations, validation, and response-contract metadata |
| `processJsonContract` | Lists legal process root fields, fluid fields, equipment fields, connection fields, supported equipment types, stream ports, common properties, and unit encoding rules |
| `capabilityGraph` | Provides graph nodes and edges linking domains, tools, schemas, examples, and governance contracts |
| `equipmentPropertyOntology` | Helps agents assemble flowsheets by exposing equipment ports, common settable properties, dynamic support, and automation variables |
| `benchmarkRegistry` | Tells agents where to fetch maturity, validation cases, accuracy bounds, reference data, and known limitations before relying on results |
| `unitSystem` | Gives preferred unit dimensions and encoding rules so generated payloads avoid silent unit mistakes |
| `automaticFlowsheetBuilder` | Describes a staged workflow for turning a process description into validated `runProcess` JSON |
| `optimizationUncertaintyWorkflows` | Describes supported sensitivity, model-risk, scenario, and P10/P50/P90 patterns |
| `modelLifecycle` | Describes inspect, mutate, snapshot, restore, diff, and audit fields for stateful model workflows |
| `safetyGatePolicy` | Keeps safety, relief, LOPA, SIL, risk, flare, HAZOP, and barrier outputs behind explicit engineering review gates |

## Practical Agent Flow

An agent can now follow a reliable workflow without relying on hidden knowledge:

1. Call `getCapabilities`.
2. Pick the relevant tool and setup template.
3. Fetch `neqsim://schemas/{tool}/input` and `neqsim://examples/tool/{tool}`.
4. Build explicit unit-bearing input JSON.
5. Call `validateInput` where applicable.
6. Execute the selected tool.
7. Parse `data`, `provenance`, `validation`, `qualityGate`, and `warnings`.
8. Fetch benchmark trust or safety-gate metadata before using results in a design claim.

This is especially important for process simulation and fluid behaviour because model choice,
units, component naming, operating envelope, and validation maturity all change how trustworthy a
result is.

## Validation Added

The focused tests now cover the discovery contract rather than only a few happy paths:

- `SchemaCatalogTest` verifies every advertised MCP tool has input and output schemas.
- `CapabilitiesRunnerTest` verifies schema links, example links, setup templates, validation
  coverage, response-contract metadata, graph sections, and safety-review flags.
- Existing runner tests continue to cover flash, process, validation, examples, envelopes, batch,
  property-table, and phase-envelope behaviour.

Together, these tests guard against a common agentic failure mode: advertising a capability that has
no schema, no example, no validation story, or no predictable response shape.

## What This Does Not Claim

This update completes the MCP foundation for agentic NeqSim workflows: advanced workflows are now
discoverable, schema-backed, unit-aware, benchmark-aware, lifecycle-aware, and review-gated. Some
higher-level workflows, such as P&ID-to-flowsheet generation and full Monte Carlo or Pareto
optimization, are exposed as standardized agent workflows rather than certified autonomous execution
engines.

Engineering-critical results still need professional review, benchmark checks, standards context,
and independent verification where the task requires it. The remaining work is deeper execution
automation and broader industrial validation, not basic MCP capability discovery.
