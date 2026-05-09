---
title: "Chemistry and Integrity Modelling in NeqSim"
description: "Open standards-traceable framework for chemical integrity management — electrolyte scale prediction (Davies activity coefficients), mechanistic CO2 corrosion (NORSOK M-506 with Nesic mass-transfer + Langmuir inhibitor), packed-bed H2S scavenger breakthrough, closed-loop deposition coupling with pipe hydraulics, Bayesian root-cause analysis, and Monte Carlo uncertainty. Exposed via the NeqSim MCP server for AI-driven workflows."
---

# Chemistry and Integrity Modelling

NeqSim ships an open, standards-traceable chemistry stack for chemical-integrity
management of oil & gas, CCS and hydrogen systems. Every routine carries an
explicit list of source standards (NORSOK, NACE, ISO, API, ASTM) so results are
auditable.

All classes live under `neqsim.process.chemistry`. The same routines are
exposed over the [Model Context Protocol](../mcp/index.md) as `runChemistry`.

## Capability matrix

| Threat | Class | Model | Standards |
|--------|-------|-------|-----------|
| Mineral scale | `scale.ScalePredictionCalculator` | Oddo–Tomson SI | NACE TM0374 |
| Mineral scale (rigorous) | `scale.ElectrolyteScaleCalculator` | Davies activity-coeff SI for CaCO3, BaSO4, CaSO4, SrSO4 | NACE TM0374, NORSOK M-001 |
| Pipeline deposition | `scale.ScaleDepositionAccumulator` | Per-segment SI × velocity × time | NACE SP0775 |
| Closed-loop deposition | `scale.ClosedLoopDepositionSolver` | Iterates effective diameter against deposition | NACE SP0775 |
| CO2 corrosion | `corrosion.CorrosionCalculator` | NORSOK M-506 base | NORSOK M-506 |
| CO2 corrosion (mechanistic) | `corrosion.MechanisticCorrosionModel` | NORSOK + Nesic Sherwood mass-transfer + Langmuir inhibitor | NORSOK M-506, NACE SP0775 |
| Inhibitor selection | `corrosion.LangmuirInhibitorIsotherm` | Van't Hoff temperature-dependent Langmuir | NACE SP0775 |
| H2S scavenging | `scavenger.PackedBedScavengerReactor` | 1D plug-flow with capacity tracking | NACE TM0169 |
| Acid gas chemistry | `acid.AcidGasCalculator` | Equilibrium-based H2S/CO2 partitioning | NACE MR0175 / ISO 15156 |
| Hydrate / wax / asphaltene | `hydrate.*`, `wax.*`, `asphaltene.*` | NeqSim thermo + flash | API RP 87 |
| Root cause | `rca.RootCauseAnalyser` | Bayesian update across threats | — |

## Coupled workflows

The closed-loop deposition solver couples `PipeBeggsAndBrills` hydraulics with
`ScaleDepositionAccumulator` so velocity-dependent deposition is consistent
with the shrinking effective diameter:

```java
ClosedLoopDepositionSolver solver =
    new ClosedLoopDepositionSolver(pipe, accumulator)
        .setToleranceM(1e-4)
        .setMaxIterations(10);
solver.solve();
double dEff = solver.getFinalEffectiveDiameterM();
List<Double> velocityHistory = solver.getVelocityHistoryMs();
```

The packed-bed scavenger predicts breakthrough time for an H2S removal vessel:

```java
PackedBedScavengerReactor bed = new PackedBedScavengerReactor()
    .setGeometry(0.5, 2.0, 0.4)            // d_m, h_m, voidage
    .setMedia(5.0, 1100.0, 1.0)            // mol/kg loading, density, stoich
    .setRateConstant(8.0)
    .setFeed(2.0, 0.005)                   // c_inlet mol/m3, Q m3/s
    .setSimulationTime(60.0 * 24 * 3600.0, 0.05)
    .evaluate();
double tBreak = bed.getBreakthroughTimeS();
double removedKg = bed.getTotalH2sRemovedKg();
```

## MCP integration

Every analysis is exposed over the NeqSim MCP server:

```json
{
  "analysis": "mechanisticCorrosion",
  "temperature_C": 60,
  "pressure_bara": 80,
  "co2_mol": 0.05,
  "velocity_ms": 2.0,
  "diameter_m": 0.15,
  "dose_mgL": 50
}
```

Send this to the `runChemistry` tool. Supported `analysis` values:
`electrolyteScale`, `mechanisticCorrosion`, `langmuirInhibitor`,
`packedBedScavenger`.

See [MCP chemistry tool reference](mcp.md) for the full schema.

## Reproducibility

Every routine is covered by JUnit tests in
`src/test/java/neqsim/process/chemistry/` and
`src/test/java/neqsim/mcp/runners/ChemistryRunnerTest.java`. As of the latest
release, 47 chemistry tests pass and serve as the regression baseline.

## Related documentation

- [Chemical compatibility quickstart](../chemicalreactions/index.md)
- [Process safety overview](../safety/index.md)
- [Standards lookup](../standards/index.md)
- [Flow assurance overview](../process/index.md)
