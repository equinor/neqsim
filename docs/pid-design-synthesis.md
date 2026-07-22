---
title: "Governed P&ID Design Synthesis"
description: "Generate a traceable, review-required P&ID proposal, completeness assessment, HAZOP preparation, and DEXPI engineering package from a governed NeqSim project."
keywords: "P&ID synthesis, DEXPI, HAZOP preparation, safeguarding, control instrumentation, NORSOK"
---

# Governed P&ID design synthesis

For the relationship between P&ID proposal synthesis, native DEXPI 2.0 Plant exchange, Proteus/pyDEXPI compatibility,
validation, and named-tool qualification, see the [DEXPI Engineering Guide](engineering/dexpi-guide).

NeqSim can generate a reviewable P&ID proposal from the same engineering project that owns the
process simulation, requirements, relief studies and DEXPI export. The generator deliberately does
not claim that an automatically produced drawing is approved or fit for construction.

```java
PidDesignModel pid = PidDesignSynthesizer.synthesize(project,
    new PidDesignBasis("NORSOK-COMPLETE-PID-PROPOSALS", "20"),
    NorsokPidRuleCatalog.completeProposals());

PidCompletenessReport completeness = PidCompletenessValidator.validate(pid);
PidHazopStudyReport hazopPreparation = PidHazopStudyRunner.run(project, pid);
PidEngineeringPackageExporter.ExportResult exported =
    PidEngineeringPackageExporter.export(project, pid, outputDirectory);
```

## Implemented proposal scope

`NorsokPidRuleCatalog.controlAndInstrumentation()` proposes conventional ISA-style measurement and control loops for
separators, compressors, pumps, heaters, coolers, and exchangers. Set points, ranges, failure actions, installation
details, and control narratives remain project inputs.

`NorsokPidRuleCatalog.completeProposals()` adds process topology, equipment safeguarding, pressure relief, shutdown
and isolation valves, non-return valves, blowdown, drains, and vents where the governed project basis supports them.
The implemented rule families are independently reviewable and cover:

- process-topology connections;
- separator control and safeguarding;
- compressor control, anti-surge interfaces, and safeguarding;
- pump control and safeguarding;
- heater/cooler/exchanger control and safeguarding; and
- pressure-relief proposals linked to overpressure studies.

Every proposal retains its source rule, engineering rationale, standards, requirement IDs, equipment identity, and
graph connections.

## Completeness and HAZOP preparation

`PidCompletenessValidator` checks tag uniqueness, references, connections, and minimum functional categories.
`PidHazopStudyRunner` then prepares one traceable HAZOP node per process item, generates parameter/guide-word
deviations, links matching proposed safeguards, reports unresolved connections, and leaves every workshop decision
`OPEN`.

This is preparation for an IEC 61882 multidisciplinary workshop. It does not establish credible causes, consequences,
IPL independence, residual risk, action closure, or approval. Reviewed outcomes can continue through the
[HAZOP and LOPA to Draft SRS Handoff](process/safety/hazop-lopa-srs-handoff).

## Package contents

The package contains the native DEXPI document, Proteus and PyDEXPI exchange documents,
`pid-design-model.json`, `pid-completeness-report.json`, and `pid-hazop-study.json`. Missing proposal tags are
materialized as DEXPI instrumentation functions so an independent PyDEXPI import and render visibly exercises the
generated design. The sidecars preserve details that a P&ID exchange profile cannot represent losslessly.

The exporter refreshes the engineering package manifest after P&ID elements are materialized into the DEXPI and
pyDEXPI documents.

## Status boundary

`structurallyComplete` means that references and minimum functional categories passed automated
checks. `readyForApproval` remains false until discipline and HAZOP/LOPA review findings are closed.
`fitnessForConstruction` is always false for generated proposals.

The executable notebook and its generated package are retained as GitHub Actions artifacts for
independent review.
The CI contract also runs Spotless and the focused P&ID test suite before rendering.

See the
[`complete_pid_design_synthesis.ipynb`](https://github.com/equinor/neqsim/blob/master/examples/notebooks/complete_pid_design_synthesis.ipynb)
notebook for the executable workflow.
