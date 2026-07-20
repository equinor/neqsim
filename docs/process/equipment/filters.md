---
title: Filter Equipment
description: Oil and gas filter simulation with type presets, beta-ratio efficiency, hydraulic curves, Ergun pressure drop, dynamic loading, bypass, and mechanical design.
---

# Filter Equipment

Documentation for filter equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Filter Class](#filter-class)
- [Filter Types](#filter-types)
- [Particle and Droplet Capture](#particle-and-droplet-capture)
- [Pressure-Drop Models](#pressure-drop-models)
- [CharCoalFilter Class](#charcoalfilter-class)
- [Dynamic Filter Loading](#dynamic-filter-loading)
- [Differential-Pressure Bypass](#differential-pressure-bypass)
- [Mechanical Design](#mechanical-design)
- [Standards Basis](#standards-basis)
- [Sulfur Filter](#sulfur-filter)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.filter`

**Classes:**
| Class | Description |
|-------|-------------|
| `Filter` | Generic particulate, strainer, coalescer, and media-filter unit operation |
| `CharCoalFilter` | Activated charcoal filter; inherits dynamic loading and regeneration behaviour |
| `SulfurFilter` | Captures solid elemental sulfur (`S8`) and feeds captured mass into dynamic loading |
| `FilterPerformanceCurve` | Particle-size versus beta-ratio performance data |
| `FilterPressureDropCurve` | Actual volumetric flow versus clean differential-pressure data |

Applications include:

- Particulate removal
- Gas and liquid coalescing
- Temporary and permanent strainers
- Bag and cartridge filtration
- Sand, nutshell, and backwashable media beds
- Activated-carbon guard media hydraulics and loading
- Sulfur compound and solid sulfur removal

---

## Filter Class

### Basic Usage

```java
import neqsim.process.equipment.filter.Filter;

// Create filter on gas stream
Filter filter = new Filter("Particulate Filter", gasStream);
filter.setFilterServiceType(FilterType.CARTRIDGE);
filter.run();

// Get outlet stream
StreamInterface cleanGas = filter.getOutletStream();
```

The original fixed differential-pressure behavior remains the default. Select a
filter type or pressure-drop model only when the required input data are
available.

## Filter Types

`FilterType` provides construction-specific starting points. Calling
`setFilterServiceType(...)` selects the associated pressure-drop model, flow
exponent, face velocity, element geometry, and mechanical-design behavior.

| Type | Typical service | Default hydraulic model |
|---|---|---|
| `CARTRIDGE` | Gas or liquid particulate removal | Flow-scaled |
| `BAG` | Produced water and liquid filtration | Flow-scaled |
| `Y_STRAINER` | Temporary or compact coarse protection | Flow-scaled, quadratic |
| `BASKET_STRAINER` | Coarse liquid protection with larger dirt capacity | Flow-scaled, quadratic |
| `COALESCER` | Aerosol or dispersed-droplet capture | Flow-scaled, quadratic |
| `GRANULAR_MEDIA` | Sand, nutshell, or guard media | Ergun |
| `BACKWASHABLE_MEDIA` | Regenerable produced-water media filters | Ergun |
| `ACTIVATED_CARBON` | Sorbent guard-bed hydraulics and loading | Ergun |

The defaults are screening values. Replace face velocities, element areas,
media geometry, ratings, and curves with project/vendor data.

## Particle and Droplet Capture

The size-dependent performance model uses the filtration beta ratio:

$$
\beta_x = \frac{N_{\mathrm{upstream},x}}{N_{\mathrm{downstream},x}}
$$

$$
\eta_x = 1 - \frac{1}{\beta_x}
$$

where $x$ is the minimum particle or droplet size in micrometres. NeqSim uses
log-linear interpolation between supplied beta-ratio points and clamps outside
the tested size range.

```java
FilterPerformanceCurve efficiencyCurve = new FilterPerformanceCurve(
    new double[] {5.0, 10.0, 20.0},
    new double[] {2.0, 100.0, 1000.0});
efficiencyCurve.setTestStandard("ISO 16889:2022");

Filter filter = new Filter("10 micron liquid filter", feed);
filter.setPerformanceCurve(efficiencyCurve);
filter.setParticleSize(10.0);
filter.setInletParticleConcentration(100.0); // mg/kg
filter.run();

double efficiency = filter.getCurrentRemovalEfficiency();
double outletConcentration = filter.getOutletParticleConcentration(); // mg/kg
double capturedRate = filter.getCalculatedCapturedRate(); // kg/hr
```

The generic concentration is an external solids/aerosol inventory and is not a
thermodynamic component. It changes reported contaminant concentration and
dynamic filter loading, but it does not change the outlet stream composition or
enthalpy. Use a contaminant-specific unit such as `SulfurFilter`,
`MercuryRemovalBed`, or `AdsorptionBed` when molecular material removal must be
included in the stream material balance.

## Pressure-Drop Models

`FilterPressureDropModel` supports four approaches:

| Model | Calculation | Required inputs |
|---|---|---|
| `FIXED` | Constant clean differential pressure | `setDeltaP(...)` |
| `FLOW_SCALED` | $\Delta P=\Delta P_{ref}(Q/Q_{ref})^n$ | Clean ΔP, reference actual flow, exponent |
| `TABULATED` | Linear interpolation/extrapolation of test points | `FilterPressureDropCurve` |
| `ERGUN` | Viscous and inertial packed-bed terms | Area, bed depth, media size, void fraction |

Actual volumetric flow is evaluated at inlet conditions. The Ergun model uses
NeqSim density and viscosity, so gas compression, temperature, and liquid
viscosity affect differential pressure.

```java
Filter strainer = new Filter("basket strainer", liquidFeed);
strainer.setFilterServiceType(FilterType.BASKET_STRAINER);
strainer.setDeltaP(0.15); // bar at reference flow
strainer.setReferenceFlowRate(120.0); // actual m3/hr

FilterPressureDropCurve testedCurve = new FilterPressureDropCurve(
    new double[] {50.0, 100.0, 150.0},
    new double[] {0.05, 0.18, 0.40});
testedCurve.setTestStandard("ISO 3968:2017");
strainer.setPressureDropCurve(testedCurve);
```

---

## CharCoalFilter Class

`CharCoalFilter` is the compatibility class for an activated-carbon media
filter. It automatically selects `ACTIVATED_CARBON`, including Ergun hydraulics,
dynamic contaminant loading, and regeneration. It does not currently remove a
named thermodynamic component.

### Basic Usage

```java
import neqsim.process.equipment.filter.CharCoalFilter;

CharCoalFilter charFilter = new CharCoalFilter("carbon guard filter", gasStream);
charFilter.setMediaGeometry(2.0, 3.0, 0.003, 0.40);
charFilter.setNominalRemovalEfficiency(0.99);
charFilter.setInletParticleConcentration(1.0); // external contaminant, mg/kg
charFilter.run();

// Get treated stream
StreamInterface treatedGas = charFilter.getOutletStream();
```

Use `MercuryRemovalBed` for mercury chemisorption, `AdsorptionBed` for
component adsorption, or `H2SScavenger` for reactive H2S removal.

---

## Dynamic Filter Loading

`Filter` can be used as a steady-state pressure-drop element or as a dynamic
filter that accumulates captured solids or contaminant loading during transient
simulation. Dynamic mode is enabled by setting `setCalculateSteadyState(false)`
and calling `runTransient(dt, id)`.

The generic loading model tracks:

- `solidsLoading` in kg
- `solidsLoadingRate` in kg/hr
- finite `loadingCapacity` in kg
- `loadingFraction = solidsLoading / loadingCapacity`
- pressure-drop buildup from clean `deltaP` plus `pressureDropIncreaseAtCapacity`
- breakthrough after `breakthroughStartFraction`
- backwash and regeneration removal rates
- holdup volume and calculated residence time

Loading is added to the selected clean-filter hydraulic model:

$$
\Delta P = \Delta P_\mathrm{clean} + \Delta P_\mathrm{capacity}\,f_\mathrm{loading}
$$

where $f_\mathrm{loading}$ is the fraction of the configured loading capacity
used. Breakthrough remains zero below the configured start fraction and then
ramps linearly to one at full capacity.

When inlet concentration and a beta ratio/efficiency are configured, the
captured rate is calculated automatically. `setSolidsLoadingRate(...)` remains
available as a manual captured-rate input when measured loading is known.

```java
Filter filter = new Filter("dynamic filter", feed);
filter.setDeltaP(0.10);
filter.setHoldupVolume(1.0);
filter.setSolidsLoadingRate(5.0);
filter.setLoadingCapacity(10.0);
filter.setPressureDropIncreaseAtCapacity(1.0);
filter.setBreakthroughStartFraction(0.5);
filter.setCalculateSteadyState(false);

filter.runTransient(3600.0, UUID.randomUUID());

double loading = filter.getSolidsLoading();
double loadingFraction = filter.getLoadingFraction();
double pressureDrop = filter.getDeltaP();
double breakthrough = filter.getBreakthroughFraction();
```

Backwash and regeneration remove accumulated loading during transient steps:

```java
filter.setBackwashRemovalRate(4.0);
filter.setRegenerationRemovalRate(2.0);
filter.startBackwash();
filter.startRegeneration();
filter.runTransient(1800.0, UUID.randomUUID());
```

Use `resetDynamicState()` after replacement, clean-out, or completed
regeneration to clear loading, breakthrough, and active maintenance flags.

## Differential-Pressure Bypass

A filter bypass or relief path can be represented by a cracking differential
pressure. When unrestricted filter differential pressure exceeds the setting,
the applied differential pressure is capped and an approximate bypass fraction
is reported. Bypassed flow receives no filtration, so capture efficiency falls.

```java
filter.setBypassCrackingDeltaP(1.5); // bar
filter.run();

double unrestrictedDeltaP = filter.getUnrestrictedDeltaP();
double appliedDeltaP = filter.getDeltaP();
double bypassFraction = filter.getBypassFraction();
```

The bypass split assumes a quadratic parallel bypass path and is a screening
model. Use a splitter, valve, recycle path, and controller for a detailed
hydraulic network.

## Mechanical Design

Every `Filter` owns a `FilterMechanicalDesign`. `calcDesign()` performs:

- type-specific element area or media-bed cross-section sizing;
- element count and actual face-velocity calculation;
- vessel diameter and tangent-length screening;
- shell and 2:1 ellipsoidal-head thickness screening;
- inlet/outlet nozzle velocity sizing and preliminary nominal diameter;
- terminal differential-pressure and element collapse/burst checks;
- element integrity evidence tracking;
- weight, bill of materials, CAPEX, maintenance, and lifecycle-cost estimates.

```java
filter.setTerminalDeltaP(1.0);          // normal replacement/backwash point, bar
filter.setElementCollapsePressure(5.0); // supplier rating, bar
filter.setElementIntegrityVerified(true);
filter.run();

FilterMechanicalDesign design =
    (FilterMechanicalDesign) filter.getMechanicalDesign();
design.setMaxOperationPressure(70.0);    // bara
design.setMaxOperationTemperature(333.15); // K
design.calcDesign();

int elements = design.getRequiredElements();
double vesselId = design.getInnerDiameter();
double shellThickness = design.getShellThickness();
double nozzleSize = design.getSelectedNozzleDiameterMm();
List<String> warnings = design.getDesignWarnings();
```

The pressure-boundary calculation is preliminary. Certified design still needs
the governing code edition, project pressure/temperature rules, certified
allowable stresses, weld efficiency, corrosion/erosion allowance, external
loads, nozzle reinforcement, closure design, supports, fatigue, fire case,
relief protection, materials compatibility, inspection, and vendor review.

## Standards Basis

NeqSim implements open, user-configurable representations of standard test
results; it does not reproduce protected acceptance tables or claim equipment
certification.

| Reference | Implemented use |
|---|---|
| [ISO 16889:2022](https://www.iso.org/standard/77245.html) | Store and interpolate beta-ratio, contaminant-capacity, and differential-pressure performance supplied by a test laboratory/vendor |
| [ISO 3968:2017](https://www.iso.org/standard/64104.html) | Store and interpolate measured clean differential-pressure versus actual-flow data |
| [ISO 2942:2018](https://www.iso.org/standard/68005.html) | Record that element fabrication integrity was verified; bubble point is not converted into efficiency |
| [ISO 2941:2009](https://www.iso.org/standard/41062.html) | Check operating differential pressure against a user-supplied element collapse/burst rating |
| [ASME Section VIII Division 1](https://www.asme.org/codes-standards/find-codes-standards/bpvc-viii-1-bpvc-section-viii-rules-construction-pressure-vessels-division-1) | Screening shell/head membrane-thickness equations with configurable material allowable stress, joint efficiency, and corrosion allowance |

The ISO references above are primarily hydraulic-fluid filter test methods.
Their data structures are also useful for oil and gas liquid filters, while gas
coalescers and special media should use the applicable vendor test method and
record it in the curve metadata.

ISO and ASME publish the scope and status pages openly, while the normative
documents remain copyrighted. Consequently, NeqSim stores user-supplied test
results and configurable design inputs rather than embedding protected tables.

---

## Sulfur Filter

`SulfurFilter` extends `Filter` for gas streams containing solid elemental
sulfur as `S8`. During `run()` it performs a TP-solid flash when `S8` is present,
detects the solid sulfur phase, removes the captured fraction from the outlet,
and reports the captured sulfur rate in kg/hr.

During `runTransient(...)`, captured `S8` is added to the inherited dynamic
filter loading state. That means a sulfur filter can represent operational
pressure buildup from sulfur deposition and eventual breakthrough as the element
capacity is consumed.

```java
SulfurFilter filter = new SulfurFilter("sulfur filter", sulfurBearingGas);
filter.setRemovalEfficiency(1.0);
filter.setDeltaP(0.10);
filter.setFilterElementCapacity(1000.0);
filter.setNumberOfElements(1);
filter.setPressureDropIncreaseAtCapacity(1.0);
filter.setBreakthroughStartFraction(0.9);
filter.setCalculateSteadyState(false);

filter.runTransient(3600.0, UUID.randomUUID());

boolean detected = filter.isSolidS8Detected();
double capturedRate = filter.getSolidSulfurRemovalRate();
double sulfurLoading = filter.getSolidsLoading();
double builtPressureDrop = filter.getDeltaP();
```

The finite dynamic capacity is synchronized from
`filterElementCapacity * numberOfElements`, so sulfur-filter sizing and dynamic
loading use the same capacity basis.

---

## Usage Examples

### Inlet Gas Conditioning

```java
ProcessSystem process = new ProcessSystem();

// Raw gas feed
Stream rawGas = new Stream("Raw Gas", gasFluid);
rawGas.setFlowRate(100000.0, "Sm3/day");
process.add(rawGas);

// Particulate filter
Filter particleFilter = new Filter("Inlet Filter", rawGas);
process.add(particleFilter);

// Molecular mercury removal uses a chemisorption bed
MercuryRemovalBed hgFilter = new MercuryRemovalBed("Hg Guard Bed",
    particleFilter.getOutletStream());
process.add(hgFilter);

// Run
process.run();
```

### Sulfur Formation and Filtration

Pair `SulfurOxidationReactor` with `SulfurFilter` when sour methane contains
oxygen and elemental sulfur formation should be represented explicitly. The
reactor creates `S8`; the filter captures solid `S8` and builds pressure drop
dynamically.

```java
SystemInterface gas = new SystemSrkEos(283.15, 20.0);
gas.addComponent("methane", 80.0);
gas.addComponent("H2S", 80.0);
gas.addComponent("oxygen", 40.0);
gas.addComponent("water", 1.0e-12);
gas.addComponent("S8", 1.0e-12);
gas.setMixingRule("classic");
gas.setMultiPhaseCheck(true);
gas.setSolidPhaseCheck("S8");

Stream feed = new Stream("sour methane feed", gas);
feed.setFlowRate(1000.0, "kg/hr");
feed.run();

SulfurOxidationReactor reactor = new SulfurOxidationReactor("sulfur reactor", feed);
reactor.setH2SConversionTarget(1.0);
reactor.run();

SulfurFilter filter = new SulfurFilter("sulfur filter", reactor.getOutletStream());
filter.setRemovalEfficiency(1.0);
filter.setDeltaP(0.10);
filter.setFilterElementCapacity(1000.0);
filter.setNumberOfElements(1);
filter.setPressureDropIncreaseAtCapacity(1.0);
filter.setCalculateSteadyState(false);
filter.runTransient(3600.0, UUID.randomUUID());
```

This example is covered by `SulfurOxidationReactorTest` and `FilterTest`.

### LNG Mercury Removal

```java
// Upstream of cryogenic section
MercuryRemovalBed mercuryRemoval = new MercuryRemovalBed("Mercury Removal", feed);
mercuryRemoval.run();

double outletMercury = mercuryRemoval.getOutletStream()
    .getFluid().getComponent("mercury").getFlowRate("g/hr");
```

---

## Related Documentation

- [Separators](separators) - Phase separation
- [Absorbers](absorbers) - Absorption processes
- [Reactors](reactors) - Sulfur oxidation and other reactor models
- [Streams](streams) - Stream handling
