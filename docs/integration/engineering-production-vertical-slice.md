---
title: "Controlled-pilot production vertical slice"
description: "Qualify an inlet separator, compressor, cooler and export system without confusing simulator completion with engineering approval."
---

# Controlled-pilot production vertical slice

`ProductionVerticalSliceSimulator` is the first fail-closed acceptance layer above the general
process-to-engineering simulator. It targets one facility section:

```text
inlet separator -> compressor -> aftercooler -> export line
                         |              |
                         +-- recycle ---+

with PCV, level valve, PSV, BDV, suction/discharge ESDVs and a flare connection
```

The simulator still supports arbitrary preliminary engineering modules. The vertical-slice layer adds a controlled
topology and evidence contract so a partial model cannot accidentally be described as a completed production
workflow.

## Configure the run

Declare the numerical design policy separately from the qualification policy. This keeps calculation limits,
required process objects and external evidence independently revision-controlled.

```java
EngineeringAutoConfigurationPolicy designPolicy =
    new EngineeringAutoConfigurationPolicy("compression-design", "A")
        .addInletCompressionExportSlice(
            "20-VA-001", "20-KA-001", "20-PL-001", "20-PV-001", "20-PIT-001",
            800.0, 0.107, 120.0,       // separator basis
            20.0, 0.5,                // line velocity and pressure-gradient limits
            0.10,                     // driver margin
            3000.0, 5000.0, 7500.0, 10000.0)
        .addHeatExchanger("20-HA-001", 500.0, 25.0, 0.15,
            50.0, 75.0, 100.0, 150.0, 250.0)
        .addCompressorOperatingEnvelope("20-KA-001", 0.10, 0.05, 160.0, 0.10)
        .addReliefDevice("20-PSV-001", "20-VA-001", requiredReliefAreaMetric,
            0.110, 0.196, 0.307, 0.503, 0.785, 1.287, 1.838, 2.853);

InletCompressionExportSlicePolicy qualificationPolicy =
    InletCompressionExportSlicePolicy.builder("compression-pilot", "A")
        .processTags("20-VA-001", "20-KA-001", "20-HA-001", "20-PL-001")
        .controlTags("20-FV-001", "20-RECYCLE-001", "20-PV-001", "20-LV-001")
        .safetyTags("20-PSV-001", "20-BDV-001", "20-ESDV-001", "20-ESDV-002",
            "FLARE-HEADER-20")
        .addRequiredDynamicScenario("compressor-trip-esd")
        .addEvidenceReference("HAZOP-20-REV-A")
        .build();

ProductionVerticalSliceSimulator.Result result = ProductionVerticalSliceSimulator.runAndCompile(
    project, designPolicy, qualificationPolicy, 4, outputDirectory, baselineGraph);
```

Every policy evidence reference must resolve to an `EngineeringEvidenceRecord` on the project. A reference string by
itself does not pass the gate. The record must carry its revision, title, source organization and equipment or
requirement link; lifecycle approval is assessed separately.

Add all normal, minimum-turndown, maximum-production, start-up, shutdown, equipment-trip, settle-out,
blocked-outlet, fire and blowdown cases to the project before running. Each case must have controlled scalar inputs
and evidence references. Add the required executable dynamic scenario with
`VerticalSliceDynamicScenarioFactory.emergencyShutdown(...)`, and attach the coupled relief/blowdown/flare studies
selected by the approved hazard review.

## Compressor map evidence

`addCompressorOperatingEnvelope` runs the active compressor map across every case and records the operating point,
head, speed, efficiency, surge margin, stonewall margin, control-line margin, required recycle fraction, recycle
cooler duty, discharge temperature and extrapolation flag. The common design loop constrains the minimum margins,
maximum temperature and map extrapolation. Changing these limits changes the automatic-configuration fingerprint and
therefore invalidates dependent calculations and package artifacts.

The module requires an active map, surge curve and stonewall curve. It does not qualify vendor guarantees, starting
torque, rotor dynamics or the dynamic anti-surge controller. Those remain controlled vendor and project evidence.

## Qualification gates

The generated `engineering-vertical-slice-qualification.json` contains nine independent gates:

1. complete process and safety topology;
2. complete converged case matrix;
3. converged closed design loop;
4. required physical design variables;
5. compressor map and anti-surge envelope;
6. dynamic safe-state response;
7. coupled relief, blowdown and flare capacity;
8. versioned standards basis; and
9. controlled engineering evidence.

Missing data fails the relevant gate. The artifact is emitted even when the vertical-slice workflow was not
configured, so downstream systems have an explicit `NOT_CONFIGURED` result instead of inferring success from an
absent file.

The design loop also detects an exact two-state oscillation in discrete candidates (for example alternate pipe sizes
on successive iterations) and terminates with `DISCRETE_DESIGN_OSCILLATION_DETECTED` rather than exhausting the
iteration count without a useful diagnosis.

## Status boundary

Passing all nine gates means only `qualifiedForControlledPilot=true`. It always retains:

- `qualifiedForFeedSupport=false`;
- `fitnessForConstruction=false`;
- `finalEngineeringApprovalGranted=false`; and
- `engineeringApprovalRequired=true`.

Production method qualification remains governed by `EngineeringProductionReadinessAssessment`: independent
benchmarks, named-tool DEXPI qualification, accepted pilots, approved safety-lifecycle evidence and release-quality
evidence are separate requirements. HAZOP/LOPA/SRS decisions, scenario credibility, SIL, valve failure actions,
materials approval, vendor selection and final shutdown actions cannot be manufactured by the simulator.

See the [controlled-pilot notebook](../../examples/notebooks/engineering_vertical_slice_controlled_pilot.ipynb) for
the executable API pattern.
