---
title: Reactors
description: "Documentation for chemical reactor equipment in NeqSim: plug flow reactor (PFR), stirred tank reactor (CSTR), Gibbs equilibrium reactor, stoichiometric reactor, ammonia synthesis reactor, sulfur deposition analyser, and bio-processing reactors."
---

# Reactors

NeqSim provides a family of reactor models for gas-phase kinetics, chemical equilibrium,
stoichiometric conversion, catalytic reactions, and bio-processing. All reactors live in the
`neqsim.process.equipment.reactor` package and integrate with `ProcessSystem` flowsheets.

## Table of Contents

- [Overview](#overview)
- [Reactor Selection Guide](#reactor-selection-guide)
- [Plug Flow Reactor (PFR)](#plug-flow-reactor-pfr)
- [Stirred Tank Reactor (CSTR)](#stirred-tank-reactor-cstr)
- [Gibbs Reactor](#gibbs-reactor)
- [Stoichiometric Reactor](#stoichiometric-reactor)
- [Ammonia Synthesis Reactor](#ammonia-synthesis-reactor)
- [Sulfur Deposition Analyser](#sulfur-deposition-analyser)
- [Furnace Burner](#furnace-burner)
- [Bio-Processing Reactors](#bio-processing-reactors)
- [Related Documentation](#related-documentation)

---

## Overview

**Package:** `neqsim.process.equipment.reactor`

| Class | Description |
|-------|-------------|
| `PlugFlowReactor` | Tubular reactor with ODE-based kinetics, catalyst bed, pressure drop |
| `KineticReaction` | Rate expression (power-law, LHHW, reversible Arrhenius) |
| `CatalystBed` | Packed bed properties, Ergun pressure drop, Thiele modulus |
| `ReactorAxialProfile` | Axial position profiles with interpolation and export |
| `StirredTankReactor` | Continuous stirred tank reactor (CSTR) |
| `GibbsReactor` | Gibbs free energy minimization for equilibrium |
| `GibbsReactorCO2` | Gibbs reactor variant specialized for CO2 reactions |
| `StoichiometricReaction` | Fixed-conversion stoichiometric reactor |
| `AmmoniaSynthesisReactor` | Specialized reactor for ammonia synthesis |
| `SulfurDepositionAnalyser` | Sulfur solubility, deposition onset, corrosion assessment |
| `FurnaceBurner` | Fired heater / furnace burner |
| `Fermenter` | Fermentation reactor for bio-processing |
| `EnzymeTreatment` | Enzyme-based treatment reactor |

---

## Reactor Selection Guide

| Reactor | When to Use |
|---------|-------------|
| `PlugFlowReactor` | Gas-phase catalytic or homogeneous reactions with axial gradients |
| `StirredTankReactor` | Liquid-phase reactions, good mixing, residence time calculations |
| `GibbsReactor` | Complex equilibrium without specifying reaction stoichiometry |
| `StoichiometricReaction` | Known fixed conversion, simple material balance |
| `AmmoniaSynthesisReactor` | Haber-Bosch ammonia synthesis modeling |
| `SulfurDepositionAnalyser` | Sulfur precipitation, H2S reactions, corrosion assessment |

---

## Plug Flow Reactor (PFR)

The `PlugFlowReactor` is the most comprehensive reactor in NeqSim. It solves coupled ODEs
for species molar flows, temperature, and pressure as a function of axial position.

**Key features:**
- Power-law, LHHW, and reversible equilibrium kinetics
- Adiabatic, isothermal, and coolant heat exchange modes
- Ergun equation pressure drop for packed catalyst beds
- Euler and RK4 integration methods
- Multi-tube reactor geometry
- Full thermodynamic coupling via NeqSim EOS

### Quick Example

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.reactor.PlugFlowReactor;
import neqsim.process.equipment.reactor.KineticReaction;
import neqsim.process.equipment.reactor.CatalystBed;

// Feed gas
SystemSrkEos gas = new SystemSrkEos(273.15 + 300.0, 20.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.10);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(10.0, "mole/sec");
feed.run();

// Reaction: methane -> ethane (illustrative)
KineticReaction rxn = new KineticReaction("A to B");
rxn.addReactant("methane", 1.0, 1.0);
rxn.addProduct("ethane", 1.0);
rxn.setPreExponentialFactor(1.0e4);
rxn.setActivationEnergy(50000.0);
rxn.setHeatOfReaction(-50000.0);

// Catalyst
CatalystBed catalyst = new CatalystBed(3.0, 0.40, 800.0);

// Reactor
PlugFlowReactor pfr = new PlugFlowReactor("PFR-1", feed);
pfr.addReaction(rxn);
pfr.setCatalystBed(catalyst);
pfr.setLength(5.0, "m");
pfr.setDiameter(0.10, "m");
pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ADIABATIC);
pfr.setNumberOfSteps(100);
pfr.setKeyComponent("methane");
pfr.run();

System.out.println("Conversion: " + pfr.getConversion());
System.out.println("Outlet T: " + (pfr.getOutletTemperature() - 273.15) + " C");
System.out.println("Pressure drop: " + pfr.getPressureDrop() + " bar");
```

> **Full documentation:** See the [Plug Flow Reactor Guide](plug_flow_reactor.md) for
> governing equations, all kinetic models, LHHW setup, coolant mode, multi-tube reactors,
> Python usage, and the complete API reference.

---

## Stirred Tank Reactor (CSTR)

The `StirredTankReactor` models a continuous stirred tank reactor with perfect mixing.

```java
import neqsim.process.equipment.reactor.StirredTankReactor;

StirredTankReactor cstr = new StirredTankReactor("R-100", feedStream);
cstr.run();
```

---

## Gibbs Reactor

Minimizes Gibbs free energy to find equilibrium composition without requiring explicit reaction
stoichiometry. Uses Newton-Raphson iteration with element balance constraints.

```java
import neqsim.process.equipment.reactor.GibbsReactor;

// Feed must include all possible product species (even at zero mole fraction)
SystemSrkEos gas = new SystemSrkEos(273.15 + 800.0, 20.0);
gas.addComponent("methane", 1.0);
gas.addComponent("water", 3.0);
gas.addComponent("CO", 1.0e-10);
gas.addComponent("CO2", 1.0e-10);
gas.addComponent("hydrogen", 1.0e-10);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(100.0, "kmol/hr");
feed.run();

GibbsReactor gibbs = new GibbsReactor("SMR Reactor", feed);
gibbs.run();
```

Key methods:
- `setComponentAsInert(String name)` — mark a component as non-reactive
- `hasConverged()` — check convergence status
- `getActualIterations()` — iteration count
- `getEnthalpyOfReactions()` — heat released/absorbed

---

## Stoichiometric Reactor

Fixed-conversion reactor based on specified stoichiometry.

```java
import neqsim.process.equipment.reactor.StoichiometricReaction;

StoichiometricReaction stoich = new StoichiometricReaction("R-Stoich", feedStream);
stoich.run();
```

---

## Ammonia Synthesis Reactor

Specialized reactor for the Haber-Bosch process (N2 + 3 H2 &#8652; 2 NH3).

```java
import neqsim.process.equipment.reactor.AmmoniaSynthesisReactor;

AmmoniaSynthesisReactor nh3 = new AmmoniaSynthesisReactor("NH3-Reactor", feedStream);
nh3.run();
```

---

## Sulfur Deposition Analyser

Analyses sulfur solubility, deposition onset temperature, chemical equilibrium of H2S/O2
reactions, corrosion (FeS formation), and blockage risk. Performs temperature sweep analysis.

```java
import neqsim.process.equipment.reactor.SulfurDepositionAnalyser;

SulfurDepositionAnalyser analyser = new SulfurDepositionAnalyser("S-Analyser", feedStream);
analyser.setTemperatureSweepRange(0.0, 200.0, 5.0);
analyser.setRunChemicalEquilibrium(true);
analyser.setRunSolidFlash(true);
analyser.setRunCorrosionAssessment(true);
analyser.run();

double onset = analyser.getSulfurDepositionOnsetTemperature();
String json = analyser.getResultsAsJson();
```

---

## Furnace Burner

Models a fired heater / furnace burner for high-temperature heating.

```java
import neqsim.process.equipment.reactor.FurnaceBurner;

FurnaceBurner burner = new FurnaceBurner("Furnace", feedStream);
burner.run();
```

---

## Bio-Processing Reactors

### Fermenter

Models a biological fermentation reactor.

```java
import neqsim.process.equipment.reactor.Fermenter;

Fermenter fermenter = new Fermenter("BioReactor", feedStream);
fermenter.run();
```

### Enzyme Treatment

Models an enzyme-based treatment step.

```java
import neqsim.process.equipment.reactor.EnzymeTreatment;

EnzymeTreatment enzyme = new EnzymeTreatment("EnzymeUnit", feedStream);
enzyme.run();
```

---

## Related Documentation

- [Plug Flow Reactor Guide](plug_flow_reactor.md) — Comprehensive PFR documentation with equations, kinetics, and examples
- [PFR Implementation Plan](../PLUG_FLOW_REACTOR_IMPLEMENTATION_PLAN.md) — Design decisions and commercial comparison
- [Equipment Index](index.md) — All NeqSim equipment types
- [Chemical Reactions](../../chemicalreactions/) — Reaction modeling background
