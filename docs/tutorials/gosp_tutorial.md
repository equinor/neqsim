---
title: Gas-Oil Separation Plant (GOSP) Tutorial
description: Complete tutorial for modeling gas-oil separation plants in NeqSim. Covers multi-stage separation, HP/MP/LP trains, export pump, and optimization for oil recovery and gas quality.
---

# Gas-Oil Separation Plant (GOSP) Tutorial

This tutorial demonstrates how to model a complete gas-oil separation plant (GOSP) in NeqSim, from wellhead through export. A GOSP separates the well stream into stabilized crude oil, associated gas, and produced water.

## Overview

### Typical GOSP Configuration

```
Well Stream → HP Separator → MP Separator → LP Separator → Oil Export
                  ↓              ↓              ↓
              HP Gas         MP Gas         LP Gas → Compression
                  ↓              ↓              ↓
                  └──────────────┴──────────────┘
                              ↓
                        Gas Compression → Export Gas
```

---

## Quick Start: 3-Stage Separation

### Java Example

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.thermo.system.SystemSrkEos;

public class GOSPSimulation {
    public static void main(String[] args) {
        
        // === CREATE WELL FLUID ===
        SystemSrkEos wellFluid = new SystemSrkEos(353.15, 50.0);  // 80°C, 50 bara
        
        // Typical North Sea oil composition
        wellFluid.addComponent("nitrogen", 0.005);
        wellFluid.addComponent("CO2", 0.02);
        wellFluid.addComponent("methane", 0.35);
        wellFluid.addComponent("ethane", 0.08);
        wellFluid.addComponent("propane", 0.06);
        wellFluid.addComponent("i-butane", 0.02);
        wellFluid.addComponent("n-butane", 0.03);
        wellFluid.addComponent("i-pentane", 0.015);
        wellFluid.addComponent("n-pentane", 0.02);
        wellFluid.addComponent("n-hexane", 0.025);
        wellFluid.addComponent("n-heptane", 0.04);
        wellFluid.addComponent("n-octane", 0.05);
        wellFluid.addComponent("n-nonane", 0.04);
        wellFluid.addComponent("nC10", 0.03);
        wellFluid.addTBPfraction("C11", 0.05, 150.0, 0.78);
        wellFluid.addTBPfraction("C15", 0.04, 210.0, 0.82);
        wellFluid.addTBPfraction("C20+", 0.06, 350.0, 0.88);
        wellFluid.addComponent("water", 0.05);
        
        wellFluid.setMixingRule("classic");
        wellFluid.setMultiPhaseCheck(true);
        
        // === BUILD PROCESS ===
        ProcessSystem gosp = new ProcessSystem();
        
        // Well stream
        Stream wellStream = new Stream("Well Stream", wellFluid);
        wellStream.setFlowRate(50000.0, "kg/hr");
        wellStream.setTemperature(80.0, "C");
        wellStream.setPressure(50.0, "bara");
        gosp.add(wellStream);
        
        // === HP SEPARATOR (1st Stage) ===
        // Pressure: 40-50 bara typical
        ThreePhaseSeparator hpSeparator = new ThreePhaseSeparator("HP Separator", wellStream);
        gosp.add(hpSeparator);
        
        Stream hpGas = hpSeparator.getGasOutStream();
        hpGas.setName("HP Gas");
        gosp.add(hpGas);
        
        Stream hpOil = hpSeparator.getOilOutStream();
        hpOil.setName("HP Oil");
        gosp.add(hpOil);
        
        Stream hpWater = hpSeparator.getWaterOutStream();
        hpWater.setName("HP Water");
        gosp.add(hpWater);
        
        // === MP SEPARATOR (2nd Stage) ===
        // Pressure: 8-15 bara typical
        ThrottlingValve mpValve = new ThrottlingValve("MP Valve", hpOil);
        mpValve.setOutletPressure(10.0);
        gosp.add(mpValve);
        
        ThreePhaseSeparator mpSeparator = new ThreePhaseSeparator("MP Separator", 
            mpValve.getOutletStream());
        gosp.add(mpSeparator);
        
        Stream mpGas = mpSeparator.getGasOutStream();
        mpGas.setName("MP Gas");
        gosp.add(mpGas);
        
        Stream mpOil = mpSeparator.getOilOutStream();
        mpOil.setName("MP Oil");
        gosp.add(mpOil);
        
        // === LP SEPARATOR (3rd Stage) ===
        // Pressure: 1.5-3 bara typical
        ThrottlingValve lpValve = new ThrottlingValve("LP Valve", mpOil);
        lpValve.setOutletPressure(2.0);
        gosp.add(lpValve);
        
        ThreePhaseSeparator lpSeparator = new ThreePhaseSeparator("LP Separator", 
            lpValve.getOutletStream());
        gosp.add(lpSeparator);
        
        Stream lpGas = lpSeparator.getGasOutStream();
        lpGas.setName("LP Gas");
        gosp.add(lpGas);
        
        Stream exportOil = lpSeparator.getOilOutStream();
        exportOil.setName("Export Crude");
        gosp.add(exportOil);
        
        // === GAS COMPRESSION ===
        // LP gas compression to MP pressure
        Compressor lpCompressor = new Compressor("LP Compressor", lpGas);
        lpCompressor.setOutletPressure(10.0);
        gosp.add(lpCompressor);
        
        Cooler lpCooler = new Cooler("LP Gas Cooler", lpCompressor.getOutletStream());
        lpCooler.setOutTemperature(40.0, "C");
        gosp.add(lpCooler);
        
        // Mix LP compressed gas with MP gas
        Mixer mpGasMixer = new Mixer("MP Gas Mixer");
        mpGasMixer.addStream(lpCooler.getOutletStream());
        mpGasMixer.addStream(mpGas);
        gosp.add(mpGasMixer);
        
        // MP to HP compression
        Compressor mpCompressor = new Compressor("MP Compressor", mpGasMixer.getOutletStream());
        mpCompressor.setOutletPressure(50.0);
        gosp.add(mpCompressor);
        
        Cooler mpCooler = new Cooler("MP Gas Cooler", mpCompressor.getOutletStream());
        mpCooler.setOutTemperature(40.0, "C");
        gosp.add(mpCooler);
        
        // Mix with HP gas for export
        Mixer exportGasMixer = new Mixer("Export Gas Mixer");
        exportGasMixer.addStream(hpGas);
        exportGasMixer.addStream(mpCooler.getOutletStream());
        gosp.add(exportGasMixer);
        
        Stream exportGas = exportGasMixer.getOutletStream();
        exportGas.setName("Export Gas");
        gosp.add(exportGas);
        
        // === RUN SIMULATION ===
        gosp.run();
        
        // === RESULTS ===
        System.out.println("========== GOSP SIMULATION RESULTS ==========");
        System.out.println();
        
        // Feed summary
        System.out.println("--- FEED ---");
        System.out.printf("Well stream: %.1f kg/hr (%.1f bbl/day oil equivalent)%n",
            wellStream.getFlowRate("kg/hr"),
            wellStream.getFlowRate("kg/hr") * 24 / 159.0 / 0.85);
        System.out.printf("Feed GOR: %.0f Sm3/Sm3%n", 
            wellFluid.getGOR());
        System.out.println();
        
        // Separator performance
        System.out.println("--- SEPARATOR PERFORMANCE ---");
        System.out.printf("HP Separator: P=%.1f bara, T=%.1f C%n",
            hpSeparator.getPressure(), hpSeparator.getTemperature() - 273.15);
        System.out.printf("  Gas: %.1f kg/hr%n", hpGas.getFlowRate("kg/hr"));
        System.out.printf("  Oil: %.1f kg/hr%n", hpOil.getFlowRate("kg/hr"));
        System.out.printf("  Water: %.1f kg/hr%n", hpWater.getFlowRate("kg/hr"));
        System.out.println();
        
        System.out.printf("MP Separator: P=%.1f bara, T=%.1f C%n",
            mpSeparator.getPressure(), mpSeparator.getTemperature() - 273.15);
        System.out.printf("  Gas: %.1f kg/hr%n", mpGas.getFlowRate("kg/hr"));
        System.out.printf("  Oil: %.1f kg/hr%n", mpOil.getFlowRate("kg/hr"));
        System.out.println();
        
        System.out.printf("LP Separator: P=%.1f bara, T=%.1f C%n",
            lpSeparator.getPressure(), lpSeparator.getTemperature() - 273.15);
        System.out.printf("  Gas: %.1f kg/hr%n", lpGas.getFlowRate("kg/hr"));
        System.out.printf("  Oil: %.1f kg/hr%n", exportOil.getFlowRate("kg/hr"));
        System.out.println();
        
        // Product specifications
        System.out.println("--- PRODUCT SPECIFICATIONS ---");
        System.out.printf("Export Oil: %.1f kg/hr%n", exportOil.getFlowRate("kg/hr"));
        System.out.printf("  API Gravity: %.1f%n", 
            141.5 / (exportOil.getFluid().getDensity("kg/m3") / 1000.0) - 131.5);
        System.out.printf("  RVP (est): %.2f bara%n", 
            lpSeparator.getPressure());  // Approximation
        System.out.println();
        
        System.out.printf("Export Gas: %.1f kg/hr (%.2f MSm3/day)%n", 
            exportGas.getFlowRate("kg/hr"),
            exportGas.getFlowRate("Sm3/day") / 1e6);
        System.out.printf("  Methane: %.1f mol%%%n",
            exportGas.getFluid().getComponent("methane").getx() * 100);
        System.out.printf("  CO2: %.2f mol%%%n",
            exportGas.getFluid().getComponent("CO2").getx() * 100);
        System.out.println();
        
        // Compression power
        System.out.println("--- COMPRESSION DUTY ---");
        System.out.printf("LP Compressor: %.1f kW%n", lpCompressor.getPower("kW"));
        System.out.printf("MP Compressor: %.1f kW%n", mpCompressor.getPower("kW"));
        System.out.printf("Total compression: %.1f kW%n", 
            lpCompressor.getPower("kW") + mpCompressor.getPower("kW"));
    }
}
```

### Python Example

```python
from neqsim import jneqsim

# Import classes
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
ThreePhaseSeparator = jneqsim.process.equipment.separator.ThreePhaseSeparator
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
Mixer = jneqsim.process.equipment.mixer.Mixer

# Create well fluid
fluid = SystemSrkEos(273.15 + 80.0, 50.0)
fluid.addComponent("nitrogen", 0.005)
fluid.addComponent("CO2", 0.02)
fluid.addComponent("methane", 0.35)
fluid.addComponent("ethane", 0.08)
fluid.addComponent("propane", 0.06)
fluid.addComponent("i-butane", 0.02)
fluid.addComponent("n-butane", 0.03)
fluid.addComponent("i-pentane", 0.015)
fluid.addComponent("n-pentane", 0.02)
fluid.addComponent("n-hexane", 0.025)
fluid.addComponent("n-heptane", 0.04)
fluid.addComponent("n-octane", 0.05)
fluid.addTBPfraction("C10+", 0.10, 200.0, 0.82)
fluid.addComponent("water", 0.05)
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

# Build process
gosp = ProcessSystem()

# Feed
well = Stream("Well", fluid)
well.setFlowRate(50000.0, "kg/hr")
well.setTemperature(80.0, "C")
well.setPressure(50.0, "bara")
gosp.add(well)

# HP Separator
hp_sep = ThreePhaseSeparator("HP Sep", well)
gosp.add(hp_sep)

# MP Separator
mp_valve = ThrottlingValve("MP Valve", hp_sep.getOilOutStream())
mp_valve.setOutletPressure(10.0)
gosp.add(mp_valve)

mp_sep = ThreePhaseSeparator("MP Sep", mp_valve.getOutletStream())
gosp.add(mp_sep)

# LP Separator
lp_valve = ThrottlingValve("LP Valve", mp_sep.getOilOutStream())
lp_valve.setOutletPressure(2.0)
gosp.add(lp_valve)

lp_sep = ThreePhaseSeparator("LP Sep", lp_valve.getOutletStream())
gosp.add(lp_sep)

# Run
gosp.run()

# Results
print("=== GOSP Results ===")
print(f"Export Oil: {lp_sep.getOilOutStream().getFlowRate('kg/hr'):.0f} kg/hr")
print(f"HP Gas: {hp_sep.getGasOutStream().getFlowRate('kg/hr'):.0f} kg/hr")
print(f"MP Gas: {mp_sep.getGasOutStream().getFlowRate('kg/hr'):.0f} kg/hr")
print(f"LP Gas: {lp_sep.getGasOutStream().getFlowRate('kg/hr'):.0f} kg/hr")
```

---

## Design Considerations

### Optimal Separator Pressures

The goal is to maximize liquid recovery while meeting product specifications.

| Stage | Typical Pressure | Purpose |
|-------|------------------|---------|
| HP | 30-70 bara | Bulk gas removal, maximum gas at high pressure |
| MP | 8-15 bara | Intermediate separation, gas compression stages |
| LP | 1.5-3 bara | Final stabilization, meet RVP spec |

### Pressure Optimization

Use NeqSim to find optimal pressures for maximum oil recovery:

```java
// Iterate to find optimal MP pressure
double maxOilRecovery = 0;
double optimalPressure = 0;

for (double mpPressure = 5.0; mpPressure <= 20.0; mpPressure += 1.0) {
    mpValve.setOutletPressure(mpPressure);
    gosp.run();
    
    double oilRate = exportOil.getFlowRate("kg/hr");
    if (oilRate > maxOilRecovery) {
        maxOilRecovery = oilRate;
        optimalPressure = mpPressure;
    }
}
System.out.printf("Optimal MP pressure: %.1f bara%n", optimalPressure);
System.out.printf("Maximum oil recovery: %.1f kg/hr%n", maxOilRecovery);
```

---

## Product Specifications

### Export Crude Specifications

| Parameter | Typical Spec | Notes |
|-----------|--------------|-------|
| RVP | < 1 bara (14.7 psia) | Reid Vapor Pressure |
| BS&W | < 0.5% | Basic Sediment & Water |
| Salt content | < 10 PTB | Pounds per thousand barrels |
| API gravity | Report | Typically 25-45° API |
| H₂S | < 10 ppm | Safety requirement |

### Export Gas Specifications

| Parameter | Typical Spec | Notes |
|-----------|--------------|-------|
| Hydrocarbon dew point | < 0°C at delivery pressure | Prevent condensation |
| Water dew point | < -10°C | Prevent hydrates/corrosion |
| H₂S | < 4 ppm | Pipeline spec |
| CO₂ | < 2-3 mol% | Customer spec |
| Gross heating value | 35-43 MJ/Sm³ | Quality spec |

---

## Adding Heat Integration

For improved efficiency, add heat exchangers between hot and cold streams:

```java
// Example: Use hot export oil to preheat incoming well stream
HeatExchanger feedOilExchanger = new HeatExchanger("Feed/Oil Exchanger");
feedOilExchanger.setFeedStream(0, wellStream);      // Cold side (well stream in)
feedOilExchanger.setFeedStream(1, exportOil);       // Hot side (export oil)
feedOilExchanger.setUAvalue(5000.0);                // Heat transfer coefficient × area
gosp.add(feedOilExchanger);
```

---

## Water Treatment Integration

For platforms with produced water treatment:

```java
// Hydrocyclone for oil-in-water removal
Separator hydrocyclone = new Separator("Hydrocyclone", hpWater);
gosp.add(hydrocyclone);

// Produced water meets discharge spec (<30 mg/L oil)
Stream treatedWater = hydrocyclone.getLiquidOutStream();
treatedWater.setName("Treated Water");
gosp.add(treatedWater);
```

---

## Common Variations

### Stabilization Column

For deeper stabilization (lower RVP), replace LP separator with a stabilizer column:

```java
import neqsim.process.equipment.distillation.DistillationColumn;

// Stabilizer column instead of LP separator
DistillationColumn stabilizer = new DistillationColumn("Stabilizer", 10, true, false);
stabilizer.addFeedStream(mpOil, 5);  // Feed at middle tray
stabilizer.setCondenserTemperature(40.0, "C");
stabilizer.setReboilerTemperature(150.0, "C");
gosp.add(stabilizer);

Stream stabilizerOverhead = stabilizer.getGasOutStream();
Stream stabilizedCrude = stabilizer.getLiquidOutStream();
```

### FPSO Configuration

For floating production, add motion considerations:

```java
// Use larger separator sizes for FPSO (handle motion effects)
hpSeparator.setInternalDiameter(3.5);  // meters
hpSeparator.setLiquidLevel(0.5);       // fraction
hpSeparator.setSeparatorLength(15.0);  // meters
```

---

## Performance Metrics

Key performance indicators for GOSP operations:

| KPI | Formula | Target |
|-----|---------|--------|
| Oil recovery | (Export oil / Feed oil) × 100 | > 98% |
| Gas compression ratio | Export P / LP P | Minimize |
| Specific power | kW / MSm³/d gas | Minimize |
| Uptime | Operating hours / Available hours | > 95% |

---

## See Also

- [Separator Equipment](../process/separators) - Detailed separator documentation
- [Compressor Guide](../process/compressors) - Compression calculations
- [Process Recipes](../cookbook/process-recipes) - Quick recipes
- [Flow Assurance](../pvtsimulation/flowassurance/README) - Flow assurance considerations
