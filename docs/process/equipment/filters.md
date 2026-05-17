---
title: Filter Equipment
description: Documentation for filter equipment in NeqSim process simulation.
---

# Filter Equipment

Documentation for filter equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Filter Classes](#filter-classes)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.filter`

**Classes:**
| Class | Description |
|-------|-------------|
| `Filter` | Generic filter unit |
| `CharCoalFilter` | Activated charcoal filter |

Filters are used to remove specific components or contaminants from process streams. Applications include:
- Particulate removal
- Activated carbon adsorption
- Mercury removal
- Sulfur compound removal

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

### LNG Mercury Removal

```java
// Upstream of cryogenic section
CharCoalFilter mercuryRemoval = new CharCoalFilter("Mercury Removal", feed);
mercuryRemoval.setRemovalEfficiency("mercury", 0.9999);  // Critical for aluminum equipment
mercuryRemoval.run();

double outletMercury = mercuryRemoval.getOutletStream()
    .getFluid().getComponent("mercury").getFlowRate("g/hr");
System.out.println("Outlet mercury: " + outletMercury + " g/hr");
```

---

## Related Documentation

- [Separators](separators) - Phase separation
- [Absorbers](absorbers) - Absorption processes
- [Streams](streams) - Stream handling
