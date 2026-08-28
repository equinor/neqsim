---
title: "Refinery Assay and TBP Cut Characterization"
description: "Create mass- or volume-basis refinery assay cuts, ingest pre-binned TBP boundaries, and generate NeqSim pseudo-components with explicit units and mass closure."
---

# Refinery Assay and TBP Cut Characterization

`OilAssayCharacterisation` is the first refinery-specific assay entry point in NeqSim. It converts a pre-binned crude or petroleum-fraction assay into TBP pseudo-components that can be used by the existing thermodynamic and `ProcessSystem` APIs.

This API is intentionally conservative. It handles **assay representation and pseudo-component generation**; it does not claim to replace a laboratory assay package or to convert ASTM D86/D1160 data to true boiling point (TBP).

## Scope and unit contract

| Quantity | API basis | Notes |
| --- | --- | --- |
| Assay mass | kg | `setTotalAssayMass(...)` |
| Molar mass | kg/mol | Use `withMolarMassKgPerMol(...)`; `withMolarMassGramPerMol(...)` is provided for assay tables reported in g/mol |
| Specific gravity | dimensionless | Numerically equivalent to density in g/cm3 for the petroleum correlations used here |
| Density | kg/m3 | Use `withDensityKgPerCubicMetre(...)` for explicit SI density input |
| API gravity | degrees API | Negative API gravity is supported for liquids denser than water |
| Boiling temperature | K, degC, or degF | Unit-explicit builder methods are provided |
| Cut yield | mass fraction or liquid-volume fraction | One assay must use exactly one basis; mass and volume bases cannot be mixed |

The historical `withMolarMass(double)` method remains available and means **kg/mol**. Values such as `150.0` therefore mean 150 kg/mol, not 150 g/mol. For a 150 g/mol petroleum cut, use `withMolarMassKgPerMol(0.150)` or `withMolarMassGramPerMol(150.0)`.

## Why the fraction basis is explicit

A refinery assay is commonly reported on either a weight basis or a liquid-volume basis. Combining a 40 wt% cut with a 60 vol% cut does not define one physical composition unless an additional conversion basis is supplied.

NeqSim therefore requires all cuts in one `OilAssayCharacterisation` to use the same basis:

- **mass basis:** mass fractions are used directly;
- **volume basis:** each cut yield is multiplied by its liquid density and the resulting masses are normalized;
- **mixed basis:** rejected before the thermodynamic system is mutated.

The declared fractions must close to unity within 0.001. This tolerance permits ordinary assay-table rounding but rejects incomplete assays rather than silently scaling a materially incomplete slate to 100%.

## Java: pre-binned mass-basis assay

```java
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface crude = new SystemSrkEos(298.15, 1.01325);
OilAssayCharacterisation assay = crude.getOilAssayCharacterisation();
assay.clearCuts();
assay.setTotalAssayMass(1.0); // kg basis

assay.addCut(new AssayCut("Naphtha")
    .withWeightPercent(18.0)
    .withSpecificGravity(0.72)
    .withBoilingRangeCelsius(90.0, 180.0));
assay.addCut(new AssayCut("Kerosene")
    .withWeightPercent(22.0)
    .withSpecificGravity(0.79)
    .withBoilingRangeCelsius(180.0, 250.0));
assay.addCut(new AssayCut("GasOil")
    .withWeightPercent(35.0)
    .withSpecificGravity(0.86)
    .withBoilingRangeCelsius(250.0, 380.0));
assay.addCut(new AssayCut("Residue")
    .withWeightPercent(25.0)
    .withApiGravity(12.0)
    .withBoilingRangeCelsius(380.0, 560.0));

assay.apply();
```

`apply()` resolves every cut before adding the first component, checks assay closure, calculates cut moles from the configured mass basis, and rejects duplicate/repeated application if the generated pseudo-component already exists. Every strictly positive normalized mass fraction is retained, including trace cuts below ordinary comparison tolerances; only exact-zero cuts are omitted so trace handling remains consistent with the reconstructed-mass closure gate.

## Java: cumulative TBP cut boundaries

For a pre-binned true-boiling-point curve, the cumulative liquid-volume yields can be converted directly into assay cuts:

```java
SystemInterface crude = new SystemSrkEos(298.15, 1.01325);
OilAssayCharacterisation assay = crude.getOilAssayCharacterisation();
assay.clearCuts();

// Boundary points: cumulative liquid-volume percent and TBP temperature.
double[] cumulativeVolumePercent = {0.0, 20.0, 55.0, 80.0, 100.0};
double[] tbpCelsius = {90.0, 180.0, 270.0, 380.0, 520.0};

// One specific gravity for each interval.
double[] specificGravity = {0.70, 0.76, 0.84, 0.93};

assay.addTBPCutBoundariesCelsius(
    "TBP", cumulativeVolumePercent, tbpCelsius, specificGravity);
assay.apply();
```

This creates `TBP1_PC` through `TBP4_PC`. Each `AssayCut` retains its lower and upper TBP boundaries. The interval midpoint is used as the representative boiling temperature for the existing NeqSim molecular-weight/petroleum-property correlation.

The input is required to span 0 to 100 liquid-volume percent with strictly increasing yield and temperature boundaries. This is deliberate: the method represents a complete, already-TBP cut table and does not invent unmeasured light or residue tails.

## Volume-basis conversion

For cut volume fractions `v_i` and cut densities `rho_i`, NeqSim first calculates the normalized mass fraction

$$w_i=\frac{v_i\rho_i}{\sum_j v_j\rho_j}$$

and then calculates the pseudo-component amount on the configured assay mass basis `m_assay`:

$$n_i=\frac{m_{assay}w_i}{M_i}$$

where `M_i` is in kg/mol. Before any component is added, the implementation verifies that the reconstructed mass from all generated amounts closes to the configured assay mass.

## Density and API gravity

Use an explicit density method when possible:

```java
AssayCut cutA = new AssayCut("CutA").withSpecificGravity(0.85);
AssayCut cutB = new AssayCut("CutB").withDensityKgPerCubicMetre(850.0);
AssayCut cutC = new AssayCut("CutC").withApiGravity(34.97);
```

`withDensity(double)` is retained for compatibility. Values up to 1.5 are interpreted as specific gravity/g/cm3; larger values are treated as kg/m3 and divided by 1000. New refinery code should prefer the unit-explicit methods above.

The API-gravity conversion follows the conventional relation

$$SG_{60/60}=\frac{141.5}{API+131.5}$$

with the existing NeqSim 60 degF water-density reference. Negative API gravities are accepted when physically meaningful; the singular range at and below -131.5 degrees API is rejected.

## Existing petroleum-property correlations

This refinery-assay API deliberately reuses the existing NeqSim TBP/pseudo-component property framework. It does not add or retune critical-property, acentric-factor, or molecular-weight coefficients.

The current implementation and its literature lineage are documented in:

- [Fluid Characterization Mathematical Foundations](../../pvtsimulation/fluid_characterization_mathematics.md)
- [TBP Fraction Models](../../wiki/tbp_fraction_models.md)
- [PVT Fluid Characterization](../pvt_fluid_characterization.md)

Relevant published foundations already used by NeqSim include petroleum-fraction characterization work by Watson, Nelson and Murphy (1935), Winn (1957), Riazi and Daubert (1980/1987), Pedersen, Thomassen and Fredenslund (1984), and Whitson (1983). This increment changes the **assay input contract and validation**, not those scientific coefficients.

## Python accessibility

`OilAssayCharacterisation` and `AssayCut` are public Java classes and are therefore reachable through the normal NeqSim Python/JVM gateway. The unit and basis contracts are identical from Python. A dedicated refinery notebook is planned after the characterization API has passed the full Java CI matrix and a public refinery benchmark dataset has been selected.

## Validation boundary

The regression suite for this API currently verifies:

- mass-basis cut conversion;
- volume-to-mass conversion and closure;
- kg/mol and g/mol explicit molar-mass input;
- kg/m3 density normalization;
- API-gravity handling, including negative API gravity;
- rounding-scale closure versus incomplete-assay rejection;
- rejection of mixed mass/volume bases and duplicate cuts;
- TBP boundary monotonicity, complete 0-100% coverage, stored boiling ranges, and generated pseudo-components;
- repeated-application protection;
- exact reconstructed assay-mass closure.

These tests establish software and bookkeeping correctness. They are **not yet an independent refinery-property validation dataset**. Experimental/public-data qualification of pseudo-component properties and atmospheric/vacuum fractionation is a separate campaign gate in issue #3305.

## Refinery capability inventory after this increment

| Capability | Current NeqSim foundation | Campaign status |
| --- | --- | --- |
| Pre-binned crude/petroleum assay cuts | `OilAssayCharacterisation` | Foundation hardened in #3305 |
| Mass- and volume-basis cut yields | Unit/basis-explicit assay API | Foundation hardened in #3305 |
| Pre-binned cumulative TBP cut boundaries | `addTBPCutBoundariesCelsius/Kelvin` | Initial implementation in #3305 |
| TBP pseudo-component properties | Pedersen, Lee-Kesler, Riazi-Daubert, Twu, Cavett, Standing and related models | Existing; needs refinery-range independent validation |
| Plus-fraction splitting/lumping | `Characterise`, plus-fraction and lumping models | Existing; refinery assay integration still to be qualified |
| Oil density/API and volatility standards | Oil-quality standards package, RVP/TVP workflows | Existing pieces; unified refinery stream-property API remains open |
| Rigorous distillation columns | `DistillationColumn`, `SimpleTray`, Naphtali-Sandholm solver, side-draw support | Existing foundation; broad-boiling atmospheric/vacuum refinery benchmark remains open |
| Crude preheat/fired heater | General heater/heat-exchanger process equipment | Refinery workflow and fuel/emission integration remain open |
| Product blending/specification optimization | Generic optimization/process facilities | Refinery property/blending framework remains open |
| ASTM D86/D1160 to TBP conversion | No qualified refinery conversion API in this increment | Open; requires public correlation provenance and validation |
| Atmospheric crude-unit benchmark | No campaign benchmark yet | Next high-value validation milestone |
| Vacuum tower benchmark | No campaign benchmark yet | Open after atmospheric case |
| Hydrotreating/reforming/FCC/hydrocracking | No campaign-qualified refinery conversion models | Deliberately later phase |

## Next campaign step

The next dependency-ready increment should select an openly reproducible refinery assay/reference case and use it to qualify the pseudo-component property slate and an atmospheric fractionation workflow. That benchmark should establish mass/energy closure, cut yields, product boiling ranges, numerical robustness, and runtime before adding more refinery-specific correlations.
