---
title: Automatic Release and Gas Dispersion Scenarios
description: Generate leak source terms, gas dispersion screening endpoints, and CFD source-term handoff cases directly from NeqSim ProcessSystem stream results. Covers ReleaseDispersionScenarioGenerator, CfdSourceTermCase, TrappedInventoryCalculator, weather envelopes, consequence branches, flammable LFL distances, and toxic endpoints.
---

NeqSim can now turn a running `ProcessSystem` into first-pass release and gas dispersion
screening cases, and then package those cases as formal CFD source-term handoffs. The
workflow is intended for early safety studies, HAZOP follow-up, dynamic simulation
reviews, and plant-data-backed screening where process stream pressure, temperature,
flow, composition, and isolated inventory already exist in NeqSim.

The workflow is deliberately a screening bridge, not a replacement for CFD or regulatory
QRA tools. Use it to identify credible leak cases, rank streams by consequence potential,
generate source terms, create auditable scenario matrices, and prepare handoffs to
detailed tools such as PHAST, FLACS, KFX, Safeti, or OpenFOAM.

## Workflow

`ReleaseDispersionScenarioGenerator` scans a process flowsheet and performs four steps:

1. Discover standalone streams and equipment outlet streams using the common stream
   introspection API.
2. Skip streams with no flow or low pressure.
3. Build a `LeakModel` source term for each remaining stream using the configured hole
   diameter, inventory volume, release duration, and boundary pressure.
4. Run `GasDispersionAnalyzer` to calculate flammable LFL distances, dense-gas or
   Gaussian model selection, optional toxic endpoint distances, and compact JSON output.
5. Optionally expand each stream into release taxonomy cases, batch weather cases,
   ignition/consequence branches, trapped-inventory provenance, and CFD source-term cases.

## Java Example

The example below is mirrored by `ReleaseDispersionScenarioGeneratorTest` so the documented
API calls are verified by the test suite.

```java
import java.util.List;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.BoundaryConditions;
import neqsim.process.safety.scenario.ReleaseDispersionScenarioGenerator;
import neqsim.process.safety.scenario.ReleaseDispersionScenarioGenerator.ReleaseDispersionScenario;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface gas = new SystemSrkEos(298.15, 55.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.10);
gas.setMixingRule("classic");

Stream feed = new Stream("feed gas", gas);
feed.setFlowRate(500.0, "kg/hr");
feed.setTemperature(25.0, "C");
feed.setPressure(55.0, "bara");

ThrottlingValve valve = new ThrottlingValve("inlet valve", feed);
valve.setOutletPressure(40.0, "bara");

Separator separator = new Separator("hp separator", valve.getOutletStream());

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(valve);
process.add(separator);
process.run();

BoundaryConditions weather = BoundaryConditions.northSeaWinter();

ReleaseDispersionScenarioGenerator generator = new ReleaseDispersionScenarioGenerator(process)
    .boundaryConditions(weather)
    .holeDiameter(10.0, "mm")
    .inventoryVolume(5.0)
    .releaseDuration(60.0, 5.0);

List<ReleaseDispersionScenario> scenarios = generator.generateScenarios();
ReleaseDispersionScenario firstScenario = scenarios.get(0);

double peakReleaseRate = firstScenario.getSourceTerm().getPeakMassFlowRate();
double distanceToLfl = firstScenario.getDispersionResult().getDistanceToLflM();
String scenarioJson = firstScenario.toJson();
```

## Configuration

| Method | Purpose |
|--------|---------|
| `boundaryConditions(BoundaryConditions)` | Sets wind, stability, ambient pressure, ambient temperature, and terrain basis. |
| `holeDiameter(double, String)` | Sets the representative leak hole size in `m`, `mm`, or `in`. |
| `fullBoreDiameter(double, String)` | Sets the full-bore rupture diameter used by taxonomy cases. |
| `inventoryVolume(double)` | Sets the isolated inventory volume used by the source-term blowdown model. |
| `trappedInventory(InventoryResult)` | Uses a `TrappedInventoryCalculator` result as the volume and inventory provenance basis. |
| `releaseDuration(double, double)` | Sets source-term duration and time step in seconds. |
| `minimumPressure(double)` | Skips streams below the specified absolute pressure in bara. |
| `minimumMassFlowRate(double)` | Skips streams below the specified mass flow rate in kg/s. |
| `releaseHeight(double)` | Sets the effective release height used in dispersion calculations. |
| `toxicEndpoint(String, double)` | Adds a toxic concentration endpoint in ppm for a component in the fluid. |
| `useDefaultScenarioTaxonomy()` | Generates 5 mm, 10 mm, 25 mm, 50 mm, full-bore, flange, instrument, and dropped-object cases. |
| `useDefaultWeatherEnvelope()` | Generates winter/summer, low/high wind, D/F stability, and wind-sector cases. |
| `consequenceBranches(List<ConsequenceBranch>)` | Adds immediate ignition, delayed ignition, jet fire, VCE, toxic-only, and no-ignition branch metadata. |
| `generateCfdSourceTermCases()` | Converts generated scenarios to validated CFD source-term schema objects. |

## Industrial Scenario Matrix

The generator can expand each eligible stream into an industrial screening matrix. This is
useful when the process model is already connected to plant data or dynamic simulations and
the safety engineer wants consistent leak cases across the plant.

```java
import java.util.List;
import neqsim.process.safety.cfd.CfdSourceTermCase;
import neqsim.process.safety.inventory.TrappedInventoryCalculator;
import neqsim.process.safety.inventory.TrappedInventoryCalculator.InventoryResult;
import neqsim.process.safety.scenario.ReleaseDispersionScenarioGenerator;

InventoryResult inventory = new TrappedInventoryCalculator()
   .setFluid(gas)
   .setOperatingConditions(55.0, "bara", 25.0, "C")
   .addEquipmentVolume("V-101", 4.0, 0.0, null)
   .calculate();

List<CfdSourceTermCase> cfdCases = new ReleaseDispersionScenarioGenerator(process)
   .trappedInventory(inventory)
   .fullBoreDiameter(150.0, "mm")
   .useDefaultScenarioTaxonomy()
   .useDefaultWeatherEnvelope()
   .releaseDuration(300.0, 5.0)
   .toxicEndpoint("H2S", 100.0)
   .generateCfdSourceTermCases();
```

The default taxonomy includes:

| Case | Purpose |
|------|---------|
| 5 mm, 10 mm, 25 mm, 50 mm leaks | Standard process leak size screening. |
| Full-bore rupture | Uses the configured full-bore diameter. |
| Flange leak | Representative gasket or bolted-joint failure. |
| Instrument leak | Small-bore instrument connection failure. |
| Dropped-object damage | Large leak caused by impact. |

Release frequency values in these cases are generic placeholders for screening and must be
replaced by site-specific leak-frequency data before QRA risk claims are made.

## Trapped Inventory Basis

Use `TrappedInventoryCalculator` when volume should come from evidence-linked equipment and
pipe segments rather than a single representative number. The resulting CFD case records the
inventory basis, gas-filled volume, liquid-filled volume, density, mass, per-segment records,
and warnings such as missing document evidence.

```java
InventoryResult inventory = new TrappedInventoryCalculator()
   .setFluid(gas)
   .setOperatingConditions(55.0, "bara", 25.0, "C")
   .addEquipmentVolume("V-101", 4.0, 0.0, null)
   .addPipeSegment("export-line-isolated-section", 0.203, 120.0, 0.0, null)
   .calculate();
```

If `inventoryVolume(double)` is used instead, generated CFD cases include a warning that the
volume is a screening assumption.

## CFD Source-Term Handoff

`CfdSourceTermCase` is a neutral, versioned JSON schema for passing NeqSim source terms to
detailed CFD studies. It is deliberately not OpenFOAM-specific. Each case contains:

- `context`: process, equipment, stream, release case, weather case, pressure, temperature,
   and flow-rate metadata.
- `fluid`: component mole fractions, density, fuel fraction, and LFL metadata.
- `release`: hole diameter, area, orientation, height, default local coordinates, and generic
   release frequency placeholder.
- `sourceTerm`: time-series mass flow, temperature, pressure, vapor fraction, jet velocity,
   jet momentum, and droplet SMD.
- `ambient`: weather, stability class, surface roughness, pressure, humidity, and offshore flag.
- `inventory`: trapped inventory result or representative-volume warning.
- `consequenceBranches`: immediate ignition, delayed ignition, jet fire, VCE, toxic-only,
   and no-ignition metadata.
- `provenance`: standards basis, source model, confidence level, known limitations, and
   `notForFinalLayoutWithoutValidation = true`.

Use `validate()` before handing a case to another tool:

```java
CfdSourceTermCase cfdCase = cfdCases.get(0);
CfdSourceTermCase.ValidationResult validation = cfdCase.validate();
if (!validation.isValid()) {
   throw new IllegalStateException(validation.toJson());
}
```

`CfdSourceTermExporter` writes neutral JSON, a multi-case manifest, and an OpenFOAM-oriented
source-term skeleton:

```java
import neqsim.process.safety.cfd.CfdSourceTermExporter;

CfdSourceTermExporter exporter = new CfdSourceTermExporter();
exporter.exportJson(cfdCase, "case.json");
exporter.exportManifest(cfdCases, "manifest.json");
exporter.exportOpenFoamSkeleton(cfdCase, "openfoam-source-term");
```

The OpenFOAM skeleton contains `case.json`, `constant/releaseSourceProperties`, and tabulated
`constant/sourceTimeSeries` files for mass flow, temperature, and jet momentum. It still requires
study-specific mesh, geometry, turbulence model, species transport setup, boundary conditions,
and ignition logic.

## Result Data

Each generated `ReleaseDispersionScenario` contains:

- Process context: equipment name, equipment type, stream name, stream pressure,
  stream temperature, and stream mass flow rate.
- Source term: peak mass release rate, total released mass, time series mass flow,
  pressure, temperature, vapor fraction, jet velocity, and jet momentum.
- Dispersion result: selected model, flammable mass rate, source density, air density,
  fuel fraction, lower flammable limit, distance to LFL, distance to half LFL,
  flammable cloud volume, optional toxic endpoint distance, and weather basis.

The `toJson()` method provides a compact representation suitable for notebooks, MCP tools,
screening reports, and external consequence-analysis handoffs.

For CFD handoff, prefer `ReleaseDispersionScenario.toCfdSourceTermCase()` or
`ReleaseDispersionScenarioGenerator.generateCfdSourceTermCases()` because those APIs preserve
schema version, provenance, assumptions, and validation metadata.

## Sour Gas Endpoint

For sour gas streams, configure a toxic endpoint in addition to flammable endpoints:

```java
List<ReleaseDispersionScenario> sourScenarios = new ReleaseDispersionScenarioGenerator(process)
    .boundaryConditions(BoundaryConditions.northSeaWinter())
    .holeDiameter(8.0, "mm")
    .inventoryVolume(2.0)
    .releaseDuration(60.0, 5.0)
    .toxicEndpoint("H2S", 100.0)
    .generateScenarios();
```

The toxic endpoint uses the same released gas composition as the flammable screening case.
If the named component is not present, the toxic distance is reported as not reached.

## Engineering Use

This workflow is useful for:

- Ranking process streams before detailed QRA.
- Screening HAZOP loss-of-containment cases from a live flowsheet.
- Comparing weather cases such as North Sea winter and summer conditions.
- Connecting dynamic release or plant-data-derived stream states to dispersion endpoints.
- Producing source-term JSON and OpenFOAM skeleton files for detailed CFD setup.

The focused validation suite includes reference-envelope cases for methane jet dispersion,
CO2 dense-gas toxic endpoints, propane heavy-gas flammable endpoints, and H2S toxic plumes.
These tests verify the screening model behavior stays within broad benchmark envelopes; they
do not replace project-specific validation against PHAST, FLACS, KFX, Safeti, OpenFOAM, or
wind-tunnel/literature data for final design.

For final layout, escalation, or regulatory studies, validate screening results with a
detailed consequence-analysis method and site-specific assumptions.

## Related Documentation

- [Automatic Scenario Generation](scenario-generation.md)
- [Dispersion and Consequence Analysis](../../safety/dispersion_and_consequence.md)
- [Process Safety Package](README.md)
