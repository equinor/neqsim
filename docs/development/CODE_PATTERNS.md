---
title: "NeqSim Code Patterns"
description: "Copy-paste code patterns for every common NeqSim task. Covers fluids, flash, process equipment, PVT, tests, and Jupyter notebooks. Java 8 compatible."
---

# NeqSim Code Patterns

> Copy-paste starters for every common task type. All Java 8 compatible.

## Table of Contents

- [Fluid Creation](#fluid-creation)
- [Flash Calculations](#flash-calculations)
- [Reading Properties](#reading-properties)
- [Oil Characterization](#oil-characterization)
- [Process Equipment](#process-equipment)
- [Complete Process Flowsheet](#complete-process-flowsheet)
- [Recycle and Adjuster](#recycle-and-adjuster)
- [PVT Simulations](#pvt-simulations)
- [Standards Calculations](#standards-calculations)
- [Test Patterns](#test-patterns)
- [Jupyter Notebook Patterns](#jupyter-notebook-patterns)
- [Unit Conversion Reference](#unit-conversion-reference)

---

## Fluid Creation

### Simple Gas

```java
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");
```

### Gas with CO2 and H2S (Sour Gas)

```java
SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 80.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("CO2", 0.10);
fluid.addComponent("H2S", 0.05);
fluid.addComponent("nitrogen", 0.05);
fluid.setMixingRule("classic");
```

### Wet Gas (with Water — CPA required)

```java
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 30.0, 50.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("water", 0.10);
fluid.setMixingRule(10); // numeric rule for CPA
fluid.setMultiPhaseCheck(true);
```

### Gas with MEG (Hydrate Inhibitor)

```java
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 5.0, 100.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("water", 0.10);
fluid.addComponent("MEG", 0.02);
fluid.setMixingRule(10);
fluid.setMultiPhaseCheck(true);
```

### Rich Gas / Condensate

```java
SystemInterface fluid = new SystemPrEos(273.15 + 80.0, 150.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("i-butane", 0.02);
fluid.addComponent("n-butane", 0.03);
fluid.addComponent("i-pentane", 0.01);
fluid.addComponent("n-pentane", 0.01);
fluid.addComponent("n-hexane", 0.005);
fluid.addComponent("CO2", 0.03);
fluid.addComponent("nitrogen", 0.02);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);
```

---

## Flash Calculations

```java
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

// TP flash (given T and P)
ops.TPflash();

// Bubble/dew point
ops.bubblePointPressureFlash(false);  // finds bubble P at current T
ops.dewPointTemperatureFlash();       // finds dew T at current P

// PH flash (constant pressure, given enthalpy)
fluid.init(3);
double enthalpy = fluid.getEnthalpy();
fluid.setPressure(newPressure);
ops.PHflash(enthalpy);

// PS flash (constant pressure, given entropy)
double entropy = fluid.getEntropy();
ops.PSflash(entropy);

// Phase envelope
ops.calcPTphaseEnvelope();
double[][] phaseEnvelope = ops.get2phaseTVPdata();
```

---

## Reading Properties

```java
// IMPORTANT: call init(3) after flash for full property calculation
fluid.init(3);

// System-level
double density = fluid.getDensity("kg/m3");
double molarMass = fluid.getMolarMass("kg/mol");
double Z = fluid.getZ();
int numPhases = fluid.getNumberOfPhases();

// Phase-level
double gasDensity = fluid.getPhase("gas").getDensity("kg/m3");
double oilVisc = fluid.getPhase("oil").getViscosity("kg/msec");
double surfTension = fluid.getInterphaseProperties().getSurfaceTension(
    fluid.getPhaseIndex("gas"), fluid.getPhaseIndex("oil"));

// Component in a phase
double methaneInGas = fluid.getPhase("gas").getComponent("methane").getx(); // mole fraction

// Stream-level (after process.run())
double temp = stream.getTemperature("C");     // Celsius
double pres = stream.getPressure("bara");
double flow = stream.getFlowRate("kg/hr");
double mflow = stream.getFlowRate("MSm3/day"); // million Sm3/day
```

---

## Oil Characterization

### TBP Fractions

```java
SystemInterface oil = new SystemPrEos(273.15 + 80.0, 50.0);
oil.addComponent("methane", 0.30);
oil.addComponent("ethane", 0.05);
oil.addComponent("propane", 0.03);

// addTBPfraction(name, moleFraction, molarMass_kg/mol, density_g/cm3)
oil.addTBPfraction("C7", 0.10, 92.0 / 1000.0, 0.727);
oil.addTBPfraction("C8", 0.08, 104.0 / 1000.0, 0.749);
oil.addTBPfraction("C9", 0.06, 121.0 / 1000.0, 0.768);
oil.addTBPfraction("C10", 0.05, 134.0 / 1000.0, 0.781);

// Plus fraction
oil.addPlusFraction("C11+", 0.33, 250.0 / 1000.0, 0.85);
oil.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(6);
oil.getCharacterization().characterise();
oil.setMixingRule("classic");
oil.setMultiPhaseCheck(true);
```

---

## Process Equipment

### Separator

```java
Separator sep = new Separator("HP Separator", feedStream);
// Outlets:
StreamInterface gasOut = sep.getGasOutStream();
StreamInterface liquidOut = sep.getLiquidOutStream();

// Three-phase:
ThreePhaseSeparator sep3 = new ThreePhaseSeparator("3-Phase Sep", feedStream);
StreamInterface gasOut = sep3.getGasOutStream();
StreamInterface oilOut = sep3.getOilOutStream();
StreamInterface waterOut = sep3.getWaterOutStream();
```

### Compressor

```java
Compressor comp = new Compressor("Compressor", gasStream);
comp.setOutletPressure(120.0, "bara");
// OR set pressure ratio:
// comp.setPressureRatio(3.0);
comp.setIsentropicEfficiency(0.75);
// After run:
double power = comp.getPower("kW");
double outletT = comp.getOutletStream().getTemperature("C");
```

### Cooler / Heater

```java
Cooler cooler = new Cooler("Aftercooler", compressor.getOutletStream());
cooler.setOutTemperature(273.15 + 35.0); // Kelvin!
// After run:
double duty = cooler.getDuty(); // Watts (negative = cooling)

Heater heater = new Heater("Preheater", feedStream);
heater.setOutTemperature(273.15 + 80.0);
```

### Valve

```java
ThrottlingValve valve = new ThrottlingValve("JT Valve", stream);
valve.setOutletPressure(20.0, "bara");
// After run: check outlet temperature (JT cooling)
double outT = valve.getOutletStream().getTemperature("C");
```

### Pump

```java
Pump pump = new Pump("Export Pump", liquidStream);
pump.setOutletPressure(80.0, "bara");
pump.setIsentropicEfficiency(0.75);
// After run:
double power = pump.getPower("kW");
```

### Heat Exchanger

```java
HeatExchanger hx = new HeatExchanger("Lean/Rich HX", hotStream);
hx.setFeedStream(1, coldStream);
hx.setUAvalue(15000.0); // W/K
// After run:
double duty = hx.getDuty();
```

### Mixer

```java
Mixer mixer = new Mixer("Mixer");
mixer.addStream(stream1);
mixer.addStream(stream2);
StreamInterface mixed = mixer.getOutletStream();
```

### Splitter

```java
Splitter splitter = new Splitter("Splitter", feedStream);
splitter.setSplitNumber(2);
splitter.setSplitFactors(new double[]{0.7, 0.3});
StreamInterface out1 = splitter.getSplitStream(0);
StreamInterface out2 = splitter.getSplitStream(1);
```

### Pipeline

```java
AdiabaticPipe pipe = new AdiabaticPipe("Pipeline", feedStream);
pipe.setLength(50000.0);       // meters
pipe.setDiameter(0.508);       // meters (20 inch)
pipe.setPipeWallRoughness(5e-5); // meters
// After run:
double outP = pipe.getOutletStream().getPressure("bara");
```

---

## Complete Process Flowsheet

### Gas Compression Train

```java
ProcessSystem process = new ProcessSystem();

// Feed
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(50000.0, "kg/hr");
feed.setTemperature(30.0, "C");
feed.setPressure(10.0, "bara");
process.add(feed);

// Stage 1
Compressor comp1 = new Compressor("Stage 1", feed);
comp1.setOutletPressure(30.0, "bara");
comp1.setIsentropicEfficiency(0.75);
process.add(comp1);

Cooler cool1 = new Cooler("Intercooler 1", comp1.getOutletStream());
cool1.setOutTemperature(273.15 + 35.0);
process.add(cool1);

// Stage 2
Compressor comp2 = new Compressor("Stage 2", cool1.getOutletStream());
comp2.setOutletPressure(90.0, "bara");
comp2.setIsentropicEfficiency(0.75);
process.add(comp2);

Cooler cool2 = new Cooler("Aftercooler", comp2.getOutletStream());
cool2.setOutTemperature(273.15 + 35.0);
process.add(cool2);

process.run();

double totalPower = comp1.getPower("kW") + comp2.getPower("kW");
System.out.println("Total power: " + totalPower + " kW");
```

---

## Recycle and Adjuster

### Recycle (Iteration Loop)

```java
// Used when an outlet stream feeds back to an earlier point
Recycle recycle = new Recycle("recycle");
recycle.addStream(returnStream);       // the stream coming back
recycle.setOutletStream(inletStream);  // the stream it feeds into
recycle.setTolerance(1e-4);
process.add(recycle);
// ProcessSystem.run() will iterate until recycle converges
```

### Adjuster (Match a Spec)

```java
// Adjust one variable to match a target
Adjuster adj = new Adjuster("water dew point adj");
adj.setAdjustedVariable(tegStream, "flow rate", "kg/hr");  // what to vary
adj.setTargetVariable(gasOut, "waterDewPointTemperature", "C"); // what to match
adj.setTargetValue(-18.0);  // target value
adj.setMaxAdjustmentSteps(50);
process.add(adj);
```

---

## PVT Simulations

### Constant Mass Expansion (CME)

```java
SystemInterface fluid = new SystemPrEos(273.15 + 100.0, 300.0);
// add components...
fluid.setMixingRule("classic");

ConstantMassExpansion cme = new ConstantMassExpansion(fluid);
cme.setTemperature(273.15 + 100.0);
cme.setPressures(new double[]{400, 350, 300, 250, 200, 150, 100, 50});
cme.runCalc();

double satP = cme.getSaturationPressure();
double[] relVol = cme.getRelativeVolume();
```

### Constant Volume Depletion (CVD)

```java
ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid);
cvd.setTemperature(273.15 + 100.0);
cvd.setPressures(pressures);
cvd.runCalc();
```

---

## Standards Calculations

### Gas Quality (ISO 6976)

```java
SystemInterface gas = new SystemSrkEos(273.15 + 15.0, 1.01325);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.06);
gas.addComponent("propane", 0.03);
gas.addComponent("CO2", 0.01);
gas.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(gas);
ops.TPflash();

Standard_ISO6976 iso = new Standard_ISO6976(gas);
iso.setReferenceState("15C");
iso.calculate();
double gcv = iso.getValue("GCV");
double wobbe = iso.getValue("WobbeIndex");
```

---

## Test Patterns

### Basic Process Test

```java
package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class CompressorFeatureTest extends neqsim.NeqSimTest {

    @Test
    void testCompressorPower() {
        SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 10.0);
        fluid.addComponent("methane", 0.9);
        fluid.addComponent("ethane", 0.1);
        fluid.setMixingRule("classic");

        ProcessSystem process = new ProcessSystem();

        Stream feed = new Stream("feed", fluid);
        feed.setFlowRate(10000.0, "kg/hr");
        feed.setPressure(10.0, "bara");
        feed.setTemperature(30.0, "C");
        process.add(feed);

        Compressor comp = new Compressor("comp", feed);
        comp.setOutletPressure(30.0, "bara");
        comp.setIsentropicEfficiency(0.75);
        process.add(comp);

        process.run();

        assertTrue(comp.getPower("kW") > 0, "Compressor should consume power");
        assertTrue(comp.getOutletStream().getTemperature("C") > 30.0,
            "Outlet should be hotter than inlet");
        assertEquals(30.0, comp.getOutletStream().getPressure("bara"), 0.01);
    }
}
```

### Regression Test (Baseline Values)

```java
@Test
void testBaselineValues() {
    // Setup...
    process.run();

    // Capture these values once, then assert they don't drift
    assertEquals(245.3, comp.getPower("kW"), 5.0,
        "Power should be ~245 kW (baseline from 2026-03-01)");
    assertEquals(98.7, stream.getTemperature("C"), 1.0,
        "Outlet T should be ~99°C");
}
```

---

## Jupyter Notebook Patterns

### Standard Setup Cell (pip)

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
```

### Devtools Setup Cell (local dev)

```python
from neqsim_dev_setup import neqsim_init, neqsim_classes
ns = neqsim_init(recompile=False)
ns = neqsim_classes(ns)

# Use: ns.SystemSrkEos, ns.Stream, ns.Compressor, etc.
```

### Results Display

```python
import pandas as pd

process.run()

results = {
    "Equipment": ["Compressor", "Cooler"],
    "Power/Duty (kW)": [
        float(comp.getPower("kW")),
        float(cooler.getDuty()) / 1000.0
    ],
    "Outlet T (°C)": [
        float(comp.getOutletStream().getTemperature("C")),
        float(cooler.getOutletStream().getTemperature("C"))
    ],
    "Outlet P (bara)": [
        float(comp.getOutletStream().getPressure("bara")),
        float(cooler.getOutletStream().getPressure("bara"))
    ],
}
pd.DataFrame(results)
```

---

## Unit Conversion Reference

| Property | NeqSim Default | Common Units |
|----------|---------------|--------------|
| Temperature | Kelvin | `"C"`, `"K"` |
| Pressure | bara | `"bara"`, `"barg"`, `"Pa"`, `"psi"` |
| Flow rate | — | `"kg/hr"`, `"m3/hr"`, `"MSm3/day"`, `"MMSCFD"` |
| Power | Watts | `"W"`, `"kW"`, `"MW"`, `"hp"` |
| Density | kg/m3 | `"kg/m3"`, `"lb/ft3"` |
| Viscosity | — | `"kg/msec"`, `"cP"` |
| Molar mass | kg/mol | `"kg/mol"`, `"g/mol"` |

Constructor temperature is **always Kelvin**. Use `273.15 + celsius`.
`setTemperature(30.0, "C")` on streams accepts a unit string.
`getTemperature()` without a unit returns **Kelvin**.
