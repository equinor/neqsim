# Governed P&ID design synthesis

NeqSim can generate a reviewable P&ID proposal from the same engineering project that owns the
process simulation, requirements, relief studies and DEXPI export. The generator deliberately does
not claim that an automatically produced drawing is approved or fit for construction.

```java
PidDesignModel pid = PidDesignSynthesizer.synthesize(project,
    new PidDesignBasis("NORSOK-COMPLETE-PID-PROPOSALS", "20"),
    NorsokPidRuleCatalog.completeProposals());

PidCompletenessReport completeness = PidCompletenessValidator.validate(pid);
PidEngineeringPackageExporter.ExportResult exported =
    PidEngineeringPackageExporter.export(project, pid, outputDirectory);
```

The complete proposal profile covers measurements, controllers, control valves, alarms, trips,
shutdown and isolation valves, non-return valves, blowdown, drains, vents and relief devices where
an overpressure study exists. Every proposal retains its source rule, engineering rationale,
standards, requirement IDs and graph connections.

The package contains the native DEXPI document, Proteus and PyDEXPI exchange documents,
`pid-design-model.json` and `pid-completeness-report.json`. Missing proposal tags are materialized as
DEXPI instrumentation functions so an independent PyDEXPI import and render visibly exercises the
generated design. The sidecars preserve details that a P&ID exchange profile cannot represent
losslessly.

`structurallyComplete` means that references and minimum functional categories passed automated
checks. `readyForApproval` remains false until discipline and HAZOP/LOPA review findings are closed.
`fitnessForConstruction` is always false for generated proposals.

The executable notebook and its generated package are retained as GitHub Actions artifacts for
independent review.
The CI contract also runs Spotless and the focused P&ID test suite before rendering.
