---
title: "Process-to-engineering simulator"
description: "Iterate NeqSim process cases into review-governed equipment, piping, valve, instrument, safety, material, mechanical, and DEXPI design outputs."
---

# Process-to-engineering simulator

The process-to-engineering simulator closes the loop between process physics and preliminary engineering design. It
runs every controlled process case on isolated copies, sizes configured engineering objects, applies selected design
variables to a separate working flowsheet, reruns the cases, and stops only when no design variable changes and all
declared constraints pass.

The original `ProcessSystem` is never modified. After a successful run, DEXPI and engineering-package compilation use
the isolated designed process and embed the calculated design state, governing cases, method evidence, warnings, and
review status.

```text
ProcessSystem / ProcessModel
  -> controlled design cases
  -> governing simulation envelope
  -> discipline design modules
  -> discrete candidate selection
  -> update isolated process geometry and ratings
  -> rerun process cases
  -> constraints and convergence
  -> safety/control dynamic verification
  -> engineering graph, DEXPI 2.0, registers and calculation package
```

## Configure the first complete vertical slice

Build a governed project, declare at least one executable case, and configure the tagged inlet separator, compressor,
export line, optional control valve, and pressure instrument:

```java
EngineeringProject project = NorsokOffshoreEngineeringBuilder
    .from("Compression project", process)
    .projectId("compression-A")
    .build();

project.addDesignCase(normalCase);
project.addDesignCase(maximumRateCase);
project.addDesignCase(turndownCase);

ProcessToEngineeringDesignBuilder.on(project)
    .separatorBasis(800.0, 0.107, 120.0)
    .exportLineLimits(20.0, 0.5)
    .compressorDrivers(0.10, 3000.0, 5000.0, 7500.0, 10000.0)
    .addInletCompressionExportSlice(
        "20-VA-001", "20-KA-001", "20-PL-001", "20-PV-001", "20-PIT-001");

EngineeringSimulationResult result = ProcessToEngineeringSimulator.run(project, 4);
```

The configured slice performs:

- Souders-Brown gas-capacity and liquid-retention separator sizing;
- discrete export-line diameter selection from velocity and simulated pressure-gradient constraints;
- compressor driver selection from the governing shaft-power case;
- IEC 60534 control-valve Cv selection when a valve tag is supplied;
- proposed design pressure, PSV set pressure, HH trip pressure and blowdown target;
- pressure-instrument range, uncertainty and response-budget screening;
- NORSOK M-001 degradation/material-class screening; and
- preliminary pressure-shell wall thickness and mass.

The selected separator and pipe dimensions are applied to the isolated process and therefore affect the next
simulation iteration. The result records every iteration, applied update, constraint, governing case, and final design
state.

## Add other equipment families

The builder also exposes explicit preliminary modules:

```java
ProcessToEngineeringDesignBuilder builder = ProcessToEngineeringDesignBuilder.on(project);

builder.addPumpDesign("30-PA-001", 0.10, 1.0,
    75.0, 110.0, 160.0, 250.0, 400.0);

builder.addHeatExchangerDesign("30-HA-001", 500.0, 25.0, 0.15,
    25.0, 50.0, 75.0, 100.0, 150.0, 250.0);

builder.addInventoryDesign("30-TK-001", 600.0, 0.70,
    10.0, 25.0, 50.0, 100.0, 250.0);

builder.addRatedCapacity("40-DA-001",
    EngineeringMetric.equipmentInletMassFlow("40-DA-001"),
    "ratedFeedCapacity", "kg/s", 0.10,
    5.0, 10.0, 20.0, 40.0, 80.0);
```

`EngineeringDesignModule` is the extension point for detailed equipment or company methods. A module reads the
governing case report and current design state, then returns proposed updates, process appliers, constraints, evidence,
and review warnings. Continuous calculations and discrete vendor or schedule candidates use the same update contract.

## Safety and scenario integration

The design loop establishes pressure and response proposals. The existing coupled relief/blowdown/flare calculation
and `DynamicSafetyScenarioRunner` then evaluate the configured hazard cases against the designed process copy.
`ProcessSafetyScenario.generateFromTopology` can prepare blocked-outlet, loss-of-drive, utility-loss and stuck-valve
screening cases for engineering review.

Scenario credibility, simultaneous-event groups, fire zones and final safeguards must be supplied from the project
hazard review. The simulator does not invent a HAZOP conclusion or SIL target.

## Engineering graph and DEXPI

After the loop runs:

- process geometry in the isolated designed copy is used for DEXPI serialization;
- every scalar design value is attached to the matching canonical engineering graph node;
- legacy DEXPI exports receive calculated-design attributes with units;
- `engineering-calculations.json` contains the complete loop and discipline evidence;
- equipment, line and instrument registers use the designed process; and
- the package retains `REVIEW_REQUIRED`, `engineeringApprovalRequired=true`, and
  `fitnessForConstruction=false` boundaries.

DEXPI remains the exchange representation. The canonical engineering graph and controlled calculation evidence are
the source of engineering identity, provenance and revision impact.

## Standards profile

The supplied methods are designed to be used with version-controlled project rule packs, including:

- DEXPI 2.0 for exchange serialization;
- NORSOK P-002 for offshore process-system and line-design requirements;
- NORSOK I-001 and IEC 61511 for instrumentation and supplied SIF requirements;
- NORSOK M-001 for materials and degradation review;
- IEC 60534 for control-valve sizing; and
- API 520/521 for supplied relief and depressurization cases.

The edition stored in the project design basis controls traceability. Numerical limits remain project inputs because
jurisdiction, service, company requirements, standard revisions and project phase can change the applicable rule.

## Required engineering review

The simulator produces calculated or proposed engineering suitable for concept and pre-FEED development. It does not
replace accountable discipline work for:

- HAZOP/LOPA scenario credibility, SIL selection or SRS approval;
- vendor compressor, pump, exchanger, valve or instrument guarantees;
- final two-phase relief method and disposal-system network verification;
- code mechanical design, fatigue, external loads, nozzle reinforcement or fabrication;
- corrosion assessment, welding and material approval;
- plot plan, maintainability, escape, access, fire and gas coverage; or
- construction, commissioning and operating approval.

See the executed
[process-to-engineering notebook](../../examples/notebooks/process_to_engineering_simulator.ipynb) for the full
vertical slice and convergence plots.
