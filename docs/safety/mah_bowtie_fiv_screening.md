---
title: "ISO 17776 MAH Bow-Tie and EI AVIFF Flow-Induced Vibration Screening"
description: "Generate a major-accident-hazard bow-tie from a pre-built ISO 17776 catalogue and screen piping circuits for flow-induced vibration using the Energy Institute AVIFF likelihood-of-failure method — MahBowTieBuilder, MahCatalogue, PipingFivScreening and FivLikelihoodResult."
keywords: "ISO 17776, MAH, major accident hazard, bow-tie, threats, barriers, consequences, Energy Institute, AVIFF, flow induced vibration, FIV, likelihood of failure, LOF, piping vibration"
---

# ISO 17776 MAH Bow-Tie and EI AVIFF Flow-Induced Vibration Screening

| Class | Purpose | Standard |
|-------|---------|----------|
| `MahCatalogue` | Pre-defined threats, consequences and barriers per MAH type | ISO 17776 |
| `MahBowTieBuilder` | Assemble a `BowTieModel` for a major-accident hazard | ISO 17776 |
| `PipingFivScreening` | Likelihood-of-failure screening for flow-induced vibration | Energy Institute AVIFF |
| `FivLikelihoodResult` | LOF score and likelihood band for one circuit | Energy Institute AVIFF |

Classes live under `neqsim.process.safety.hazid` and
`neqsim.process.safety.vibration`.

## MAH bow-tie from the ISO 17776 catalogue

`MahBowTieBuilder.build(MahType)` returns a fully populated
`BowTieModel` (threats on the left, consequences on the right, barriers in the
middle) for a standard major-accident-hazard type:

```java
import neqsim.process.safety.hazid.MahType;
import neqsim.process.safety.hazid.MahBowTieBuilder;
import neqsim.process.safety.hazid.MahCatalogue;
import neqsim.process.safety.risk.bowtie.BowTieModel;

BowTieModel bowtie = MahBowTieBuilder.build(MahType.TOPSIDE_HYDROCARBON_RELEASE);

String hazard = bowtie.getHazardId();
bowtie.getThreats();        // ≥ 4 threats, each with getFrequency()
bowtie.getConsequences();   // ≥ 3 consequences
bowtie.getBarriers();       // ≥ 5 barriers, each with getPfd()

// Inspect the raw catalogue entries directly
MahCatalogue.threatsFor(MahType.TOPSIDE_HYDROCARBON_RELEASE);
MahCatalogue.consequencesFor(MahType.TOPSIDE_HYDROCARBON_RELEASE);
MahCatalogue.barriersFor(MahType.TOPSIDE_HYDROCARBON_RELEASE);
```

Default threat frequency and barrier PFD are exposed as
`MahBowTieBuilder.DEFAULT_THREAT_FREQUENCY` and
`MahBowTieBuilder.DEFAULT_BARRIER_PFD`. `MahType` covers
`TOPSIDE_HYDROCARBON_RELEASE`, `RISER_LEAK`, `WELL_BLOWOUT`,
`STRUCTURAL_COLLAPSE`, `DROPPED_OBJECT`, `HELICOPTER_LOSS`, `SHIP_COLLISION`,
`FIRE_EXPLOSION`, `TOXIC_RELEASE`, `LOSS_OF_BUOYANCY`, and `EXTREME_WEATHER`,
each carrying a human-readable description.

## EI AVIFF flow-induced-vibration screening

`PipingFivScreening` computes an Energy Institute AVIFF likelihood-of-failure
(LOF) score for a piping circuit and maps it to a likelihood band. Use
`screenGas` or `screenLiquid` depending on the fluid:

```java
import neqsim.process.safety.vibration.PipingFivScreening;
import neqsim.process.safety.vibration.PipingFivLikelihood;
import neqsim.process.safety.vibration.FivLikelihoodResult;

// Gas circuit: tag, rho[kg/m3], v[m/s], D[m], wall t[m], nBranches, pulsation, support
FivLikelihoodResult gas = PipingFivScreening.screenGas(
    "Compressor discharge", 80.0, 30.0, 0.3, 0.006, 2, 4.0, 2.0);

double lof = gas.getLofScore();
PipingFivLikelihood band = gas.getLikelihood();   // LOW / MEDIUM / HIGH / VERY_HIGH
String json = gas.toJson();                        // contains "lofScore", "likelihood"

// Liquid circuit: tag, v[m/s], D[m], wall t[m], nBranches, support
FivLikelihoodResult liquid = PipingFivScreening.screenLiquid(
    "Pump discharge", 3.5, 0.15, 0.005, 1, 1.5);

// Map an arbitrary LOF score to a band
PipingFivLikelihood b = PipingFivScreening.bandFor(0.7);   // HIGH
```

The likelihood bands are `LOW` (< 0.3), `MEDIUM` (0.3–0.5), `HIGH` (0.5–1.0),
and `VERY_HIGH` (≥ 1.0). Invalid geometry (zero diameter, negative velocity)
throws `IllegalArgumentException`.

## Verification

```bash
./mvnw test -Dtest=MahBowTieBuilderTest,PipingFivScreeningTest
```

## Related Documentation

- [Event and Fault Trees](event_fault_trees.md)
- [Barrier Management and SCE Traceability](barrier_management.md)
- [Automated HAZOP from STID and Simulation](automated_hazop_from_stid.md)
