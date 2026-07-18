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

### Start from the executable reference facility

`InletCompressionExportReferenceFacility` is the regression-quality vertical slice used to prove that the complete
workflow can run. It builds an actually connected process graph, rather than a collection of required tags:

- separator liquid outlet to LCV;
- separator gas outlet through suction ESDV and recycle mixer to the compressor;
- compressor discharge through the aftercooler, discharge ESDV, PCV and export line;
- aftercooler discharge through the ASV, recycle cooler and recycle tear stream to compressor suction; and
- aftercooler discharge through the PSV and BDV to a common flare connection.

It also supplies ten evidence-linked cases, an active compressor map, a dynamic anti-surge/ESD/blowdown response,
an API-style blocked-outlet PSV metric, a coupled relief/blowdown/flare study, a shutdown sequence, piping rules and
installed relief-device data.

```java
InletCompressionExportReferenceFacility.Definition reference =
    InletCompressionExportReferenceFacility.build();

ProductionVerticalSliceSimulator.Result result =
    ProductionVerticalSliceSimulator.runStrictAndCompile(
        reference.getProject(),
        reference.getAutoConfigurationPolicy(),
        reference.getQualificationPolicy(),
        1,
        outputDirectory,
        null);
```

The embedded values and evidence are explicitly labelled synthetic. Copy the object structure, but replace every
case, map, scenario, material, line, valve, PSV and flare input with controlled project evidence. The reference can
qualify for a controlled software pilot; it can never grant construction approval.

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

ProductionVerticalSliceSimulator.Result result = ProductionVerticalSliceSimulator.runStrictAndCompile(
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

For Python/JPype clients, `VerticalSliceCaseMatrixFactory` creates serializable Java configurators from explicit feed
flow, feed pressure, feed temperature and compressor discharge-pressure inputs. `applyTo(project, policy)` refuses an
incomplete required-type matrix. The factory changes only steady boundary conditions; start-up, trip, fire, blocked
outlet and blowdown event actions and credibility must still be represented by controlled scenario definitions.

## Preflight before simulation

Use `ProductionVerticalSlicePreflight.assess(project, qualificationPolicy)` while assembling a project. It checks the
controlled definitions without running thermodynamics or the engineering loop:

- complete typed process, control and safety topology;
- directed stream connectivity for the main process, recycle, liquid-control and relief/blowdown paths;
- one enabled definition for every required case type, with scalar inputs and resolvable evidence;
- active compressor map, surge curve and stonewall curve;
- executable dynamic scenarios with logic, criteria and evidence;
- reviewed relief concurrency coupled to calculation-ready blowdown and flare inputs;
- controlled project editions of every required standard; and
- complete, revision-controlled evidence records linked to engineering objects.

Warnings retain review-required or unapproved lifecycle status. Blockers mean the numerical run must not start.
`runStrict(...)` and `runStrictAndCompile(...)` throw before automatic module configuration when blockers exist.
The existing `run(...)` method remains available for exploratory gap analysis and returns the preflight result together
with the eventual qualification findings.

Every attempted vertical-slice execution also creates a SHA-256 input fingerprint over the project revision, both
policy revisions, case definitions and scalar inputs, dynamic scenarios, coupled safety studies, standards and
evidence. The coordinated package writes this as
`engineering-vertical-slice-execution-manifest.json`. Changing any controlled input invalidates the fingerprint and
provides a deterministic trigger for recalculation and revision-impact review.

## Compressor map evidence

`addCompressorOperatingEnvelope` runs the active compressor map across every case and records the operating point,
head, speed, efficiency, surge margin, stonewall margin, control-line margin, required recycle fraction, recycle
cooler duty, discharge temperature and extrapolation flag. The common design loop constrains the minimum margins,
maximum temperature and map extrapolation. Changing these limits changes the automatic-configuration fingerprint and
therefore invalidates dependent calculations and package artifacts.

The module requires an active map, surge curve and stonewall curve. It does not by itself qualify vendor guarantees,
starting torque, rotor dynamics or the dynamic anti-surge controller. Supply those controlled results to
`CompressorProtectionQualificationCalculation`; its checks remain review-required and cannot replace vendor or
accountable machinery approval.

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

When multiple modules propose the same physical variable, the later discipline module in deterministic execution
order owns the applied update. This allows a network-level piping module to supersede a preliminary single-line
screen without producing an artificial two-state oscillation. The loop still detects a genuine exact two-state
oscillation in discrete candidates and terminates with `DISCRETE_DESIGN_OSCILLATION_DETECTED`.

## Status boundary

Passing all nine gates means only `qualifiedForControlledPilot=true`. It always retains:

- `qualifiedForFeedSupport=false`;
- `fitnessForConstruction=false`;
- `finalEngineeringApprovalGranted=false`; and
- `engineeringApprovalRequired=true`.

Production method qualification remains governed by `EngineeringProductionReadinessAssessment`: independent
benchmarks, named-tool DEXPI qualification, accepted pilots, approved safety-lifecycle evidence and release-quality
evidence are separate requirements. The assessment also has independent gates for distributed piping transients,
compressor protection/machinery, valve/instrument response and installation, detailed mechanical integrity, and flare
radiation/dispersion/noise. HAZOP/LOPA/SRS decisions, scenario credibility, SIL, valve failure actions, materials
approval, vendor selection and final shutdown actions cannot be manufactured by the simulator.

See the [controlled-pilot notebook](https://github.com/equinor/neqsim/blob/master/examples/notebooks/engineering_vertical_slice_controlled_pilot.ipynb) for
the executable API pattern.
