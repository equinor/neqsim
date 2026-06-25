---
title: Filter Equipment
description: Documentation for filter equipment in NeqSim process simulation, including dynamic loading, breakthrough, backwash, regeneration, and sulfur S8 capture.
---

# Filter Equipment

Documentation for filter equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Filter Class](#filter-class)
- [CharCoalFilter Class](#charcoalfilter-class)
- [Dynamic Filter Loading](#dynamic-filter-loading)
- [Sulfur Filter](#sulfur-filter)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.filter`

**Classes:**
| Class | Description |
|-------|-------------|
| `Filter` | Generic filter unit with optional dynamic loading and pressure-drop buildup |
| `CharCoalFilter` | Activated charcoal filter; inherits dynamic loading and regeneration behaviour |
| `SulfurFilter` | Captures solid elemental sulfur (`S8`) and feeds captured mass into dynamic loading |

Filters are used to remove specific components or contaminants from process streams. Applications include:
- Particulate removal
- Activated carbon adsorption
- Mercury removal
- Sulfur compound and solid sulfur removal

---

## Filter Class

### Basic Usage

```java
import neqsim.process.equipment.filter.Filter;

// Create filter on gas stream
Filter filter = new Filter("Particulate Filter", gasStream);
filter.run();

// Get outlet stream
StreamInterface cleanGas = filter.getOutletStream();
```

---

## CharCoalFilter Class

Activated charcoal filter for removing specific components.

### Basic Usage

```java
import neqsim.process.equipment.filter.CharCoalFilter;

// Create charcoal filter
CharCoalFilter charFilter = new CharCoalFilter("Mercury Filter", gasStream);
charFilter.setRemovalEfficiency("mercury", 0.99);  // 99% removal
charFilter.run();

// Get treated stream
StreamInterface treatedGas = charFilter.getOutletStream();
```

### Removal Efficiency

```java
// Set removal efficiency for specific components
charFilter.setRemovalEfficiency("mercury", 0.99);
charFilter.setRemovalEfficiency("H2S", 0.95);
charFilter.setRemovalEfficiency("benzene", 0.90);
```

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

The pressure-drop relation is intentionally simple and transparent:

$$
\Delta P = \Delta P_\mathrm{clean} + \Delta P_\mathrm{capacity}\,f_\mathrm{loading}
$$

where $f_\mathrm{loading}$ is the fraction of the configured loading capacity
used. Breakthrough remains zero below the configured start fraction and then
ramps linearly to one at full capacity.

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

// Mercury removal
CharCoalFilter hgFilter = new CharCoalFilter("Hg Guard Bed",
    particleFilter.getOutletStream());
hgFilter.setRemovalEfficiency("mercury", 0.999);
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
CharCoalFilter mercuryRemoval = new CharCoalFilter("Mercury Removal", feed);
mercuryRemoval.setRemovalEfficiency("mercury", 0.9999);  // Critical for aluminum equipment
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
