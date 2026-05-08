---
title: Automated HAZOP from STID and Simulation
description: End-to-end workflow for generating HAZOP worksheets from STID/P&ID documents, historian data, NeqSim process simulations, safety scenarios, barrier registers, and report outputs.
---

# Automated HAZOP from STID and Simulation

This workflow connects technical-document access, plant-data context, and NeqSim
process simulation to generate a first-pass IEC 61882 HAZOP worksheet. It is a
screening and preparation workflow for a chaired HAZOP team, not an automatic
approval mechanism.

The implemented MCP bridge is `runHAZOP`. It accepts a standard NeqSim process
JSON definition plus optional HAZOP nodes, failure modes, safeguards, evidence
references, and a barrier register. The runner builds and runs the base process,
generates equipment-failure scenarios, runs those scenarios on process copies,
and returns HAZOP rows with simulation evidence and report markdown.

## Scope

Use this workflow when an agent or engineer has one or more of these inputs:

- STID/P&ID documents with equipment, lines, valves, instruments, and safeguards.
- C&E charts, SRS documents, inspection reports, or operating procedures.
- Historian tag mappings or representative operating data.
- A NeqSim process model built from JSON, Python, Java, UniSim conversion, or manual modeling.
- A barrier register for PSFs, SCEs, SIFs, alarms, relief devices, or procedural safeguards.

Human review remains mandatory. The generated worksheet proposes nodes,
deviations, consequences, and safeguards, but a competent HAZOP team must verify
node boundaries, causes, safeguard independence, action ownership, and the final
risk ranking.

## Workflow

1. Retrieve relevant documents into the task folder.

   Use the STID/document retrieval workflow to place source documents under
   `task_solve/.../step1_scope_and_research/references/`. Record document IDs,
   revisions, page references, figure coordinates, and extraction confidence.

2. Extract process topology and safeguards.

   From STID/P&ID and C&E sources, extract HAZOP nodes with `nodeId`,
   `designIntent`, `equipment`, `safeguards`, and `evidenceRefs`. Extract any
   PSF/SCE/SIF/relief evidence into a barrier-register JSON block.

3. Build or retrieve a NeqSim process model.

   Use a standard `runProcess` JSON definition. The HAZOP runner accepts either
   `processDefinition`, `processJson`, or top-level `fluid` plus `process`.

4. Select failure modes.

   Use `failureModes` to focus the study, or `enableAllFailureModes` for a broad
   equipment-failure sweep. Supported modes include valve stuck open/closed,
   cooling loss, heating loss, compressor trip, pump trip, blocked outlet,
   instrument failure, external fire, power failure, and loss of containment.

5. Run `runHAZOP`.

   The runner uses `AutomaticScenarioGenerator` to inspect the `ProcessSystem`,
   map equipment failures to HAZOP-style deviations, apply scenarios to copied
   processes, run the modified simulations, and capture key pressure,
   temperature, mass-balance, and equipment KPIs where available.

6. Review HAZOP rows.

   Each row includes node, equipment, guideword, parameter, failure mode, cause,
   consequence, safeguards, recommendation, document evidence references, and
   scenario-simulation status.

7. Link to barrier management and LOPA/SIL/QRA.

   If `barrierRegister` is supplied, `runHAZOP` calls the barrier-register
   runner and includes validation plus LOPA, SIL, bow-tie, and QRA handoff
   blocks. Only barriers with valid evidence and performance standards should be
   credited as protection layers.

8. Generate report output.

   Use `reportMarkdown` directly in task reports, or pass the whole output to
   reporting tools. The report should include assumptions, source documents,
   generated rows, simulation caveats, and the human-review gate.

## Input Contract

Minimal structure:

```json
{
  "studyId": "HAZOP-COMP-001",
  "runSimulations": true,
  "failureModes": ["COOLING_LOSS", "COMPRESSOR_TRIP"],
  "processDefinition": {
    "fluid": {
      "model": "SRK",
      "temperature": 298.15,
      "pressure": 10.0,
      "mixingRule": "classic",
      "components": {
        "methane": 0.90,
        "ethane": 0.07,
        "propane": 0.03
      }
    },
    "process": [
      {
        "type": "Stream",
        "name": "feed",
        "properties": {"flowRate": [5000.0, "kg/hr"]}
      },
      {
        "type": "Compressor",
        "name": "1st Stage",
        "inlet": "feed",
        "properties": {"outletPressure": [30.0, "bara"]}
      }
    ]
  },
  "nodes": [
    {
      "nodeId": "Node-01 First-stage compressor",
      "designIntent": "Compress feed gas to intercooler pressure",
      "equipment": ["1st Stage"],
      "safeguards": ["Anti-surge control", "Discharge pressure trip"],
      "evidenceRefs": ["EV-COMP-101"]
    }
  ]
}
```

A complete example is available through `getExample` with category `safety` and
name `hazop-study`. JSON schemas are available through `getSchema` for
`run_hazop` input and output.

## Output Contract

The `runHAZOP` response contains:

| Field | Purpose |
|-------|---------|
| `summary` | Counts for nodes, generated rows, enabled failure modes, and simulations |
| `process` | Baseline process name, equipment count, warnings, and report JSON |
| `nodes` | Node-level text reports generated from `HAZOPTemplate` |
| `hazopRows` | Machine-readable HAZOP worksheet rows |
| `scenarioResults` | Per-scenario simulation status, timing, and captured KPI values |
| `qualityGates` | Human-review, process-model, evidence, and generated-row flags |
| `barrierRegisterHandoff` | Optional barrier validation plus LOPA/SIL/bow-tie/QRA handoff |
| `reportMarkdown` | Report-ready markdown section |

## Quality Gates

Before accepting the generated worksheet:

- Verify every node boundary against the STID/P&ID source.
- Confirm all extracted equipment names match NeqSim process unit names.
- Check that scenario perturbations represent the intended failure mechanism.
- Review failed or non-converged scenario simulations manually.
- Confirm safeguards are independent, specific, effective, auditable, and available.
- Do not credit a barrier in LOPA unless it has traceable evidence and a performance standard.
- Record chaired HAZOP team decisions, action owners, and due dates separately from generated output.

## Implementation Notes

`runHAZOP` currently uses these components:

- `ProcessSystem.fromJsonAndRun(...)` for the baseline simulation.
- `AutomaticScenarioGenerator` for equipment-failure discovery and scenario execution.
- `ProcessSafetyScenario` for applying perturbations to copied process models.
- `HAZOPTemplate` for node worksheets and text reports.
- `BarrierRegisterRunner` for optional evidence-linked safeguard handoff.

The runner is intentionally conservative. It proposes rows and captures scenario
outputs, but it does not claim that a row is fully risk-assessed until the
barrier register, LOPA/SIL/QRA, and human review are complete.

## Related Documentation

- [HAZOP Worksheet](HAZOP.md)
- [Barrier Management and SCE Traceability](barrier_management.md)
- [Scenario Generation](../process/safety/scenario-generation.md)
- [Web API JSON Process Builder](../integration/web_api_json_process_builder.md)
- [STID Retriever Skill](../../.github/skills/neqsim-stid-retriever/SKILL.md)
- [Technical Document Reading Skill](../../.github/skills/neqsim-technical-document-reading/SKILL.md)
- [Plant Data Skill](../../.github/skills/neqsim-plant-data/SKILL.md)
