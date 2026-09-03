---
title: Qualified CO2 impurity-kinetics execution
description: Fail-closed evidence binding for the experimental CO2 impurity kinetic reactor.
---

`CO2ImpurityKineticReactor` contains an experimental R1-R8 network with illustrative Arrhenius
parameters. Those defaults are useful for software and sensitivity testing, but they are not
validated engineering correlations. `QualifiedCO2ImpurityKineticReactor` provides an explicit
fail-closed execution path without changing the legacy experimental class.

## Evidence registry

Before execution, register one immutable `KineticReactionQualification` for each homogeneous
parameterization R1, R2, R3A, R3B, and R4-R7. Register the selected heterogeneous family as R8CS
for carbon steel or magnetite, or R8SS for stainless steel or inert material.

Each qualification records a public source, stable source identifier, validation status,
temperature range, pressure range, and limitations. The qualification reaction name must match the
normalized reaction identifier. This prevents evidence for one parameterization from being bound
silently to another.

```java
QualifiedCO2ImpurityKineticReactor reactor =
    new QualifiedCO2ImpurityKineticReactor("qualified impurity reactor", inlet);

reactor.setReactionQualification(
    "R1",
    new KineticReactionQualification(
        "R1",
        "Author, title, journal and year",
        "doi:replace-with-public-primary-source",
        ChemicalReactionValidationStatus.VALIDATED,
        minimumTemperatureK,
        maximumTemperatureK,
        minimumPressureBara,
        maximumPressureBara,
        "Exact composition, phase, rate basis, material and uncertainty limits"));
```

The example intentionally uses placeholders rather than inventing a kinetic source or validity
range. Qualified execution requires corresponding metadata for every required identifier.
`getUnqualifiedReactionIds(T, P)` reports missing, unvalidated, and out-of-range entries.
`requireValidatedKineticsAt(T, P)` applies the same gate without running the reactor.

## Structured preflight report

`getQualificationReport(T, P)` returns an immutable, source-ordered entry for every required
reaction identifier. Each entry is classified as `QUALIFIED`, `MISSING`, `NOT_VALIDATED`, or
`OUT_OF_RANGE`. Missing metadata takes precedence over validation and range checks; an explicitly
non-validated entry takes precedence over its range. The report records the evaluated temperature,
absolute pressure, and material selection so the selected R8 family remains auditable.

`getUnqualifiedReactionIds(T, P)` delegates to the same report and therefore preserves the existing
fail-closed result and identifier ordering. The report is diagnostic evidence only: it neither
executes chemistry nor supplies citations, parameter values, validation status, or validity ranges.

## Parameter binding and execution

Calling `setReactionConstants(...)` replaces an Arrhenius parameter pair and automatically removes
the qualification bound to that identifier. R8 resolves to the currently selected material family,
so changing R8 constants invalidates R8CS or R8SS as appropriate. Changing wall material selects
the other R8 family and therefore requires its own evidence.

Both `run()` and `run(UUID)` reach the overridden UUID execution path. The inlet temperature and
absolute pressure must lie inside every registered range before the inherited reactor calculation
can start. This gate establishes evidence completeness for the configured parameterizations; it
does not prove that the physical phase, concentrations, wall condition, reaction mechanism, or
complete transport case match the cited experiments.

## Scientific boundary

This capability adds no kinetic constant, reaction order, stoichiometry, material factor, fitted
parameter, or validation claim. A `VALIDATED` entry must refer to independent public evidence for
the exact configured parameterization and its units. Merely registering metadata does not make the
illustrative defaults valid.

The gate does not calculate gas-to-water transfer, free-water appearance, electrolyte activities,
pH, corrosion, scale, reaction heat, flash equilibrium, or pipeline source terms. Coordination with
the electrolyte, flash, dynamics, pipeline, and MCP roadmaps remains unchanged. Facility-specific
Northern Lights data must not be inserted into this registry.

See the [CO2 impurity kinetics guide](co2_impurity_kinetics_guide) for the experimental network and
the [CO2 transport reaction-kinetics guide](co2_transport_reaction_kinetics) for the wider evidence
and transport-coupling contract.
