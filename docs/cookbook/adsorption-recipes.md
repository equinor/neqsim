---
title: "Adsorption Recipes"
description: "Quick-start recipes and practical examples for adsorption simulation in NeqSim. Covers isotherm model setup, PSA/TSA cycle design, material comparison, breakthrough analysis, and integration with process flowsheets."
---

# Adsorption Recipes

Practical recipes for common adsorption simulation tasks in NeqSim. Each recipe is self-contained with ready-to-use Java code.

**Prerequisites**: See [Adsorption Isotherm Models](../thermo/adsorption_isotherms.md) for thermodynamic theory and [Adsorption Bed](../process/equipment/adsorption_bed.md) for process simulation details.

---

## Quick Reference

| Recipe | Use Case |
|--------|----------|
| [Evaluate a Single Isotherm](#recipe-1-evaluate-a-single-isotherm) | Get loading for one gas on one solid |
| [Compare Adsorbent Materials](#recipe-2-compare-adsorbent-materials) | Screen materials for a separation |
| [Multi-Component Competitive Adsorption](#recipe-3-multi-component-competitive-adsorption) | Extended Langmuir/Sips for mixtures |
| [Steady-State Bed Sizing](#recipe-4-steady-state-bed-sizing) | Quick bed performance estimate |
| [Transient Breakthrough Curve](#recipe-5-transient-breakthrough-curve) | Track concentration front propagation |
| [PSA Cycle Simulation](#recipe-6-psa-cycle-simulation) | Full pressure swing cycle |
| [TSA Cycle Simulation](#recipe-7-tsa-cycle-simulation) | Full temperature swing cycle |
| [Effect of Temperature on Adsorption](#recipe-8-effect-of-temperature-on-adsorption) | Isotherm sensitivity study |
| [Bed in a Process Flowsheet](#recipe-9-bed-in-a-process-flowsheet) | Integrate with compressors, coolers, etc. |
| [Capillary Condensation in Mesopores](#recipe-10-capillary-condensation-in-mesopores) | Kelvin equation for mesoporous materials |

---

## Recipe 1: Evaluate a Single Isotherm

Calculate equilibrium loading of CO$_2$ on Zeolite 13X at various pressures.

```java
import neqsim.physicalproperties.interfaceproperties.solidadsorption.LangmuirAdsorption;
import neqsim.thermo.system.SystemSrkEos;

// Create gas system at 25°C
for (double pressure = 1.0; pressure <= 20.0; pressure += 1.0) {
    SystemSrkEos system = new SystemSrkEos(298.15, pressure);
    system.addComponent("CO2", 1.0);
    system.setMixingRule("classic");
    system.init(0);

    LangmuirAdsorption model = new LangmuirAdsorption(system);
    model.setSolidMaterial("Zeolite 13X");
    model.calcAdsorption(0);

    double loading = model.getSurfaceExcess(0); // mol/kg
    System.out.printf("P=%5.1f bar  q=%.3f mol/kg  coverage=%.3f%n",
        pressure, loading, model.getCoverage(0));
}
```

---

## Recipe 2: Compare Adsorbent Materials

Screen multiple adsorbents for CO$_2$ removal at process conditions.

```java
import neqsim.physicalproperties.interfaceproperties.solidadsorption.LangmuirAdsorption;
import neqsim.thermo.system.SystemSrkEos;

String[] materials = {"AC", "Zeolite 13X", "Zeolite 5A", "MOF HKUST-1",
                      "Silica Gel", "AC Calgon F400", "Alumina"};

SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
system.addComponent("CO2", 0.10);
system.addComponent("methane", 0.90);
system.setMixingRule("classic");
system.init(0);

System.out.printf("%-20s  %10s  %10s%n", "Material", "CO2 (mol/kg)", "CH4 (mol/kg)");
System.out.printf("%-20s  %10s  %10s%n", "--------", "-----------", "-----------");

for (String material : materials) {
    LangmuirAdsorption model = new LangmuirAdsorption(system);
    model.setSolidMaterial(material);
    model.calcAdsorption(0);

    System.out.printf("%-20s  %10.3f  %10.3f%n",
        material,
        model.getSurfaceExcess("CO2"),
        model.getSurfaceExcess("methane"));
}
```

---

## Recipe 3: Multi-Component Competitive Adsorption

Use the Extended Langmuir model for competitive CO$_2$/CH$_4$/N$_2$ adsorption.

```java
import neqsim.physicalproperties.interfaceproperties.solidadsorption.LangmuirAdsorption;
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
system.addComponent("methane", 0.85);
system.addComponent("CO2", 0.10);
system.addComponent("nitrogen", 0.05);
system.setMixingRule("classic");
system.init(0);

LangmuirAdsorption model = new LangmuirAdsorption(system);
model.setSolidMaterial("Zeolite 13X");
model.calcExtendedLangmuir(0);  // multi-component competition

for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
    String name = system.getPhase(0).getComponent(i).getComponentName();
    double loading = model.getSurfaceExcess(i);
    double moleFrac = model.getAdsorbedPhaseMoleFraction(i);
    System.out.printf("%-10s  q=%.4f mol/kg  x_ads=%.4f%n", name, loading, moleFrac);
}

// CO2/CH4 selectivity
double selectivity = model.getSelectivity(1, 0, 0); // CO2 over CH4
System.out.printf("CO2/CH4 selectivity: %.1f%n", selectivity);
```

---

## Recipe 4: Steady-State Bed Sizing

Quick estimate of adsorption bed performance without transient simulation.

```java
import neqsim.process.equipment.adsorber.AdsorptionBed;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos gas = new SystemSrkEos(298.15, 10.0);
gas.addComponent("methane", 0.85);
gas.addComponent("CO2", 0.10);
gas.addComponent("nitrogen", 0.05);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(5000.0, "kg/hr");
feed.run();

AdsorptionBed bed = new AdsorptionBed("CO2 Adsorber", feed);
bed.setBedDiameter(1.5);
bed.setBedLength(4.0);
bed.setAdsorbentMaterial("Zeolite 13X");
bed.setIsothermType(IsothermType.LANGMUIR);
bed.setKLDF(0.05);
bed.setCalculateSteadyState(true);  // default
bed.run();

System.out.println("Adsorbent mass: " + bed.getAdsorbentMass() + " kg");
System.out.println("Bed volume: " + bed.getBedVolume() + " m³");
System.out.println("Pressure drop: " + bed.getPressureDrop() + " Pa");

// Check outlet CO2 level
Stream outlet = (Stream) bed.getOutletStream();
double outletCO2 = outlet.getFluid().getPhase(0).getComponent("CO2").getx();
System.out.printf("Outlet CO2 mole fraction: %.6f%n", outletCO2);
```

---

## Recipe 5: Transient Breakthrough Curve

Track how the concentration front propagates through the bed and detect breakthrough.

```java
import neqsim.process.equipment.adsorber.AdsorptionBed;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;
import java.util.UUID;

// Setup (same as Recipe 4 feed)
SystemSrkEos gas = new SystemSrkEos(298.15, 10.0);
gas.addComponent("methane", 0.85);
gas.addComponent("CO2", 0.10);
gas.addComponent("nitrogen", 0.05);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(5000.0, "kg/hr");
feed.run();

AdsorptionBed bed = new AdsorptionBed("Breakthrough Test", feed);
bed.setBedDiameter(1.5);
bed.setBedLength(4.0);
bed.setAdsorbentMaterial("Zeolite 13X");
bed.setIsothermType(IsothermType.LANGMUIR);
bed.setKLDF(0.05);
bed.setNumberOfCells(100);
bed.setCalculateSteadyState(false);  // Enable transient mode
bed.setBreakthroughThreshold(0.05);

// Run transient simulation
double dt = 1.0;
UUID id = UUID.randomUUID();

System.out.printf("%8s  %12s  %12s  %10s%n", "Time(s)", "CO2_out/in", "MTZ(m)", "Avg_q");
for (int step = 0; step < 600; step++) {
    bed.runTransient(dt, id);

    if (step % 30 == 0) {
        double[] outConc = bed.getConcentrationProfile(1); // CO2
        double outletRatio = outConc[outConc.length - 1]
            / Math.max(outConc[0], 1e-20);
        double mtz = bed.getMassTransferZoneLength(1);
        double avgQ = bed.getAverageLoading(1);
        System.out.printf("%8.0f  %12.6f  %12.3f  %10.4f%n",
            bed.getElapsedTime(), outletRatio, mtz, avgQ);
    }

    if (bed.hasBreakthrough()) {
        System.out.printf("BREAKTHROUGH at t=%.1f s%n", bed.getBreakthroughTime());
        break;
    }
}
```

---

## Recipe 6: PSA Cycle Simulation

Simulate a complete Pressure Swing Adsorption cycle for CO$_2$ removal.

```java
import neqsim.process.equipment.adsorber.AdsorptionBed;
import neqsim.process.equipment.adsorber.AdsorptionCycleController;
import neqsim.process.equipment.adsorber.AdsorptionCycleController.CyclePhase;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;
import java.util.UUID;

// Feed gas
SystemSrkEos gas = new SystemSrkEos(298.15, 10.0);
gas.addComponent("methane", 0.90);
gas.addComponent("CO2", 0.10);
gas.setMixingRule("classic");

Stream feed = new Stream("PSA Feed", gas);
feed.setFlowRate(2000.0, "kg/hr");
feed.run();

// Adsorption bed
AdsorptionBed bed = new AdsorptionBed("PSA Bed", feed);
bed.setBedDiameter(1.2);
bed.setBedLength(3.5);
bed.setAdsorbentMaterial("Zeolite 13X");
bed.setAdsorbentBulkDensity(620.0);
bed.setIsothermType(IsothermType.LANGMUIR);
bed.setKLDF(0.05);
bed.setNumberOfCells(80);
bed.setCalculateSteadyState(false);

// PSA cycle: 5 min adsorption, 30s blowdown, 1 min purge, 30s repress
AdsorptionCycleController controller = new AdsorptionCycleController(bed);
controller.configurePSA(300.0, 30.0, 60.0, 30.0, 1.0);

// Run 3 complete cycles
double dt = 0.5;
UUID id = UUID.randomUUID();
double totalTime = 3 * 420.0; // 3 cycles × 420 s/cycle

for (double t = 0; t < totalTime; t += dt) {
    controller.advance(dt, id);
    bed.runTransient(dt, id);

    // Log every 30 seconds
    if (Math.abs(t % 30.0) < dt / 2) {
        System.out.printf("t=%6.0f s  Phase=%-15s  CO2_avg=%.3f mol/kg  Cycles=%d%n",
            t, controller.getCurrentPhase(),
            bed.getAverageLoading(1),
            controller.getCompletedCycles());
    }
}

System.out.printf("Completed %d PSA cycles%n", controller.getCompletedCycles());
```

---

## Recipe 7: TSA Cycle Simulation

Simulate a Temperature Swing Adsorption cycle for gas dehydration.

```java
import neqsim.process.equipment.adsorber.AdsorptionBed;
import neqsim.process.equipment.adsorber.AdsorptionCycleController;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;
import java.util.UUID;

// Wet gas feed
SystemSrkEos gas = new SystemSrkEos(303.15, 50.0); // 30°C, 50 bar
gas.addComponent("methane", 0.95);
gas.addComponent("water", 0.005);
gas.addComponent("CO2", 0.03);
gas.addComponent("ethane", 0.015);
gas.setMixingRule("classic");

Stream feed = new Stream("TSA Feed", gas);
feed.setFlowRate(10000.0, "kg/hr");
feed.run();

// Molecular sieve bed
AdsorptionBed bed = new AdsorptionBed("Dehydration Bed", feed);
bed.setBedDiameter(2.0);
bed.setBedLength(5.0);
bed.setAdsorbentMaterial("Zeolite 4A");
bed.setAdsorbentBulkDensity(700.0);
bed.setIsothermType(IsothermType.LANGMUIR);
bed.setKLDF(0.02);
bed.setNumberOfCells(60);
bed.setCalculateSteadyState(false);

// TSA: 30 min adsorption, 10 min heating at 250°C, 5 min cooling
AdsorptionCycleController controller = new AdsorptionCycleController(bed);
controller.configureTSA(1800.0, 600.0, 300.0, 523.15); // 250°C desorption

// Run 1 full cycle
double dt = 1.0;
UUID id = UUID.randomUUID();

for (double t = 0; t < 2700.0; t += dt) {
    controller.advance(dt, id);
    bed.runTransient(dt, id);
}

System.out.printf("Water avg loading after cycle: %.4f mol/kg%n",
    bed.getAverageLoading(1)); // water component index
```

---

## Recipe 8: Effect of Temperature on Adsorption

Study how temperature affects equilibrium loading (basis for TSA design).

```java
import neqsim.physicalproperties.interfaceproperties.solidadsorption.LangmuirAdsorption;
import neqsim.thermo.system.SystemSrkEos;

double pressure = 5.0; // bar

System.out.printf("%8s  %12s  %12s%n", "T (°C)", "CO2 (mol/kg)", "K (1/bar)");
for (double tempC = 0; tempC <= 200; tempC += 10) {
    double tempK = 273.15 + tempC;
    SystemSrkEos system = new SystemSrkEos(tempK, pressure);
    system.addComponent("CO2", 1.0);
    system.setMixingRule("classic");
    system.init(0);

    LangmuirAdsorption model = new LangmuirAdsorption(system);
    model.setSolidMaterial("Zeolite 13X");
    model.calcAdsorption(0);

    System.out.printf("%8.0f  %12.4f  %12.6f%n",
        tempC, model.getSurfaceExcess(0), model.getKLangmuir(0));
}
// Expect: loading decreases monotonically with temperature
// The ratio of loading at 25°C vs 200°C = working capacity for TSA
```

---

## Recipe 9: Bed in a Process Flowsheet

Integrate the adsorption bed with other NeqSim process equipment.

```java
import neqsim.process.equipment.adsorber.AdsorptionBed;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create feed
SystemSrkEos gas = new SystemSrkEos(298.15, 5.0);
gas.addComponent("methane", 0.85);
gas.addComponent("CO2", 0.10);
gas.addComponent("nitrogen", 0.05);
gas.setMixingRule("classic");

Stream feed = new Stream("Raw Gas", gas);
feed.setFlowRate(5000.0, "kg/hr");

// Compress feed to adsorption pressure
Compressor comp = new Compressor("Feed Compressor", feed);
comp.setOutletPressure(20.0);

// Cool after compression
Cooler cooler = new Cooler("Aftercooler", comp.getOutletStream());
cooler.setOutTemperature(298.15);

// Adsorption bed
AdsorptionBed bed = new AdsorptionBed("CO2 Adsorber", cooler.getOutletStream());
bed.setBedDiameter(1.5);
bed.setBedLength(4.0);
bed.setAdsorbentMaterial("Zeolite 13X");
bed.setIsothermType(IsothermType.LANGMUIR);
bed.setKLDF(0.05);

// Build and run process
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(comp);
process.add(cooler);
process.add(bed);
process.run();

// Results
Stream outlet = (Stream) bed.getOutletStream();
System.out.println("Outlet CO2: "
    + outlet.getFluid().getPhase(0).getComponent("CO2").getx());
System.out.println("Compressor power: " + comp.getPower("kW") + " kW");
System.out.println("Cooler duty: " + cooler.getDuty() + " W");
```

---

## Recipe 10: Capillary Condensation in Mesopores

Calculate condensation onset and condensate amounts in mesoporous silica.

```java
import neqsim.physicalproperties.interfaceproperties.solidadsorption.CapillaryCondensationModel;
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos system = new SystemSrkEos(298.15, 5.0);
system.addComponent("n-butane", 1.0);
system.setMixingRule("classic");
system.init(0);
system.init(1);

CapillaryCondensationModel ccModel = new CapillaryCondensationModel(system);
ccModel.setMeanPoreRadius(4.0);     // nm
ccModel.setPoreRadiusStdDev(1.5);   // nm
ccModel.setTotalPoreVolume(0.8);    // cm³/g
ccModel.setPoreType(CapillaryCondensationModel.PoreType.CYLINDRICAL);

ccModel.calcCapillaryCondensation(0);

System.out.println("Kelvin radius: " + ccModel.getKelvinRadius(0) + " nm");
System.out.println("Condensate amount: " + ccModel.getCondensateAmount(0) + " mol/g");
System.out.println("Saturation pressure: " + ccModel.getSaturationPressure(0) + " bar");
```

---

## Tips and Common Pitfalls

### 1. Always Set the Mixing Rule

Without a mixing rule, the thermodynamic system will not calculate fugacities correctly, and adsorption models will give wrong results.

```java
system.setMixingRule("classic"); // Required!
```

### 2. Temperature in Kelvin

NeqSim uses Kelvin internally. Always add 273.15 when specifying temperatures in Celsius.

```java
SystemSrkEos system = new SystemSrkEos(273.15 + 25.0, 10.0); // 25°C, 10 bar
```

### 3. Transient Mode Must Be Enabled

By default, `AdsorptionBed` runs in steady-state mode. For breakthrough curves, MTZ analysis, or PSA/TSA cycles, you must switch to transient:

```java
bed.setCalculateSteadyState(false);
```

### 4. Grid Resolution vs Speed

More cells give better accuracy but slower simulation. Start with 50 cells for design studies, increase to 100+ for final analysis.

### 5. kLDF Sensitivity

The LDF coefficient is the most critical tuning parameter. Too low and the front barely moves; too high and the front is sharp but may cause numerical oscillation. Start with 0.01–0.05 and calibrate against experimental breakthrough data.

### 6. Desorption Requires Pressure or Temperature Change

Simply setting `setDesorptionMode(true)` without a pressure or temperature change may not drive sufficient desorption. NeqSim defaults to atmospheric pressure (1.01325 bara) when no explicit desorption pressure is set, but for TSA you **must** specify the desorption temperature.

---

## Related Documentation

- [Adsorption Isotherm Models](../thermo/adsorption_isotherms.md) — Full mathematical reference
- [Adsorption Bed Process Equipment](../process/equipment/adsorption_bed.md) — Detailed engineering reference
- [Process Recipes](process-recipes.md) — Other process simulation recipes
- [Thermodynamics Recipes](thermodynamics-recipes.md) — Fluid and property recipes
