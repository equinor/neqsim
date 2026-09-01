---
title: "Engineering simulator foundations"
description: "Run isolated design cases, typed calculations, coupled relief/blowdown/flare studies, and dynamic protection scenarios."
---

# Engineering simulator foundations

NeqSim can execute a coordinated engineering simulation before compiling DEXPI and discipline deliverables. The API is
split into four layers so process physics, project rules, safety evidence, and document serialization remain separate:

1. isolated and deterministic multi-case process simulation;
2. typed calculation results with readiness, provenance, method versions, and uncertainty;
3. coupled steady PSV, transient blowdown, flare-header, and flare-capacity evaluation;
4. dynamic initiating-event, control, ESD, and SIS response testing.

These calculations produce proposed or screening engineering. They do not establish credible scenarios, infer a SIL
target, approve an SRS, select a vendor, or make a package fit for construction.

## Run an isolated case envelope

`EngineeringCaseRunner` deep-copies the base `ProcessSystem` for every case. It can run the copies concurrently, but
orders results by case priority and ID so sequential and parallel runs produce the same result fingerprint.

```java
EngineeringCaseSet cases = new EngineeringCaseSet("export-envelope")
    .addCase(normalCase)
    .addCase(maximumRateCase)
    .addMetric(EngineeringMetric.equipmentPressure("20-KA-001"))
    .addMetric(EngineeringMetric.equipmentTemperature("20-KA-001"));

EngineeringCaseRunReport report = EngineeringCaseRunner.run(
    process,
    cases,
    EngineeringCaseRunOptions.builder().parallelism(2).requireConvergence(true).build());
```

The report contains separate definition and result SHA-256 fingerprints, convergence status for every case, isolated
metric failures, acceptance-limit verdicts, and the governing case for each metric. The base process is not modified.
Every configurator must record its controlled scalar inputs on the `EngineeringDesignCase`; a Java callback alone is
not a sufficient auditable design basis.

## Implement typed calculations

Implement `EngineeringCalculationModule<I, O>` for new discipline calculations. `assess` must report missing critical
inputs before physics is run. `calculate` returns `EngineeringCalculationResult<O>` with:

- stable calculation and method identifiers;
- independent method version;
- design-case and simulation fingerprints;
- immutable input snapshot;
- standards and evidence references;
- readiness blockers and warnings;
- optional numerical uncertainty interval;
- calculated, review-required, blocked, or failed status.

A result with readiness blockers cannot be marked calculated. Calculated status still does not grant engineering
approval.

## Couple relief, blowdown, and flare loads

`CoupledReliefBlowdownFlareCalculation` accepts project-defined `OverpressureProtectionStudy` objects grouped by
simultaneous event and a governed `DynamicBlowdownFlareStudyDataSource`. It evaluates:

- the governing credible scenario and PSV size for each protected item;
- accumulated-pressure and selected-orifice capacity;
- total steady relief load for each concurrency group;
- transient multi-source blowdown peak and source contributions;
- flare-header velocity and Mach acceptance;
- flare heat, mass, and molar capacity; and
- the governing steady or transient disposal-system load.

```java
CoupledReliefBlowdownFlareInput input =
    CoupledReliefBlowdownFlareInput.builder("flare-envelope-A")
        .addReliefStudy(separatorReliefStudy, "FIRE-ZONE-20")
        .addReliefStudy(scrubberReliefStudy, "FIRE-ZONE-20")
        .dynamicStudy(dynamicFlareStudy)
        .scenarioSelectionReviewed(true)
        .addEvidenceReference("HAZOP-20-REV-A")
        .build();

EngineeringCalculationResult<CoupledReliefBlowdownFlareResult> result =
    new CoupledReliefBlowdownFlareCalculation().calculate(input, context);
```

Concurrency and credibility must come from the project hazard review. NeqSim compares and aggregates the supplied
cases; it does not decide that they are credible or simultaneous.

## Verify dynamic protection response

`DynamicSafetyScenarioRunner` copies and initializes the process, applies an initiating event at a controlled time,
activates logic created against equipment in that copy, runs the transient process, and records scalar acceptance
criteria. Logic factories are used because an ESD action must point to copied valves, compressors, and controllers—not
objects in the live base model.

Each `DynamicScenarioCriterion` defines an observed quantity, engineering unit, acceptance range, response deadline,
and whether it is mandatory. The result includes complete time histories, first time satisfied, final logic states,
simulation errors, and an overall pass/fail verdict.

Use this for response verification such as:

- pressure below the declared safe-state limit within the SRS response budget;
- ESD valve below a maximum opening within its stroke-time allowance;
- compressor stopped before surge or temperature limits are exceeded;
- blowdown valve open and protected inventory below the target pressure; and
- permissive, override, reset, and restart sequence testing.

The framework verifies the supplied dynamic model. Sensor failure data, certified device architecture, SIL target,
independence, systematic capability, final trip set points, and lifecycle approval remain IEC 61511 project inputs.

## Run all configured layers from an engineering project

Attach executable cases, coupled safety studies, and dynamic scenarios to `EngineeringProject`, then run:

```java
EngineeringSimulationResult simulation = EngineeringSimulationRunner.run(
    project,
    EngineeringCaseRunOptions.builder().parallelism(4).build());
```

`SimulationEngineeringDesignRunner` includes this coordinated result in `coordinatedEngineeringSimulation`, allowing
the existing DEXPI exporter, engineering calculations, coverage matrices, evidence registers, and approval workflow to
carry the simulator evidence in the same governed package.

The executed [four-foundations notebook](https://github.com/equinor/neqsim/blob/master/examples/notebooks/engineering_simulator_four_foundations.ipynb)
demonstrates all layers and plots the dynamic ESD response.

## Verification checklist

- Every case runs on an independent process copy.
- Parallel and sequential results have identical fingerprints.
- Every governing metric comes from a converged case.
- Calculation modules expose their method version and exact input snapshot.
- Scenario credibility and concurrency are controlled project inputs.
- Flare capacity checks include both steady relief and transient blowdown peaks.
- Dynamic logic is built against the isolated model, not the live process.
- Every required safe-state criterion has a response deadline and unit.
- Failed, blocked, and review-required results remain visible in the package.
- No calculated result is labelled approved or fit for construction automatically.
