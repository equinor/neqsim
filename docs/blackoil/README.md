---
title: Black Oil Package
description: The `blackoil` package provides black oil model capabilities for reservoir engineering applications, including PVT table handling and flash calculations.
---

# Black Oil Package

The `blackoil` package provides black oil model capabilities for reservoir engineering applications, including PVT table handling and flash calculations.

## Table of Contents
- [Overview](#overview)
- [Package Structure](#package-structure)
- [Black Oil Model](#black-oil-model)
- [PVT Tables](#pvt-tables)
- [Flash Calculations](#flash-calculations)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.blackoil`

**Purpose:**
- Black oil PVT modeling
- Generate PVT tables from compositional models
- Black oil flash calculations
- Export to reservoir simulators

---

## Package Structure

```
blackoil/
├── BlackOilFlash.java           # Black oil flash calculator
├── BlackOilFlashResult.java     # Flash results container
├── BlackOilPVTTable.java        # PVT table storage
├── BlackOilConverter.java       # Compositional to black oil conversion
├── SystemBlackOil.java          # Black oil system representation
│
└── io/                          # I/O utilities
    └── BlackOilTableExporter.java
```

---

## Black Oil Model

### Theory

The black oil model describes reservoir fluids using three pseudo-components:
- **Oil** (stock tank oil)
- **Gas** (separator gas)
- **Water**

### Key Properties

| Property | Symbol | Description |
|----------|--------|-------------|
| Solution GOR | Rs | Gas dissolved in oil (Sm³/Sm³) |
| Oil FVF | Bo | Oil formation volume factor |
| Gas FVF | Bg | Gas formation volume factor |
| Water FVF | Bw | Water formation volume factor |
| Oil-in-Gas ratio | Rv | Oil vaporized in gas (Sm³/Sm³) |
| Oil viscosity | μo | Dynamic viscosity of oil |
| Gas viscosity | μg | Dynamic viscosity of gas |
| Water viscosity | μw | Dynamic viscosity of water |

### Correlations

#### Standing Correlation for Rs

$$R_s = \gamma_g \left( \frac{P}{18.2} \cdot 10^{0.0125 \cdot API - 0.00091 \cdot T} \right)^{1.2048}$$

#### Vasquez-Beggs for Bo

$$B_o = 1 + C_1 R_s + C_2 (T - 60) \left( \frac{API}{\gamma_{g,100}} \right) + C_3 R_s (T - 60) \left( \frac{API}{\gamma_{g,100}} \right)$$

---

## PVT Tables

### BlackOilPVTTable

The `BlackOilPVTTable` class stores PVT properties at multiple pressure points with linear interpolation.

```java
import neqsim.blackoil.BlackOilPVTTable;
import neqsim.blackoil.BlackOilPVTTable.Record;
import java.util.Arrays;
import java.util.List;

// Create PVT records at each pressure point
// Record(p, Rs, Bo, mu_o, Bg, mu_g, Rv, Bw, mu_w)
List<Record> records = Arrays.asList(
    new Record(50.0, 80.0, 1.25, 0.0015, 0.010, 0.000015, 0.0, 1.01, 0.001),
    new Record(100.0, 100.0, 1.30, 0.0012, 0.008, 0.000016, 0.0, 1.01, 0.001),
    new Record(150.0, 120.0, 1.35, 0.0010, 0.006, 0.000017, 0.0, 1.02, 0.001),
    new Record(200.0, 140.0, 1.40, 0.0009, 0.005, 0.000018, 0.0, 1.02, 0.001),
    new Record(250.0, 160.0, 1.45, 0.0008, 0.004, 0.000019, 0.0, 1.03, 0.001),
    new Record(300.0, 180.0, 1.50, 0.0007, 0.003, 0.000020, 0.0, 1.03, 0.001)
);

// Create PVT table with bubble point pressure
double bubblePointPressure = 250.0;  // bar
BlackOilPVTTable pvtTable = new BlackOilPVTTable(records, bubblePointPressure);
```

### Interpolation

```java
// Get properties at any pressure (linear interpolation)
double P = 175.0;  // bar
double Rs = pvtTable.Rs(P);      // Solution GOR
double Bo = pvtTable.Bo(P);      // Oil FVF
double Bg = pvtTable.Bg(P);      // Gas FVF
double muO = pvtTable.mu_o(P);   // Oil viscosity (Pa·s)
double muG = pvtTable.mu_g(P);   // Gas viscosity (Pa·s)

// Above bubble point, Rs stays constant
double RsEffective = pvtTable.RsEffective(P);
```

---

## BlackOilFlash

### Flash Calculator

```java
import neqsim.blackoil.BlackOilFlash;
import neqsim.blackoil.BlackOilFlashResult;

// Create flash calculator
double rho_o_sc = 850.0;   // Oil density at SC, kg/m³
double rho_g_sc = 0.85;    // Gas density at SC, kg/m³
double rho_w_sc = 1000.0;  // Water density at SC, kg/m³

BlackOilFlash flash = new BlackOilFlash(pvtTable, rho_o_sc, rho_g_sc, rho_w_sc);

// Perform flash at reservoir conditions
double P = 200.0;    // bar
double T = 373.15;   // K (not used in simple model)
double Otot_std = 1000.0;  // Stock tank oil, Sm³
double Gtot_std = 150000.0; // Total gas, Sm³
double W_std = 500.0;      // Water, Sm³

BlackOilFlashResult result = flash.flash(P, T, Otot_std, Gtot_std, W_std);
```

### Flash Results

```java
// Phase volumes at reservoir conditions
double V_oil = result.V_o;   // Oil volume, m³
double V_gas = result.V_g;   // Gas volume, m³
double V_water = result.V_w; // Water volume, m³

// Phase properties
double rho_oil = result.rho_o;   // Oil density, kg/m³
double rho_gas = result.rho_g;   // Gas density, kg/m³
double rho_water = result.rho_w; // Water density, kg/m³

double mu_oil = result.mu_o;     // Oil viscosity, cP
double mu_gas = result.mu_g;     // Gas viscosity, cP
double mu_water = result.mu_w;   // Water viscosity, cP

// PVT properties used
double Rs = result.Rs;   // Solution GOR
double Bo = result.Bo;   // Oil FVF
double Bg = result.Bg;   // Gas FVF
double Bw = result.Bw;   // Water FVF
```

---

## Compositional to Black Oil Conversion

### BlackOilConverter

Generate black oil tables from compositional EoS model.

```java
import neqsim.blackoil.BlackOilConverter;

// Create compositional oil
SystemInterface oil = new SystemPrEos(373.15, 300.0);
oil.addComponent("nitrogen", 0.5);
oil.addComponent("CO2", 2.0);
oil.addComponent("methane", 35.0);
oil.addComponent("ethane", 8.0);
oil.addComponent("propane", 5.0);
oil.addComponent("n-butane", 3.0);
oil.addComponent("n-pentane", 2.5);
oil.addComponent("n-hexane", 3.0);
oil.addTBPfraction("C7+", 41.0, 220.0/1000.0, 0.85);
oil.setMixingRule("classic");
oil.useVolumeCorrection(true);

// Convert to black oil
BlackOilConverter converter = new BlackOilConverter(oil);
converter.setTemperature(373.15, "K");

// Define separator conditions
converter.setSeparatorTemperature(288.15, "K");
converter.setSeparatorPressure(1.01325, "bara");

// Generate table
double[] pressures = {1, 50, 100, 150, 200, 250, 300};
converter.setPressures(pressures, "bara");
converter.run();

// Get black oil table
BlackOilPVTTable boTable = converter.getBlackOilTable();
```

---

## Export to Simulators

### Eclipse Format

```java
import neqsim.blackoil.io.BlackOilTableExporter;

BlackOilTableExporter exporter = new BlackOilTableExporter(boTable);
exporter.setFormat("ECLIPSE");
exporter.exportToFile("PVTO.inc");
```

### Example PVTO Output

```
PVTO
-- Rs      P       Bo      viscosity
   50.0    50.0   1.250    1.50
          100.0   1.248    1.55
          150.0   1.246    1.60 /
  100.0   100.0   1.350    1.20
          150.0   1.347    1.25
          200.0   1.345    1.30 /
/
```

---

## Complete Example

```java
import neqsim.blackoil.*;
import neqsim.pvtsimulation.simulation.*;

// Step 1: Create compositional model
SystemInterface oil = new SystemPrEos(373.15, 250.0);
oil.addComponent("nitrogen", 0.3);
oil.addComponent("CO2", 1.5);
oil.addComponent("methane", 40.0);
oil.addComponent("ethane", 7.0);
oil.addComponent("propane", 4.5);
oil.addComponent("i-butane", 1.0);
oil.addComponent("n-butane", 2.5);
oil.addComponent("i-pentane", 1.2);
oil.addComponent("n-pentane", 1.8);
oil.addComponent("n-hexane", 2.5);
oil.addTBPfraction("C7-C10", 15.0, 120.0/1000.0, 0.78);
oil.addTBPfraction("C11-C15", 10.0, 180.0/1000.0, 0.82);
oil.addTBPfraction("C16+", 12.7, 350.0/1000.0, 0.90);
oil.setMixingRule("classic");
oil.useVolumeCorrection(true);

// Step 2: Run differential liberation
DifferentialLiberation dl = new DifferentialLiberation(oil);
dl.setTemperature(373.15, "K");
double[] pressures = {250, 200, 150, 100, 75, 50, 25, 1.01325};
dl.setPressures(pressures, "bara");
dl.run();

// Step 3: Create black oil table
BlackOilPVTTable boTable = new BlackOilPVTTable();
boTable.setPressures(pressures);
boTable.setRs(dl.getRs());
boTable.setBo(dl.getBo());
boTable.setMuO(dl.getOilViscosity());

// Add gas properties
boTable.setBg(dl.getBg());
boTable.setMuG(dl.getGasViscosity());

// Step 4: Separator test for stock tank conditions
SeparatorTest sep = new SeparatorTest(oil.clone());
sep.setSeparatorConditions(
    new double[]{323.15, 288.15},
    new double[]{30.0, 1.01325}
);
sep.run();

double rho_o_sc = sep.getOilDensity();

// Step 5: Create flash calculator
BlackOilFlash boFlash = new BlackOilFlash(boTable, rho_o_sc, 0.85, 1000.0);

// Step 6: Calculate at different conditions
System.out.println("P (bar)\tRs\tBo\tρ_oil\tμ_oil");
for (double P : new double[]{50, 100, 150, 200}) {
    BlackOilFlashResult r = boFlash.flash(P, 373.15, 1000.0, 150000.0, 0.0);
    System.out.printf("%.0f\t%.1f\t%.4f\t%.1f\t%.3f%n",
        P, r.Rs, r.Bo, r.rho_o, r.mu_o);
}
```

---

## Best Practices

1. **Validate against compositional** - Compare black oil results with full EoS
2. **Use appropriate correlations** - Match fluid type (light, medium, heavy oil)
3. **Check consistency** - Ensure Rs and Bo are consistent at bubble point
4. **Include undersaturated region** - Extend table above bubble point
5. **Document separator conditions** - Record conditions used for conversion

---

## Limitations

- Temperature dependence not fully captured
- Compositional grading not modeled
- Gas condensate requires extended model (Rv)
- Near-critical fluids may need compositional treatment

---

## Related Documentation

- [PVT Simulation](../pvtsimulation/README.md) - Laboratory experiments
- [PVT Workflow](../pvtsimulation/pvt_workflow.md) - End-to-end workflow
- [Black Oil PVT Export](../pvtsimulation/blackoil_pvt_export.md) - Export details
