---
title: Reactors
description: Documentation for chemical reactor equipment in NeqSim.
---

# Reactors

Documentation for chemical reactor equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Reactor Types](#reactor-types)
- [CSTR](#cstr)
- [PFR](#pfr)
- [Equilibrium Reactor](#equilibrium-reactor)
- [Gibbs Reactor](#gibbs-reactor)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.equipment.reactor`

**Classes:**
| Class | Description |
|-------|-------------|
| `Reactor` | Base reactor class |
| `CSTRReactor` | Continuous stirred tank reactor |
| `PFRReactor` | Plug flow reactor |
| `EquilibriumReactor` | Chemical equilibrium reactor |
| `GibbsReactor` | Gibbs energy minimization reactor |

---

## Reactor Types

### Selection Guide

| Reactor | When to Use |
|---------|-------------|
| CSTR | Liquid-phase reactions, good mixing |
| PFR | Gas-phase reactions, no back-mixing |
| Equilibrium | Fast reactions at equilibrium |
| Gibbs | Complex equilibrium without specifying reactions |

---

## CSTR

Continuous Stirred Tank Reactor with perfect mixing.

### Basic Usage

```java
import neqsim.process.equipment.reactor.CSTRReactor;

CSTRReactor cstr = new CSTRReactor("R-100", feedStream);
cstr.setVolume(10.0, "m3");
cstr.setTemperature(400.0, "K");
cstr.run();
```

### With Reaction

```java
// Define reaction: A + B → C
cstr.addReaction("component_A", -1);  // reactant
cstr.addReaction("component_B", -1);  // reactant
cstr.addReaction("component_C", 1);   // product

// Reaction rate constant
cstr.setRateConstant(0.1, "1/s");

cstr.run();
```

### Residence Time

$$\tau = \frac{V}{\dot{Q}}$$

Where:
- $\tau$ = residence time
- $V$ = reactor volume
- $\dot{Q}$ = volumetric flow rate

---

## PFR

Plug Flow Reactor with no back-mixing.

### Basic Usage

```java
import neqsim.process.equipment.reactor.PFRReactor;

PFRReactor pfr = new PFRReactor("R-100", feedStream);
pfr.setLength(10.0, "m");
pfr.setDiameter(0.5, "m");
pfr.run();
```

### With Kinetics

```java
// Set reaction kinetics
pfr.setReaction(reaction);
pfr.setNumberOfReactorSegments(100);
pfr.run();
```

---

## Equilibrium Reactor

For reactions at chemical equilibrium.

### Basic Usage

```java
import neqsim.process.equipment.reactor.EquilibriumReactor;

EquilibriumReactor eqReactor = new EquilibriumReactor("R-100", feedStream);
eqReactor.setTemperature(500.0, "K");
eqReactor.setPressure(10.0, "bara");
eqReactor.run();
```

### Reaction Definition

```java
// Water-gas shift: CO + H2O ⇌ CO2 + H2
eqReactor.addReaction("CO", -1);
eqReactor.addReaction("H2O", -1);
eqReactor.addReaction("CO2", 1);
eqReactor.addReaction("H2", 1);

// Equilibrium constant
eqReactor.setEquilibriumConstant(Keq);
```

---

## Gibbs Reactor

Minimize Gibbs free energy to find equilibrium composition.

### Basic Usage

```java
import neqsim.process.equipment.reactor.GibbsReactor;

GibbsReactor gibbs = new GibbsReactor("R-100", feedStream);
gibbs.setTemperature(1000.0, "K");
gibbs.setPressure(10.0, "bara");
gibbs.run();

// Get equilibrium composition
Stream outlet = gibbs.getOutletStream();
```

### Constrained Minimization

```java
// Specify which elements to balance
gibbs.setElementBalanceCheck(true);

// Specify inert components
gibbs.setInertComponent("N2", true);
```

---

## Examples

### Example 1: Simple CSTR

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.reactor.CSTRReactor;

// Feed with reactants
SystemSrkEos feed = new SystemSrkEos(350.0, 5.0);
feed.addComponent("methanol", 0.5);
feed.addComponent("water", 0.5);
feed.setMixingRule("classic");

Stream feedStream = new Stream("Feed", feed);
feedStream.setFlowRate(1000.0, "kg/hr");
feedStream.run();

// Reactor
CSTRReactor reactor = new CSTRReactor("R-100", feedStream);
reactor.setVolume(5.0, "m3");
reactor.run();

double residenceTime = reactor.getResidenceTime("min");
System.out.println("Residence time: " + residenceTime + " min");
```

### Example 2: Steam Methane Reforming (Gibbs)

```java
// SMR: CH4 + H2O ⇌ CO + 3H2
SystemSrkEos feed = new SystemSrkEos(700.0, 20.0);
feed.addComponent("methane", 1.0);
feed.addComponent("water", 3.0);  // Steam to carbon ratio = 3
feed.setMixingRule("classic");

// Add possible products
feed.addComponent("CO", 0.0);
feed.addComponent("CO2", 0.0);
feed.addComponent("hydrogen", 0.0);

Stream feedStream = new Stream("SMR Feed", feed);
feedStream.setFlowRate(100.0, "kmol/hr");
feedStream.run();

// Gibbs reactor for equilibrium
GibbsReactor smr = new GibbsReactor("SMR Reactor", feedStream);
smr.setTemperature(1100.0, "K");
smr.setPressure(20.0, "bara");
smr.run();

// Results
Stream product = smr.getOutletStream();
System.out.println("H2 mole fraction: " + product.getFluid().getMoleFraction("hydrogen"));
System.out.println("CO mole fraction: " + product.getFluid().getMoleFraction("CO"));
System.out.println("CH4 conversion: " + 
    (1 - product.getFluid().getMoleFraction("methane") / 
     feedStream.getFluid().getMoleFraction("methane")) * 100 + " %");
```

### Example 3: Ammonia Synthesis

```java
// N2 + 3H2 ⇌ 2NH3
SystemSrkEos synthGas = new SystemSrkEos(700.0, 200.0);
synthGas.addComponent("nitrogen", 1.0);
synthGas.addComponent("hydrogen", 3.0);
synthGas.addComponent("ammonia", 0.0);
synthGas.setMixingRule("classic");

Stream feed = new Stream("Syngas", synthGas);
feed.setFlowRate(1000.0, "kmol/hr");
feed.run();

EquilibriumReactor ammoniaReactor = new EquilibriumReactor("Ammonia Reactor", feed);
ammoniaReactor.setTemperature(700.0, "K");
ammoniaReactor.setPressure(200.0, "bara");
ammoniaReactor.run();

double nh3Prod = ammoniaReactor.getOutletStream().getFluid().getMoleFraction("ammonia");
System.out.println("Ammonia mole fraction: " + nh3Prod);
```

---

## Related Documentation

- [Equipment Index](README) - All equipment
- [Chemical Reactions](../../chemicalreactions/README) - Reaction modeling
- [Heat Exchangers](heat_exchangers) - Reactor heat exchange
