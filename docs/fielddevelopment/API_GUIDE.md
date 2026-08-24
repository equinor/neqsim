---
title: Field Development API Guide
description: Source-anchored guide to concept inputs, screening KPIs, option ranking, units, and engineering boundaries.
---

# Field Development API Guide

Use the field-development API to define early-phase concepts, run consistent screening, and compare options. The API
does not replace a reservoir model, a verified `ProcessSystem`, detailed equipment design, a safety study, or an
accountable economic model.

## Start with the right object

| Need | Current entry point | Result |
|---|---|---|
| Define reservoir, wells, and infrastructure | `FieldConcept.builder(name)` | Serializable concept with a stable ID |
| Create a conventional screening case | `FieldConcept.gasTieback(...)` or `oilDevelopment(...)` | Concept populated with documented defaults |
| Run all screening modules | `new ConceptEvaluator().evaluate(concept)` | `ConceptKPIs` with production, flow-assurance, safety, emissions, and screening-cost results |
| Run reduced-fidelity screening | `new ConceptEvaluator().quickScreen(concept)` | KPIs without the safety screen |
| Compare user-defined option scores | `DevelopmentOptionRanker` | Normalized, weighted ranking |
| Build a process model | `ConceptToProcessLinker.generateProcessSystem(...)` | Reusable `ProcessSystem` for a separate simulation and validation step |

The [field-development overview](README.md) provides the module map. Use the
[decision-engine guide](DECISION_ENGINE_WORKFLOWS.md) for tieback, portfolio, reservoir-export, and report workflows,
and the [lifecycle guide](FIELD_LIFECYCLE_SIMULATION.md) for time-series production and economics.

## Complete executable screening example

This Java program uses only current public signatures. The two NPV values are explicit scenario inputs; they are not
outputs from `ConceptEvaluator`.

```java
package neqsim.process.fielddevelopment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.evaluation.ConceptEvaluator;
import neqsim.process.fielddevelopment.evaluation.ConceptKPIs;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.Criterion;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.DevelopmentOption;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.RankingResult;

public final class FieldDevelopmentApiGuideExample {
  private static final Logger logger = LogManager.getLogger(FieldDevelopmentApiGuideExample.class);

  private FieldDevelopmentApiGuideExample() {}

  public static void main(String[] args) {
    FieldConcept tieback = FieldConcept.gasTieback("Gas tieback", 30.0, 2, 0.8);
    FieldConcept standalone = FieldConcept.oilDevelopment("Oil development", 6, 5000.0, 0.15);

    ConceptEvaluator evaluator = new ConceptEvaluator();
    ConceptKPIs tiebackKpis = evaluator.evaluate(tieback);
    ConceptKPIs standaloneKpis = evaluator.evaluate(standalone);

    DevelopmentOptionRanker ranker = new DevelopmentOptionRanker();
    ranker.setWeightProfile("balanced");

    DevelopmentOption tiebackOption = ranker.addOption(tieback.getName());
    tiebackOption.setScore(Criterion.NPV, 600.0);
    tiebackOption.setScore(Criterion.CO2_INTENSITY, tiebackKpis.getCo2IntensityKgPerBoe());
    tiebackOption.setScore(Criterion.TECHNICAL_RISK, 1.0 - tiebackKpis.getTechnicalScore());

    DevelopmentOption standaloneOption = ranker.addOption(standalone.getName());
    standaloneOption.setScore(Criterion.NPV, 750.0);
    standaloneOption.setScore(Criterion.CO2_INTENSITY, standaloneKpis.getCo2IntensityKgPerBoe());
    standaloneOption.setScore(Criterion.TECHNICAL_RISK, 1.0 - standaloneKpis.getTechnicalScore());

    RankingResult ranking = ranker.rank();
    logger.info("Tieback screening: {}", tiebackKpis.getOneLiner());
    logger.info("Standalone screening: {}", standaloneKpis.getOneLiner());
    logger.info("Highest screening score: {}", ranking.getBestOption().getName());
  }
}
```

The example proves API composition, not project selection. Replace every default and scenario score with traceable
project data before using the result in a decision process.

## Define inputs with explicit units

### Reservoir

`ReservoirInput` stores screening inputs; it does not build a thermodynamic `SystemInterface`.

```java
ReservoirInput reservoir = ReservoirInput.gasCondensate()
    .gor(5000.0, "Sm3/Sm3")
    .co2Percent(2.5)
    .h2sPercent(0.01)
    .waterCut(0.10)
    .waterSalinity(35000.0)
    .reservoirPressure(320.0)
    .reservoirTemperature(95.0)
    .resourceUncertainty(30.0, 24.0, 18.0, "GSm3")
    .recoveryFactor(0.65)
    .build();
```

- Pressure is bara and temperature is °C.
- `waterCut` and `recoveryFactor` are fractions from 0 to 1.
- `co2Percent` and `h2sPercent` are percentages, not fractions.
- Resource uncertainty follows the repository's existing P10/P50/P90 naming. Verify the probability convention used
  by the accountable subsurface study before transferring values.
- Component names and mole fractions supplied with `addComponent(...)` are stored as metadata; the builder does not
  normalize the composition or create a fluid.

### Wells

```java
WellsInput wells = WellsInput.builder()
    .producerCount(4)
    .injectorCount(2)
    .producerType(WellsInput.WellType.GAS_LIFT)
    .completionType(WellsInput.CompletionType.SUBSEA)
    .tubeheadPressure(100.0)
    .ratePerWell(0.8, "MSm3/d")
    .shutInPressure(250.0)
    .productivityIndex(10000.0)
    .gasLift(0.15, "MSm3/d")
    .build();
```

`ratePerWell(rate, unit)` preserves the value and unit string. `getRatePerWellSm3d()` recognizes `Sm3/d`, `Sm3/day`,
`MSm3/d`, `bbl/d`, and `bopd`; other strings fall through without conversion. Keep the unit spelling exact and check
whether an oil or gas basis is appropriate before aggregating rates.

### Infrastructure

```java
InfrastructureInput infrastructure = InfrastructureInput.subseaTieback()
    .tiebackLength(30.0)
    .waterDepth(350.0)
    .exportPipeline(85.0, 20.0)
    .ambientTemperatures(6.0, 10.0)
    .exportType(InfrastructureInput.ExportType.WET_GAS)
    .hostCapacityAvailable(0.25)
    .insulatedFlowline(true)
    .electricHeating(false)
    .build();
```

Tieback and pipeline lengths are km, water depth is m, pipeline diameter is inches, and temperatures are °C.
`exportPressure(double)` currently does not store an override; `getExportPressure()` returns a default derived from
`ExportType`. Do not present that default as a project design pressure.

### Assemble the concept

```java
FieldConcept concept = FieldConcept.builder("Gas-condensate tieback")
    .id("asset:gas-condensate-tieback:screening-v1")
    .description("Public screening assumptions; replace with controlled project inputs")
    .reservoir(reservoir)
    .wells(wells)
    .infrastructure(infrastructure)
    .build();
```

Set `id(...)` when results must be joined across serialized runs, reports, diagrams, or control mappings. Without an
explicit ID, the builder creates a random UUID. The concept is serializable, but the input values still need external
provenance and version control.

## Understand evaluation ownership

### Full screening

`evaluate(concept)` calls `FacilityBuilder.autoGenerate(concept)`, then runs:

1. a production-profile estimate;
2. flow-assurance screening;
3. safety screening;
4. emissions estimation;
5. screening-level CAPEX/OPEX estimation;
6. technical, economic, environmental, and overall scores.

Use the exact getters on `ConceptKPIs`:

| Quantity | Getter | Unit or type |
|---|---|---|
| Plateau rate | `getPlateauRateMsm3d()` | MSm³/d |
| Estimated recovery | `getEstimatedRecoveryPercent()` | percent |
| Field life | `getFieldLifeYears()` | years |
| Screening CAPEX | `getTotalCapexMUSD()` | MUSD |
| Screening OPEX | `getAnnualOpexMUSD()` | MUSD/year |
| CO₂ intensity | `getCo2IntensityKgPerBoe()` | kg CO₂e/boe |
| Annual emissions | `getAnnualEmissionsTonnes()` | tonne/year |
| Hydrate and wax margins | `getHydrateMarginC()`, `getWaxMarginC()` | °C |
| Screening disposition | `getFlowAssuranceOverall()`, `getSafetyLevel()` | enums |
| Messages | `getNotes()`, `getWarnings()` | defensive-copy maps |

`ConceptEvaluator` does not currently populate `getNpv10MUSD()` or `getBreakEvenOilPriceUSD()`. A zero returned by
those getters is not an economic result. Use the lifecycle/economics workflows and document prices, tax, exchange
rate, discount date, CAPEX schedule, and uncertainty basis before ranking on NPV or breakeven price.

### Reduced-fidelity screening

`quickScreen(concept)` omits the safety screen and uses simplified emissions and economics estimates. Its
`getSafetyReport()` is therefore `null`. Do not use quick-screen output to claim ESD, blowdown, minimum-metal-
temperature, relief, HAZOP, SIL, or design-code adequacy.

### Provide a facility explicitly

Use `evaluate(concept, facilityConfig)` when the screening must use a specific `FacilityConfig`. The configuration is
still a screening representation. Build and run a `ProcessSystem` separately when thermodynamic states, equipment
duties, recycles, convergence, mass balance, or constraint utilization matter.

## Rank alternatives without hiding assumptions

`DevelopmentOptionRanker` normalizes each populated criterion across the options in the current call. Higher-is-
better criteria retain their direction; lower-is-better criteria are inverted. Missing scores are skipped for that
option. The weighted score is:

$$S_i=\frac{\sum_{j\in J_i}w_j\hat{x}_{ij}}{\sum_{j\in J_i}w_j}$$

where $J_i$ is the set of populated criteria for option $i$, $w_j$ is the configured weight, and
$\hat{x}_{ij}$ is the min-max-normalized directional score.

Consequences:

- keep the same populated criteria for every option;
- record the source, date, unit, and uncertainty of every raw score;
- remember that adding or removing an option changes min-max normalization;
- use profile names `economic`, `environmental`, `risk`, or `balanced`; unrecognized names fall back to `balanced`;
- inspect `DevelopmentOption.getWeightedScore()` or `RankingResult.getBestOption()`—there is no
  `RankingResult.getWeightedScore(option)` method.

The ranker is a transparent screening utility, not an MCDA governance process. Weight approval, uncertainty,
sensitivity, and accountable decision records remain outside this class.

## Move from screening to simulation

Use [integrated field development](INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK.md) and
[integrated production modelling](INTEGRATED_PRODUCTION_MODELLING.md) for the next fidelity step. A typical chain is:

`FieldConcept → FacilityConfig → ProcessSystem → simulation and balances → lifecycle profiles → economics → ranking`

Keep these boundaries explicit:

- `FieldConcept` stores screening inputs and identity; it is not a fluid or process model.
- `ConceptEvaluator` returns correlation- and assumption-based screening results.
- `ConceptToProcessLinker` creates a reusable process-system starting point; run it and validate its thermodynamics,
  convergence, mass closure, equipment limits, and product specifications.
- `DevelopmentOptionRanker` consumes scores supplied by the study; it does not calculate NPV, risk, or emissions.
- Outputs are preliminary engineering evidence, not design certification or safety approval.

## Source navigation

- [`FieldConcept`](../../src/main/java/neqsim/process/fielddevelopment/concept/FieldConcept.java),
  [`ReservoirInput`](../../src/main/java/neqsim/process/fielddevelopment/concept/ReservoirInput.java),
  [`WellsInput`](../../src/main/java/neqsim/process/fielddevelopment/concept/WellsInput.java), and
  [`InfrastructureInput`](../../src/main/java/neqsim/process/fielddevelopment/concept/InfrastructureInput.java)
- [`ConceptEvaluator`](../../src/main/java/neqsim/process/fielddevelopment/evaluation/ConceptEvaluator.java) and
  [`ConceptKPIs`](../../src/main/java/neqsim/process/fielddevelopment/evaluation/ConceptKPIs.java)
- [`DevelopmentOptionRanker`](../../src/main/java/neqsim/process/fielddevelopment/evaluation/DevelopmentOptionRanker.java)
- [`ConceptToProcessLinker`](../../src/main/java/neqsim/process/fielddevelopment/facility/ConceptToProcessLinker.java)
- [`FieldLifecycleConfiguration`](../../src/main/java/neqsim/process/fielddevelopment/lifecycle/FieldLifecycleConfiguration.java)

## Verification checklist

Before reusing a screening result:

1. record NeqSim version, concept ID, input source, units, and reference date;
2. replace all convenience-factory defaults with reviewed project assumptions;
3. distinguish calculated screening values from user-supplied ranking scores;
4. run a composable `ProcessSystem` for claims that require thermodynamic or equipment behavior;
5. verify mass and energy balances, convergence, nearby cases, constraints, and product specifications;
6. perform separate economics, uncertainty, risk, safety, and design-code reviews at the required project maturity.
