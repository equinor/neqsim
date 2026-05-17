---
title: PVT Lab Test Simulations
description: Guide to simulating standard PVT laboratory experiments in NeqSim including CCE, CVD, differential liberation, separator tests, swelling tests, and viscosity measurements.
---

# PVT Lab Test Simulations

This guide demonstrates how to simulate standard PVT laboratory experiments using NeqSim. These simulations are essential for validating EoS parameters against lab data and characterizing reservoir fluids.

## Overview

| Test | Abbreviation | Fluid Type | Purpose |
|------|--------------|------------|---------|
| Constant Composition Expansion | CCE | Oil, Gas, Condensate | Saturation pressure, compressibility |
| Constant Volume Depletion | CVD | Gas Condensate | Liquid dropout, gas composition |
| Differential Liberation | DL | Black Oil | Bo, Rs, GOR vs pressure |
| Separator Test | SEP | All | GOR, FVF at separator conditions |
| Swelling Test | SWT | Oil + Gas | MMP, oil swelling with gas injection |
| Viscosity vs Pressure | VIS | All | μo, μg behavior |

---

## Constant Composition Expansion (CCE)

CCE measures fluid behavior above and below the saturation pressure at constant temperature.

### What CCE Measures

- Bubble point or dew point pressure
- Relative volume vs pressure
- Liquid compressibility above Pb
- Two-phase compressibility below Pb

### Java Implementation

```java
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.thermo.system.SystemSrkEos;

// Create reservoir fluid
SystemSrkEos fluid = new SystemSrkEos(373.15, 300.0);  // Reservoir T, high P
fluid.addComponent("nitrogen", 0.005);
fluid.addComponent("CO2", 0.015);
fluid.addComponent("methane", 0.45);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("i-butane", 0.02);
fluid.addComponent("n-butane", 0.03);
fluid.addComponent("i-pentane", 0.015);
fluid.addComponent("n-pentane", 0.02);
fluid.addComponent("n-hexane", 0.025);
fluid.addTBPfraction("C7", 0.05, 95.0, 0.72);
fluid.addTBPfraction("C10", 0.08, 135.0, 0.78);
fluid.addTBPfraction("C15", 0.06, 200.0, 0.82);
fluid.addTBPfraction("C20+", 0.10, 350.0, 0.88);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

// Set up CCE test
ConstantMassExpansion cce = new ConstantMassExpansion(fluid);
cce.setTemperature(100.0, "C");  // Test temperature

// Define pressure steps
double[] pressures = {400, 350, 300, 280, 260, 240, 220, 200, 180, 160, 140, 120, 100};
cce.setPressures(pressures, "bara");

// Run CCE
cce.runCalc();

// Get results
System.out.println("=== CCE Results at 100°C ===");
System.out.println("Pressure (bara) | Rel. Volume | Liquid Vol%");
System.out.println("----------------|-------------|------------");

double[] relVol = cce.getRelativeVolume();
double[] liquidVol = cce.getLiquidVolume();
double saturationP = cce.getSaturationPressure();

for (int i = 0; i < pressures.length; i++) {
    System.out.printf("%15.1f | %11.4f | %10.2f%n", 
        pressures[i], relVol[i], liquidVol[i] * 100);
}

System.out.printf("%nSaturation Pressure: %.1f bara%n", saturationP);
```

### Python Implementation

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ConstantMassExpansion = jneqsim.pvtsimulation.simulation.ConstantMassExpansion

# Create fluid
fluid = SystemSrkEos(273.15 + 100.0, 300.0)
fluid.addComponent("methane", 0.50)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.08)
fluid.addComponent("n-butane", 0.05)
fluid.addComponent("n-pentane", 0.04)
fluid.addTBPfraction("C7+", 0.23, 150.0, 0.80)
fluid.setMixingRule("classic")

# Run CCE
cce = ConstantMassExpansion(fluid)
cce.setTemperature(100.0, "C")

pressures = [350.0, 300.0, 250.0, 200.0, 180.0, 160.0, 140.0, 120.0, 100.0]
cce.setPressures(pressures, "bara")
cce.runCalc()

print(f"Saturation pressure: {cce.getSaturationPressure():.1f} bara")
```

---

## Constant Volume Depletion (CVD)

CVD simulates gas condensate reservoir depletion by removing gas at constant volume.

### What CVD Measures

- Liquid dropout curve (retrograde condensation)
- Gas production rate vs pressure
- Produced gas composition changes
- Cumulative recovery

### Java Implementation

```java
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.thermo.system.SystemSrkEos;

// Rich gas condensate
SystemSrkEos fluid = new SystemSrkEos(373.15, 400.0);
fluid.addComponent("nitrogen", 0.02);
fluid.addComponent("CO2", 0.03);
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("i-butane", 0.015);
fluid.addComponent("n-butane", 0.025);
fluid.addComponent("i-pentane", 0.01);
fluid.addComponent("n-pentane", 0.015);
fluid.addTBPfraction("C7+", 0.055, 120.0, 0.76);
fluid.setMixingRule("classic");

// CVD test
ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid);
cvd.setTemperature(100.0, "C");

double[] pressures = {400, 350, 300, 250, 200, 150, 100, 50};
cvd.setPressures(pressures, "bara");

cvd.runCalc();

// Results
System.out.println("=== CVD Results ===");
System.out.println("Pressure | Liquid Dropout | Z-factor | Gas Produced");
System.out.println("(bara)   | (vol%)         |          | (mol%)");
System.out.println("---------------------------------------------------------");

double[] liquidDropout = cvd.getLiquidVolume();  // Liquid volume %
double[] zFactor = cvd.getZfactor();
double[] gasProduced = cvd.getCumulativeGasProduced();

for (int i = 0; i < pressures.length; i++) {
    System.out.printf("%8.0f | %14.2f | %8.4f | %11.2f%n",
        pressures[i], liquidDropout[i], zFactor[i], gasProduced[i] * 100);
}

System.out.printf("%nDew Point Pressure: %.1f bara%n", cvd.getDewPointPressure());
System.out.printf("Maximum Liquid Dropout: %.2f%% at %.0f bara%n",
    cvd.getMaxLiquidVolume(), cvd.getPressureAtMaxLiquid());
```

---

## Differential Liberation (DL)

DL simulates oil reservoir depletion by removing all gas at each pressure step.

### What DL Measures

- Oil formation volume factor (Bo)
- Solution gas-oil ratio (Rs)
- Gas formation volume factor (Bg)
- Oil and gas density vs pressure

### Java Implementation

```java
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.thermo.system.SystemSrkEos;

// Black oil
SystemSrkEos fluid = new SystemSrkEos(373.15, 300.0);
fluid.addComponent("nitrogen", 0.002);
fluid.addComponent("CO2", 0.008);
fluid.addComponent("methane", 0.30);
fluid.addComponent("ethane", 0.06);
fluid.addComponent("propane", 0.04);
fluid.addComponent("i-butane", 0.015);
fluid.addComponent("n-butane", 0.025);
fluid.addComponent("i-pentane", 0.01);
fluid.addComponent("n-pentane", 0.015);
fluid.addComponent("n-hexane", 0.02);
fluid.addTBPfraction("C7", 0.08, 100.0, 0.74);
fluid.addTBPfraction("C10", 0.10, 140.0, 0.78);
fluid.addTBPfraction("C15", 0.10, 210.0, 0.83);
fluid.addTBPfraction("C25+", 0.22, 400.0, 0.90);
fluid.setMixingRule("classic");

// DL test
DifferentialLiberation dl = new DifferentialLiberation(fluid);
dl.setTemperature(100.0, "C");

double[] pressures = {300, 250, 200, 180, 160, 140, 120, 100, 80, 60, 40, 20, 1};
dl.setPressures(pressures, "bara");

dl.runCalc();

// Results
System.out.println("=== Differential Liberation Results at 100°C ===");
System.out.println("Pressure | Bo      | Rs        | Bg        | Oil ρ    | Gas ρ");
System.out.println("(bara)   | (m3/m3) | (Sm3/Sm3) | (m3/Sm3)  | (kg/m3)  | (kg/m3)");
System.out.println("--------------------------------------------------------------");

double[] Bo = dl.getBo();
double[] Rs = dl.getRs();
double[] Bg = dl.getBg();
double[] oilDensity = dl.getOilDensity("kg/m3");
double[] gasDensity = dl.getGasDensity("kg/m3");

for (int i = 0; i < pressures.length; i++) {
    System.out.printf("%8.0f | %7.4f | %9.1f | %9.5f | %8.1f | %7.2f%n",
        pressures[i], Bo[i], Rs[i], Bg[i], oilDensity[i], gasDensity[i]);
}

System.out.printf("%nBubble Point: %.1f bara%n", dl.getBubblePointPressure());
System.out.printf("Bo at Pb: %.4f m3/m3%n", dl.getBoAtBubblePoint());
System.out.printf("Rs at Pb: %.1f Sm3/Sm3%n", dl.getRsAtBubblePoint());
```

---

## Separator Test

Separator tests simulate field separation to determine GOR and FVF at specific conditions.

### Java Implementation

```java
import neqsim.pvtsimulation.simulation.SeparatorTest;
import neqsim.thermo.system.SystemSrkEos;

// Reservoir fluid at bubble point
SystemSrkEos fluid = new SystemSrkEos(373.15, 250.0);
fluid.addComponent("methane", 0.40);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.06);
fluid.addComponent("n-butane", 0.04);
fluid.addComponent("n-pentane", 0.03);
fluid.addComponent("n-hexane", 0.03);
fluid.addTBPfraction("C7+", 0.36, 180.0, 0.82);
fluid.setMixingRule("classic");

// 2-stage separator test
SeparatorTest sepTest = new SeparatorTest(fluid);

// Stage 1: HP separator
sepTest.setSeparatorTemperature(0, 60.0, "C");
sepTest.setSeparatorPressure(0, 40.0, "bara");

// Stage 2: LP separator (stock tank)
sepTest.setSeparatorTemperature(1, 15.0, "C");
sepTest.setSeparatorPressure(1, 1.01325, "bara");

sepTest.runCalc();

// Results
System.out.println("=== Separator Test Results ===");
System.out.printf("Stage 1: P=40 bara, T=60°C%n");
System.out.printf("  GOR: %.0f Sm3/Sm3%n", sepTest.getGOR(0));

System.out.printf("Stage 2: P=1.01 bara, T=15°C%n");
System.out.printf("  GOR: %.0f Sm3/Sm3%n", sepTest.getGOR(1));

System.out.printf("%nTotal GOR: %.0f Sm3/Sm3%n", sepTest.getTotalGOR());
System.out.printf("Oil FVF (Bo): %.4f Rm3/Sm3%n", sepTest.getBo());
System.out.printf("Stock tank oil density: %.1f kg/m3%n", sepTest.getOilDensity("kg/m3"));
System.out.printf("Stock tank API gravity: %.1f°%n", sepTest.getAPIgravity());
```

---

## Swelling Test

Swelling tests measure oil volume change when gas is injected (used for EOR evaluation).

### Java Implementation

```java
import neqsim.pvtsimulation.simulation.SwellingTest;
import neqsim.thermo.system.SystemPrEos;

// Reservoir oil
SystemPrEos oil = new SystemPrEos(373.15, 200.0);
oil.addComponent("methane", 0.25);
oil.addComponent("ethane", 0.05);
oil.addComponent("propane", 0.04);
oil.addComponent("n-butane", 0.03);
oil.addComponent("n-pentane", 0.03);
oil.addTBPfraction("C7+", 0.60, 220.0, 0.84);
oil.setMixingRule("classic");

// Injection gas (CO2 for EOR)
SystemPrEos gas = new SystemPrEos(373.15, 200.0);
gas.addComponent("CO2", 1.0);
gas.setMixingRule("classic");

// Swelling test
SwellingTest swelling = new SwellingTest(oil);
swelling.setInjectionGas(gas);
swelling.setTemperature(100.0, "C");

// Injection amounts (mol% of original oil)
double[] injectionMol = {0, 5, 10, 15, 20, 25, 30, 35, 40};
swelling.setInjectedMoles(injectionMol);

swelling.runCalc();

// Results
System.out.println("=== Swelling Test (CO2 Injection) ===");
System.out.println("CO2 Added | Swelling Factor | Sat. Pressure");
System.out.println("(mol%)    | (vol/vol)       | (bara)");
System.out.println("------------------------------------------");

double[] swellingFactor = swelling.getSwellingFactor();
double[] satPressure = swelling.getSaturationPressure();

for (int i = 0; i < injectionMol.length; i++) {
    System.out.printf("%9.0f | %15.4f | %12.1f%n",
        injectionMol[i], swellingFactor[i], satPressure[i]);
}

System.out.printf("%nMMP (est): %.1f bara%n", swelling.getMMP());
```

---

## Viscosity Measurements

### Dead Oil Viscosity vs Temperature

```java
import neqsim.pvtsimulation.simulation.ViscositySimulation;
import neqsim.thermo.system.SystemSrkEos;

// Dead oil (no dissolved gas)
SystemSrkEos deadOil = new SystemSrkEos(298.15, 1.0);
deadOil.addComponent("n-hexane", 0.05);
deadOil.addTBPfraction("C7", 0.10, 100.0, 0.74);
deadOil.addTBPfraction("C10", 0.20, 140.0, 0.78);
deadOil.addTBPfraction("C15", 0.25, 210.0, 0.83);
deadOil.addTBPfraction("C25+", 0.40, 400.0, 0.90);
deadOil.setMixingRule("classic");

// Viscosity vs temperature
ViscositySimulation visSim = new ViscositySimulation(deadOil);
visSim.setPressure(1.0, "bara");

double[] temperatures = {20, 40, 60, 80, 100, 120, 140};
visSim.setTemperatures(temperatures, "C");

visSim.runCalc();

System.out.println("=== Dead Oil Viscosity vs Temperature ===");
System.out.println("Temperature | Viscosity");
System.out.println("(°C)        | (cP)");
System.out.println("-------------------------");

double[] viscosity = visSim.getViscosity("cP");
for (int i = 0; i < temperatures.length; i++) {
    System.out.printf("%11.0f | %9.2f%n", temperatures[i], viscosity[i]);
}
```

### Live Oil Viscosity vs Pressure

```java
// Live oil viscosity below bubble point
ViscositySimulation visSim = new ViscositySimulation(liveOil);
visSim.setTemperature(100.0, "C");

double[] pressures = {250, 200, 180, 160, 140, 120, 100, 80, 60, 40, 20};
visSim.setPressures(pressures, "bara");

visSim.runCalc();

System.out.println("=== Live Oil Viscosity vs Pressure at 100°C ===");
System.out.println("Pressure | Oil μ   | Gas μ");
System.out.println("(bara)   | (cP)    | (cP)");
System.out.println("----------------------------");

double[] oilVis = visSim.getOilViscosity("cP");
double[] gasVis = visSim.getGasViscosity("cP");

for (int i = 0; i < pressures.length; i++) {
    System.out.printf("%8.0f | %7.3f | %6.4f%n", pressures[i], oilVis[i], gasVis[i]);
}
```

---

## Tuning EoS to Lab Data

When simulation results don't match lab data:

### Volume Translation

```java
// Adjust volume translation to match liquid density
fluid.getComponent("C7+").setVolumeCorrectionT(0.02);  // Peneloux correction
```

### Binary Interaction Parameters

```java
// Adjust kij for CO2-hydrocarbon interactions
fluid.setMixingRule(2);  // Classic with tuned kij
fluid.getPhase(0).setKij(
    fluid.getComponent("CO2").getComponentNumber(),
    fluid.getComponent("methane").getComponentNumber(),
    0.12  // Tuned kij value
);
```

### Plus Fraction Characterization

```java
// Split C7+ into more pseudo-components
CharacterisationTBP char = new CharacterisationTBP(fluid);
char.setNumberOfPseudocomponents(10);
char.characterisePlusFraction();
```

---

## See Also

- [Fluid Characterization](../pvtsimulation/fluid_characterization) - Plus fraction characterization
- [Thermodynamic Models](../thermo/thermodynamic_models) - EoS selection
- [Phase Envelope Guide](../pvtsimulation/phase_envelope_guide) - PT diagrams
