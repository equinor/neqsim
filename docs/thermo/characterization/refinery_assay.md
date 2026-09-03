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

When molecular weight is not supplied, the inverse petroleum correlation first produces a g/mol-sized value and `OilAssayCharacterisation` converts it to kg/mol before creating the pseudo-component. This unit conversion is part of the assay API contract; generated refinery cuts must not be passed to `addTBPfraction(...)` with g/mol interpreted as kg/mol.

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

and stores the result as dimensionless specific gravity. Negative API gravities are accepted when physically meaningful; the singular range at and below -131.5 degrees API is rejected. This exact dimensionless handling corrects the earlier assay path, which additionally multiplied API-derived specific gravity by the water density and therefore made API- and specific-gravity-origin cuts differ by about 0.0985%.

### Reconstructed whole-assay density

For a complete cut table, `getBulkSpecificGravity()` reconstructs the ideal additive-volume whole-assay specific gravity without creating pseudo-components or mutating the thermodynamic system. `getBulkApiGravity()` reports the corresponding degrees API. `getBulkDensityKgPerCubicMetreAt60F()` provides physical density at 60 degF by multiplying the dimensionless result by 999.016 kg/m3. Both mass- and liquid-volume-basis assays use the same resolved relation:

$$SG_{bulk}=\left(\sum_i\frac{w_i}{SG_i}\right)^{-1}$$

where `w_i` is the resolved mass fraction and `SG_i` is the cut specific gravity. For volume-basis inputs this reduces to the volume-fraction-weighted cut density after normalization.

These methods are screening calculations at the density reference condition represented by the inputs. They assume ideal additive liquid volumes and do not apply temperature correction, excess-volume, or blend-contraction models. They are not custody-transfer or certified blend-design calculations. The public validation and numerical error boundary are documented in [DOE/OEDI COA bulk density and API qualification](refinery_oedi_coa_bulk_density_validation).

## Per-cut UOP/Watson characterization factor

`AssayCut.getWatsonCharacterizationFactor()` calculates the dimensionless UOP/Watson factor from the same authoritative density and representative-boiling-point inputs used by the assay workflow:

$$K_W=\frac{(1.8T_b)^{1/3}}{SG}$$

Here $T_b$ is in K and $SG$ is dimensionless specific gravity. This is equivalent to using boiling point in degrees Rankine. A finite boiling interval uses its arithmetic midpoint. Kelvin, Celsius and Fahrenheit inputs therefore share one calculation, and exact-equivalent SG/API inputs give the same result. Missing density or boiling-point information fails closed.

The public [DOE Big Hill Watson-factor qualification](refinery_big_hill_watson_validation) covers four bounded 375-1050 degF cuts with SG 0.8297-0.9336. The maximum absolute difference from DOE's one-decimal UOP K values is 0.0122. The method is qualified for assay screening inside that matrix; it is not a whole-crude aggregation, ASTM conversion or design-certification method.

### Watson-factor input for terminal cuts

When a source reports a Watson factor but no representative boiling point, use
`withWatsonCharacterizationFactor(...)` together with specific gravity or API gravity. NeqSim
inverts the same authoritative relation:

$T_b=\frac{(K_W SG)^3}{1.8}$

The result is a representative normal boiling point in K; it does not create a finite terminal
boundary. An explicit representative boiling point and an explicit Watson factor are mutually
exclusive. A derived value outside a stored one-sided boundary fails before any thermodynamic
component is added.

The [DOE Big Hill terminal-Watson qualification](refinery_big_hill_watson_terminal_validation)
uses the reported 1050 degF+ residue values SG60/60 = 1.0089 and UOP K = 11.7. They imply
913.7543263804 K (1185.0877874847 degF), above the published lower boundary. This supplies a
source-derived representative temperature for the existing pseudo-component path; it does not
independently validate the molecular weight or downstream properties produced by that path.

## Composition-resolved standard light ends

When an assay source reports known light molecules, mark each mass-basis cut with
`withStandardComponent(componentName)`. NeqSim then uses the attached Java thermodynamic system's
standard-component database and authoritative molar mass instead of creating a petroleum `_PC`
component. Preparation is performed on a clone so unknown or inconsistent inputs fail before the
original system is mutated.

The [DOE Big Hill composition-resolved light-end qualification](refinery_big_hill_light_ends_validation)
normalizes DOE's reported ethane, propane, i-butane, and n-butane debutanization weights over the
reported C2-C4 subset and applies the independently reported 1.70 mass% whole-crude gas yield. It
qualifies standard-component identity plus exact mass/mole bookkeeping. It does not qualify VLE,
flash recovery, or C5-175 degF properties.

## PIANO-derived assay-cut molar mass

When a public assay reports hydrocarbon family and carbon number on a mass basis, use
`calculatePianoMolarMassKgPerMol(...)` to obtain the number-average molar mass for an explicit
pseudo-component. The helper supports paraffin, iso-paraffin, aromatic, and naphthene groups using
their ideal homologous-series formulas and conventional C/H atomic weights. It normalizes small
source-table rounding errors but fails closed for invalid formulas, weights, or closure.

The [DOE Big Hill C5-175 degF PIANO qualification](refinery_big_hill_piano_molar_mass_validation)
freezes the public family/carbon-number table and derives 0.0791538366563 kg/mol. Together with the
reported 5.22 mass% yield, SG60/60 = 0.6731, and one-sided 175 degF upper boundary, this supplies a
reproducible explicit-molar-mass cut without inventing a lower or representative boiling point. It
does not resolve isomers or validate critical properties, VLE, or fractionation yields.

## Complete public reference slate

`DoeBigHillSweetAssay.create(system, totalMassKg)` composes the qualified DOE Big Hill inputs into a
complete source-specific mass-basis assay. It configures four standard C2-C4 components plus eight
petroleum cuts without automatically mutating the thermodynamic component list. The caller inspects
the returned `OilAssayCharacterisation` and invokes `apply()` explicitly.

The [complete modeled-slate contract and provenance](refinery_big_hill_complete_slate) document the
2021 workbook values, the normalized C2-C4 allocation assumption, the blank sulfur/nitrogen screening
assumptions, exact mass closure, and the boundary between reproducibility and property/process
validation.

## Constrained Sarir pseudo-component input

`SarirAtmosphericAssay.create(...)` converts the public Sarir cumulative TBP evidence into 18
volume-basis cuts while preserving the `70 degC-` and `550 degC+` terminal intervals as one-sided.
The source does not publish cut density or molar-mass profiles, so both are required caller inputs.
The factory accepts a profile only when it reconciles the reported 841.5 kg/m3 whole-crude density
within 1.0 kg/m3 and the reported 0.2447 kg/mol average molar mass within 0.001 kg/mol.

This capability makes the missing-property boundary executable instead of silently filling it.
It does not resolve Sarir light-end composition, distribute whole-crude sulfur, or qualify
fractionation yields. The [Sarir reference and input contract](refinery_sarir_atmospheric_reference)
provide provenance, Java/JPype usage, and the process-validation stop boundary.

## Open-ended terminal boiling boundaries

Terminal assay cuts often publish only one boiling limit. Use
`withLowerBoilingPointKelvin/Celsius/Fahrenheit(...)` for a heavy-end “plus” cut and
`withUpperBoilingPointKelvin/Celsius/Fahrenheit(...)` for a light-end “minus” cut.
`hasLowerBoilingPoint()` and `hasUpperBoilingPoint()` distinguish one-sided metadata from a
complete finite interval.

A one-sided limit is provenance, not a representative boiling point. NeqSim never turns it into a
finite midpoint. Pseudo-component generation therefore still requires either an explicit molar mass
or an independently supported representative boiling point together with density. Contradictory
bounds or representative values fail before the cut is changed.

The public [DOE Big Hill terminal-cut qualification](refinery_big_hill_terminal_boundary_validation)
freezes the workbook's 11.56 mass% 1050 degF+ residue boundary. It verifies exact unit conversion,
one-sided retention, clone behavior, fail-closed incomplete characterization and exact mass closure
when an explicit engineering molar mass is supplied. DOE does not publish that molar mass; the test
does not present its control value as source data.

## Assay-carried total sulfur

`AssayCut.withSulfurMassFraction(...)` and `withSulfurMassPercent(...)` carry total sulfur explicitly on a mass basis. `getBulkSulfurMassFraction()` and `getBulkSulfurMassPercent()` apply the linear mass-basis rule to the same resolved assay mass fractions used for pseudo-component bookkeeping. Positive-yield cuts without sulfur data fail closed; zero-yield cuts do not contribute.

The public [DOE Big Hill assay sulfur qualification](refinery_big_hill_sulfur_validation) covers a complete non-overlapping crude slate. With the documented zero-sulfur screening assumption for the 1.70 mass% gas cut that lacks a reported value, the reconstructed 0.40867518 mass% agrees with DOE's 0.409 mass% whole-crude result within 0.00032482 mass%. This is linear assay bookkeeping, not sulfur-species thermodynamics, emissions prediction or hydrotreating chemistry.

## Assay-carried total nitrogen

`AssayCut.withNitrogenMassFraction(...)` and `withNitrogenMassPercent(...)` carry total nitrogen explicitly on a mass basis. `getBulkNitrogenMassFraction()` and `getBulkNitrogenMassPercent()` apply the linear mass-basis rule to the same resolved assay mass fractions used for pseudo-component bookkeeping. Positive-yield cuts without nitrogen data fail closed; zero-yield cuts do not contribute.

The public [DOE Big Hill assay nitrogen qualification](refinery_big_hill_nitrogen_validation) covers the same complete non-overlapping crude slate. DOE omits nitrogen for the four lightest cuts, so the documented screening case assigns zero to those cuts. The reconstructed 0.1095129 mass% agrees with DOE's 0.11 mass% whole-crude result within 0.0004871 mass%. This is linear assay bookkeeping, not nitrogen-species thermodynamics, emissions prediction or hydrotreating chemistry.

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
- kg/mol scaling of molar mass inferred from boiling point and density;
- kg/m3 density normalization;
- API-gravity handling, including negative API gravity;
- rounding-scale closure versus incomplete-assay rejection;
- rejection of mixed mass/volume bases and duplicate cuts;
- TBP boundary monotonicity, complete 0-100% coverage, finite and one-sided stored boiling limits, and generated pseudo-components;
- repeated-application protection;
- exact reconstructed assay-mass closure;
- analytical mass- and volume-basis bulk specific-gravity reconstruction;
- whole-assay SG/API agreement across all five complete four-category rows in the public DOE/OEDI COA summary workbook;
- per-cut UOP/Watson factors against four bounded cuts in the public DOE SPR Big Hill Sweet 2021 assay;
- Watson-derived representative boiling point for the DOE Big Hill 1050 degF+ terminal residue;
- composition-resolved standard-component ingestion for the DOE Big Hill C2-C4 light ends;
- whole-assay total-sulfur reconstruction against the public DOE Big Hill Sweet 2021 assay;
- whole-assay total-nitrogen reconstruction against the public DOE Big Hill Sweet 2021 assay;
- deterministic complete-slate construction with four standard light molecules, eight petroleum
  pseudo-components, exact assay-mass closure, and preserved terminal evidence boundaries;
- input-order independence and no thermodynamic-system mutation for bulk-property queries.

These tests establish software/bookkeeping correctness, qualify ideal-additive-volume SG/API screening over the frozen COA matrix (published crude SG 0.765-0.847; maximum observed errors 0.006 SG and 1.5 degrees API), qualify per-cut Watson factors over the frozen DOE matrix (375-1050 degF; maximum observed error 0.0122), qualify the DOE residue's Watson-derived representative temperature, qualify the DOE C2-C4 standard-component split, qualify the DOE C5-175 degF PIANO aggregate molar mass, and qualify linear assay sulfur and nitrogen bookkeeping over one complete DOE crude slate. They do **not** qualify temperature correction, contraction, generic molecular-weight or critical-property correlations, sulfur/nitrogen species or reactions, or atmospheric/vacuum fractionation; those remain separate campaign gates in issue #3305.

## Refinery capability inventory after this increment

| Capability | Current NeqSim foundation | Campaign status |
| --- | --- | --- |
| Pre-binned crude/petroleum assay cuts | `OilAssayCharacterisation` | Foundation hardened in #3305 |
| Mass- and volume-basis cut yields | Unit/basis-explicit assay API | Foundation hardened in #3305 |
| Pre-binned cumulative TBP cut boundaries | `addTBPCutBoundariesCelsius/Kelvin` | Initial implementation in #3305 |
| Open-ended terminal boiling limits | Unit-explicit one-sided `AssayCut` boundaries | DOE-qualified for metadata and fail-closed preparation |
| Watson-derived terminal representative point | `withWatsonCharacterizationFactor(...)` | DOE-qualified for the Big Hill 1050 degF+ residue |
| Composition-resolved standard light ends | `AssayCut.withStandardComponent(...)` | DOE-qualified for the Big Hill C2-C4 composition and mass/mole bookkeeping |
| PIANO family/carbon-number molar mass | `calculatePianoMolarMassKgPerMol(...)` | DOE-qualified for the Big Hill C5-175 degF aggregate and explicit-molar-mass bookkeeping |
| Per-cut UOP/Watson characterization factor | `AssayCut.getWatsonCharacterizationFactor()` | DOE-qualified for assay screening over 375-1050 degF |
| Assay-carried total sulfur | `getBulkSulfurMassFraction/Percent()` | DOE-qualified for linear bookkeeping over one complete crude slate |
| Assay-carried total nitrogen | `getBulkNitrogenMassFraction/Percent()` | DOE-qualified for linear bookkeeping over one complete crude slate |
| Complete public reference slate | `DoeBigHillSweetAssay` | Reproducible modeled 2021 DOE composition; property and process validation remain separate gates |
| TBP pseudo-component properties | Pedersen, Lee-Kesler, Riazi-Daubert, Twu, Cavett, Standing and related models | Existing; needs refinery-range independent validation |
| Plus-fraction splitting/lumping | `Characterise`, plus-fraction and lumping models | Existing; refinery assay integration still to be qualified |
| Oil density/API and volatility standards | Oil-quality standards package, RVP/TVP workflows | Whole-assay SG/API, per-cut Watson and linear sulfur/nitrogen bookkeeping qualified over frozen public matrices; broader stream properties remain open |
| Rigorous distillation columns | `DistillationColumn`, `SimpleTray`, Naphtali-Sandholm solver, side-draw support | Existing foundation; broad-boiling atmospheric/vacuum refinery benchmark remains open |
| Crude preheat/fired heater | General heater/heat-exchanger process equipment | Refinery workflow and fuel/emission integration remain open |
| Product blending/specification optimization | Generic optimization/process facilities | Refinery property/blending framework remains open |
| ASTM D86/D1160 to TBP conversion | No qualified refinery conversion API in this increment | Open; requires public correlation provenance and validation |
| Atmospheric crude-unit benchmark | No campaign benchmark yet | Next high-value validation milestone |
| Vacuum tower benchmark | No campaign benchmark yet | Open after atmospheric case |
| Hydrotreating/reforming/FCC/hydrocracking | No campaign-qualified refinery conversion models | Deliberately later phase |

## Next campaign step

The next dependency-ready process increment should advance the bounded DOE atmospheric integration
case to this complete modeled slate and compare product yields and boiling ranges against independent
public evidence before vacuum fractionation.
