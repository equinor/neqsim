---
title: "Process Simulation Enhancements: Air Cooler, Packed Column, Column Internals, and More"
description: "Comprehensive guide to new NeqSim features: air-cooled heat exchangers with API 661 thermal design, packed columns for amine/TEG contactors, column internals sizing (tray and packing hydraulics), shortcut distillation, PVF flash, and stream summary tables."
---

# Process Simulation Enhancements

This document covers seven new capabilities added to NeqSim for process simulation
and equipment design. Each section includes API reference, usage examples, and
verification notes.

## Table of Contents

1. [Air-Cooled Heat Exchanger (AirCooler)](#1-air-cooled-heat-exchanger)
2. [Packed Column (Absorber/Contactor)](#2-packed-column)
3. [Tray Hydraulics Calculator](#3-tray-hydraulics)
4. [Packing Hydraulics Calculator](#4-packing-hydraulics)
5. [Column Internals Designer](#5-column-internals-designer)
6. [Shortcut Distillation Column](#6-shortcut-distillation)
7. [PVF Flash and Stream Summary](#7-pvf-flash-and-stream-summary)

---

## 1. Air-Cooled Heat Exchanger

**Class:** `neqsim.process.equipment.heatexchanger.AirCooler`

A full thermal-design model for air-cooled heat exchangers (fin-fan coolers),
following the API 661 framework.

### Features

- **Humid-air energy balance** using `HumidAir` utility (psychrometric properties)
- **Briggs-Young** fin-tube correlation for air-side heat transfer coefficient
- **Schmidt** annular fin efficiency method
- **Robinson-Briggs** air-side pressure drop for finned tube banks
- **LMTD** with F-correction factor for cross-flow arrangement
- **Fan model** with cubic polynomial fan curve (dP vs Q)
- **Ambient temperature correction** based on ITD ratio (actual vs design)
- **Bundle sizing** with tubes-per-row, total tubes, face area, fin area
- **Comprehensive JSON report** with all thermal, hydraulic, and geometry data

### Correlations

| Correlation | Purpose | Reference |
|-------------|---------|-----------|
| Briggs-Young | Air-side HTC for finned tubes | Chem. Eng. Prog. Symp. Ser. 59(41), 1963 |
| Schmidt | Annular fin efficiency | VDI Heat Atlas method |
| Robinson-Briggs | Air-side pressure drop | Empirical for staggered finned banks |
| LMTD cross-flow | Mean temperature difference | TEMA standards |

### Java Example

```java
import neqsim.process.equipment.heatexchanger.AirCooler;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create hot gas stream
SystemInterface gas = new SystemSrkEos(273.15 + 80.0, 10.0);
gas.addComponent("methane", 1.0);
gas.setMixingRule("classic");

Stream hotGas = new Stream("hot gas", gas);
hotGas.setFlowRate(1.0, "MSm3/day");
hotGas.setTemperature(80.0, "C");
hotGas.setPressure(10.0, "bara");

// Configure air cooler
AirCooler ac = new AirCooler("AC-100", hotGas);
ac.setOutletTemperature(40.0, "C");
ac.setAirInletTemperature(25.0, "C");
ac.setAirOutletTemperature(35.0, "C");
ac.setRelativeHumidity(0.6);

// Bundle geometry
ac.setNumberOfTubeRows(4);
ac.setTubeLength(12.0);
ac.setBayWidth(3.05);
ac.setNumberOfBays(1);
ac.setNumberOfFansPerBay(2);
ac.setFanDiameter(4.27);
ac.setFinPitch(0.0025);       // 2.5 mm
ac.setFinHeight(0.015875);    // 5/8 inch
ac.setFinThickness(0.0004);   // 0.4 mm aluminium

// Optional: fan curve
ac.setFanCurve(350.0, 0.0, -0.003, 0.0);

// Optional: ambient correction
ac.setDesignAmbientTemperature(25.0, "C");

ProcessSystem process = new ProcessSystem();
process.add(hotGas);
process.add(ac);
process.run();

// Results
System.out.println("Air mass flow:    " + ac.getAirMassFlow() + " kg/s");
System.out.println("Air volume flow:  " + ac.getAirVolumeFlow() + " m3/s");
System.out.println("LMTD:             " + ac.getLMTD() + " K");
System.out.println("Overall U:        " + ac.getOverallU() + " W/m2-K");
System.out.println("Required area:    " + ac.getRequiredArea() + " m2");
System.out.println("Air-side HTC:     " + ac.getAirSideHTC() + " W/m2-K");
System.out.println("Fin efficiency:   " + ac.getFinEfficiency());
System.out.println("Face velocity:    " + ac.getFaceVelocity() + " m/s");
System.out.println("Air-side DP:      " + ac.getAirSidePressureDrop() + " Pa");
System.out.println("Fan power:        " + ac.getFanPower("kW") + " kW");
System.out.println("ITD:              " + ac.getITD() + " K");
System.out.println("Ambient factor:   " + ac.getAmbientCorrectionFactor());
System.out.println("Total tubes:      " + ac.getTotalTubes());
System.out.println("Face area:        " + ac.getFaceArea() + " m2");
System.out.println("JSON report:\n" + ac.toJson());
```

### Python (Jupyter) Example

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
AirCooler = jneqsim.process.equipment.heatexchanger.AirCooler
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

gas = SystemSrkEos(273.15 + 80.0, 10.0)
gas.addComponent("methane", 1.0)
gas.setMixingRule("classic")

hot_gas = Stream("hot gas", gas)
hot_gas.setFlowRate(1.0, "MSm3/day")
hot_gas.setTemperature(80.0, "C")
hot_gas.setPressure(10.0, "bara")

ac = AirCooler("AC-100", hot_gas)
ac.setOutletTemperature(40.0, "C")
ac.setAirInletTemperature(25.0, "C")
ac.setAirOutletTemperature(35.0, "C")
ac.setRelativeHumidity(0.6)
ac.setNumberOfTubeRows(4)

process = ProcessSystem()
process.add(hot_gas)
process.add(ac)
process.run()

print(f"Fan power:  {ac.getFanPower('kW'):.1f} kW")
print(f"LMTD:      {ac.getLMTD():.1f} K")
print(f"U:         {ac.getOverallU():.1f} W/m2-K")
print(f"Area:      {ac.getRequiredArea():.1f} m2")
```

---

## 2. Packed Column

**Classes:** `neqsim.process.equipment.distillation.PackedColumn`, `neqsim.process.equipment.distillation.RateBasedPackedColumn`

`PackedColumn` wraps `DistillationColumn` and adds HETP-based packing hydraulics.
`RateBasedPackedColumn` is the non-equilibrium absorber/stripper model for
counter-current segment calculations with bidirectional mass transfer.

### Features

- **HETP-based staging** from packed bed height
- **Packing hydraulics** (flooding, pressure drop, mass transfer coefficients)
- **Registry-backed packing data** (Pall Ring, Raschig, IMTP, Mellapak, Flexipac, etc.)
- **Absorber/contactor mode** (no condenser/reboiler) for amine/TEG service
- **Rate-based non-equilibrium mode** with segment profiles, bidirectional transfer, and interface equilibrium
- **Maxwell-Stefan matrix film option** using NeqSim binary diffusivities and phase composition
- **Explicit interphase heat transfer** using Chilton-Colburn heat and mass transfer analogy
- **Optional simultaneous segment solver** with Maxwell-Stefan flux residuals, interface-temperature residuals, and PH-flash enthalpy targets
- **TEG dehydration validation** with water-removal checks against typical circulation ratios
- **Distillation mode** with condenser + reboiler for packed distillation
- **Auto-sizing** of column diameter from flood fraction
- **JSON report** with complete packing configuration and hydraulic results

### Packing Data Available

Packing data comes from `PackingSpecificationLibrary`, which combines built-in entries with
`designdata/Packing.csv`. Names are resolved with forgiving aliases such as `pall ring 50`,
`Mellapak250Y`, and `IMTP 70`.

| Random | Structured |
|--------|------------|
| Pall-Ring-25, Pall-Ring-38, Pall-Ring-50 | Mellapak-125Y, Mellapak-250Y, Mellapak-350Y, Mellapak-500Y |
| Raschig-Ring-25, Raschig-Ring-50 | Flexipac-1Y, Flexipac-2Y, Flexipac-3Y |
| IMTP-25, IMTP-40, IMTP-50, IMTP-70 | Sulzer-BX, Sulzer-CY |
| Berl-Saddle-25, Berl-Saddle-38, Berl-Saddle-50 | |
| Intalox-Saddle-25 | |

### Java Example (TEG Contactor)

```java
import neqsim.process.equipment.distillation.PackedColumn;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Wet gas feed
SystemInterface wetGas = new SystemSrkEos(273.15 + 30.0, 70.0);
wetGas.addComponent("methane", 0.85);
wetGas.addComponent("ethane", 0.08);
wetGas.addComponent("propane", 0.04);
wetGas.addComponent("water", 0.03);
wetGas.setMixingRule("classic");

Stream gasIn = new Stream("wet gas", wetGas);
gasIn.setFlowRate(50000.0, "kg/hr");
gasIn.setTemperature(30.0, "C");
gasIn.setPressure(70.0, "bara");

// Lean TEG
SystemInterface tegSys = new SystemSrkEos(273.15 + 40.0, 70.0);
tegSys.addComponent("TEG", 0.98);
tegSys.addComponent("water", 0.02);
tegSys.setMixingRule("classic");

Stream tegIn = new Stream("lean TEG", tegSys);
tegIn.setFlowRate(3000.0, "kg/hr");
tegIn.setTemperature(40.0, "C");
tegIn.setPressure(70.0, "bara");

// Packed contactor
PackedColumn contactor = new PackedColumn("TEG Contactor", gasIn);
contactor.setPackedHeight(6.0);                 // 6 m packed bed
contactor.setPackingType("Mellapak-250Y");      // Structured packing
contactor.setStructuredPacking(true);
contactor.setDesignFloodFraction(0.70);         // 70% of flood
contactor.addSolventStream(tegIn);              // Lean solvent at top

ProcessSystem process = new ProcessSystem();
process.add(gasIn);
process.add(tegIn);
process.add(contactor);
process.run();

// Results
System.out.println("HETP:           " + contactor.getHETP() + " m");
System.out.println("NTS:            " + contactor.getTheoreticalStages());
System.out.println("% Flood:        " + contactor.getPercentFlood() + " %");
System.out.println("Packing DP:     " + contactor.getPackingPressureDrop("mbar") + " mbar");
System.out.println("Hydraulics OK:  " + contactor.isHydraulicsOk());
System.out.println("JSON:\n" + contactor.toJson());
```

### Rate-Based Packed Column Details

Use `RateBasedPackedColumn` when the design question depends on film-rate limitations, local absorption/stripping direction, heat effects, or packed-bed hydraulic diagnostics. It solves a counter-current packed section as axial segments, using flash calculations for interface equilibrium, `PackingHydraulicsCalculator` for wetted area, pressure drop, flooding fraction, `kGa`, and `kLa`, and NeqSim physical-property diffusivities for the default Maxwell-Stefan matrix film correction.

The default mass-transfer path is `FilmModel.MAXWELL_STEFAN_MATRIX`. This assembles a multicomponent film resistance matrix from binary diffusion coefficients and phase mole fractions, following the same Krishna-Standart/Maxwell-Stefan structure used by the fluid-mechanics non-equilibrium boundary models. The simpler `OVERALL_TWO_RESISTANCE` model remains available for screening and backward comparisons.

The default heat-transfer path is `HeatTransferModel.CHILTON_COLBURN_ANALOGY`. It derives gas- and liquid-side heat-transfer coefficients from the packed-bed mass-transfer coefficients, phase heat capacities, viscosities, diffusivities, and thermal conductivities, then applies explicit gas-liquid heat exchange in every segment before re-flashing the outlet states.

The robust default segment solver is `SegmentSolver.SEQUENTIAL_EXPLICIT`. Advanced users can enable `SegmentSolver.SIMULTANEOUS_RESIDUAL` to solve the component transfer rates and interface temperature in one damped residual system. That mode evaluates Maxwell-Stefan flux residuals, computes interface equilibrium and component molar enthalpies at the trial interface temperature, applies PH-flash enthalpy targets after the material transfer, and records residual diagnostics in each `SegmentResult`. Trial interface flashes and PH flashes are guarded with bounded fallback states so thermodynamic failures do not abort the counter-current column profile.

For TEG dehydration, the recommended setup is a CPA glycol/water system, water-only transfer via `setTransferComponents("water")`, and structured packing such as `Mellapak-250Y` for compact contactors. In addition to outlet water content, check the circulation efficiency:

$$
R_{TEG} = \frac{\dot{m}_{TEG} / \rho_{TEG}}{\dot{m}_{H2O,removed}}
$$

Typical TEG absorber practice is about 15-40 L TEG/kg H2O removed, equivalent to roughly 0.02-0.07 kg water removed per kg TEG circulated for lean TEG density near 1.125 kg/L. The focused `RateBasedPackedColumnTest` suite includes this check along with absorption, stripping, interface equilibrium output, explicit heat-transfer output, simultaneous residual diagnostics, height sensitivity, zero-height transfer, material balance, stream introspection, and JSON reporting tests.

More detail is available in the [absorbers and strippers guide](equipment/absorbers.md#rate-based-packed-column) and the [TEG dehydration tutorial](../tutorials/teg_dehydration_tutorial.md).

---

## 3. Tray Hydraulics

**Class:** `neqsim.process.equipment.distillation.internals.TrayHydraulicsCalculator`

Per-tray hydraulic design for sieve, valve, or bubble-cap trays.

### Correlations

| Check | Correlation | Criterion |
|-------|-------------|-----------|
| Flooding | Fair (1961) | Percent flood < design limit |
| Weeping | Sinnott (2005) | Hole velocity > minimum |
| Entrainment | Fair (1961) | Fractional entrainment < 0.1 |
| Downcomer backup | Francis weir | Backup < 50% tray spacing |
| Tray pressure drop | Dry + liquid head | Per-tray DP in Pa |
| Efficiency | O'Connell (1946) | Murphree efficiency |

### Java Example

```java
import neqsim.process.equipment.distillation.internals.TrayHydraulicsCalculator;

TrayHydraulicsCalculator tray = new TrayHydraulicsCalculator();
tray.setTrayType("sieve");
tray.setTraySpacing(0.6);          // m
tray.setWeirHeight(0.05);          // m
tray.setHoleDiameter(0.005);       // m
tray.setOpenAreaFraction(0.10);    // 10% open area
tray.setDowncomerAreaFraction(0.12);

// Set fluid properties for a specific tray
tray.setVaporDensity(5.0);         // kg/m3
tray.setLiquidDensity(600.0);      // kg/m3
tray.setVaporViscosity(1.2e-5);    // Pa.s
tray.setLiquidViscosity(3.0e-4);   // Pa.s
tray.setSurfaceTension(0.020);     // N/m
tray.setVaporMassFlow(2.0);        // kg/s
tray.setLiquidMassFlow(5.0);       // kg/s
tray.setRelativeVolatility(2.5);
tray.setLiquidMolarMass(0.060);    // kg/mol

tray.calculate();

System.out.println("Flooding velocity: " + tray.getFloodingVelocity() + " m/s");
System.out.println("Percent flood:     " + tray.getPercentFlood() + "%");
System.out.println("Weep check OK:     " + tray.isWeepCheckOk());
System.out.println("Entrainment:       " + tray.getFractionalEntrainment());
System.out.println("Downcomer backup:  " + tray.getDowncomerBackup() + " m");
System.out.println("Tray DP:           " + tray.getTrayPressureDrop() + " Pa");
System.out.println("O'Connell eff:     " + tray.getMurphreeEfficiency());
System.out.println("Design OK:         " + tray.isDesignOk());
```

---

## 4. Packing Hydraulics

**Class:** `neqsim.process.equipment.distillation.internals.PackingHydraulicsCalculator`

Hydraulics and mass transfer for random or structured packing.

### Correlations

| Calculation | Correlation | Reference |
|-------------|-------------|-----------|
| Flooding velocity | Eckert GPDC | Perry's Handbook |
| Pressure drop | Leva (1992) | Empirical correlation |
| Mass transfer | Onda (1968) | Wetted-wall model |
| HETP | HTU-NTU from Onda | HTU_OG derivation |

### Java Example

```java
import neqsim.process.equipment.distillation.internals.PackingHydraulicsCalculator;

PackingHydraulicsCalculator packing = new PackingHydraulicsCalculator();
packing.setPackingPreset("Pall-Ring-50");
packing.setDesignFloodFraction(0.70);
packing.setPackedHeight(5.0);         // m
packing.setColumnDiameter(1.2);       // m

// Fluid properties
packing.setVaporDensity(3.0);
packing.setLiquidDensity(800.0);
packing.setVaporViscosity(1.5e-5);
packing.setLiquidViscosity(5.0e-4);
packing.setSurfaceTension(0.025);
packing.setVaporDiffusivity(1.5e-5);
packing.setLiquidDiffusivity(2.0e-9);
packing.setVaporMassFlow(1.5);
packing.setLiquidMassFlow(4.0);

packing.calculate();

System.out.println("HETP:              " + packing.getHETP() + " m");
System.out.println("Flooding velocity: " + packing.getFloodingVelocity() + " m/s");
System.out.println("Percent flood:     " + packing.getPercentFlood() + "%");
System.out.println("Pressure drop:     " + packing.getTotalPressureDrop() + " Pa");
System.out.println("kGa:               " + packing.getKGa());
System.out.println("kLa:               " + packing.getKLa());
System.out.println("Fs factor:         " + packing.getFsFactor());
System.out.println("Design OK:         " + packing.isDesignOk());
```

---

## 5. Column Internals Designer

**Class:** `neqsim.process.equipment.distillation.internals.ColumnInternalsDesigner`

High-level facade that evaluates hydraulics on every tray of a converged
`DistillationColumn`, identifies the controlling tray, and produces a sizing report.

### Java Example

```java
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.distillation.internals.ColumnInternalsDesigner;

// After column.run() has converged:
ColumnInternalsDesigner designer = new ColumnInternalsDesigner(column);
designer.setInternalsType("tray");          // or "packed"
designer.setTrayType("sieve");
designer.setDesignFloodFraction(0.80);
designer.setTraySpacing(0.6);

designer.calculate();

System.out.println("Required diameter: " + designer.getRequiredDiameter() + " m");
System.out.println("Controlling tray:  " + designer.getControllingTrayIndex());
System.out.println("Max % flood:       " + designer.getMaxPercentFlood());
System.out.println("All trays OK:      " + designer.isAllTraysOk());
System.out.println("JSON:\n" + designer.toJson());

// Convenience from DistillationColumn:
column.calcColumnInternals("sieve");
column.calcColumnInternals();          // uses default "sieve"
```

---

## 6. Shortcut Distillation Column

**Class:** `neqsim.process.equipment.distillation.ShortcutDistillationColumn`

Fenske-Underwood-Gilliland (FUG) shortcut method for conceptual column design.

### Results Provided

- Minimum stages (Fenske equation)
- Minimum reflux ratio (Underwood equation)
- Actual stages at given reflux (Gilliland correlation)
- Feed tray location (Kirkbride equation)
- Condenser and reboiler duties

### Java Example

```java
import neqsim.process.equipment.distillation.ShortcutDistillationColumn;
import neqsim.process.equipment.stream.Stream;

// Feed: light hydrocarbons
SystemInterface feed = new SystemSrkEos(273.15 + 60.0, 15.0);
feed.addComponent("methane", 0.05);
feed.addComponent("ethane", 0.25);
feed.addComponent("propane", 0.35);
feed.addComponent("n-butane", 0.20);
feed.addComponent("n-pentane", 0.15);
feed.setMixingRule("classic");

Stream feedStream = new Stream("feed", feed);
feedStream.setFlowRate(10000.0, "kg/hr");
feedStream.run();

ShortcutDistillationColumn shortcut =
    new ShortcutDistillationColumn("Deethanizer", feedStream);
shortcut.setLightKey("ethane");
shortcut.setHeavyKey("propane");
shortcut.setLightKeyRecoveryInDistillate(0.99);
shortcut.setHeavyKeyRecoveryInBottoms(0.99);
shortcut.setRefluxRatio(1.5);
shortcut.run();

System.out.println("Min stages:    " + shortcut.getMinimumStages());
System.out.println("Min reflux:    " + shortcut.getMinimumRefluxRatio());
System.out.println("Actual stages: " + shortcut.getActualStages());
System.out.println("Feed tray:     " + shortcut.getFeedTray());
System.out.println("Cond. duty:    " + shortcut.getCondenserDuty("kW") + " kW");
System.out.println("Reb. duty:     " + shortcut.getReboilerDuty("kW") + " kW");
```

---

## 7. PVF Flash and Stream Summary

### PVF Flash

**Class:** `neqsim.thermodynamicoperations.flashops.PVFflash`

Pressure-Vapor Fraction flash: finds temperature T such that the system
achieves a specified molar vapor fraction at given pressure.

```java
import neqsim.thermodynamicoperations.ThermodynamicOperations;

SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 30.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("ethane", 0.15);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

// Flash at 50% vapor fraction
ops.PVFflash(30.0, 0.5);

System.out.println("T at 50% vapor: " + (fluid.getTemperature() - 273.15) + " C");

// Bubble point (vapor fraction = 0)
ops.PVFflash(30.0, 0.0);
System.out.println("Bubble point:   " + (fluid.getTemperature() - 273.15) + " C");

// Dew point (vapor fraction = 1)
ops.PVFflash(30.0, 1.0);
System.out.println("Dew point:      " + (fluid.getTemperature() - 273.15) + " C");
```

### Stream Summary Table

**Methods on `ProcessSystem`:**
- `getStreamSummaryTable()` - Returns a 2D String array of stream properties
- `getStreamSummaryJson()` - Returns a JSON string with all stream data

```java
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
process.run();

// Table format
String[][] table = process.getStreamSummaryTable();
for (String[] row : table) {
    for (String cell : row) {
        System.out.printf("%-20s", cell);
    }
    System.out.println();
}

// JSON format
String json = process.getStreamSummaryJson();
System.out.println(json);
```

---

## Test Coverage Summary

| Feature | Test Class | Tests | Status |
|---------|-----------|-------|--------|
| AirCooler | `AirCoolerTest` | 16 | All pass |
| PackedColumn | `PackedColumnTest` | 4 | All pass |
| TrayHydraulicsCalculator | `TrayHydraulicsCalculatorTest` | 7 | All pass |
| PackingHydraulicsCalculator | `PackingHydraulicsCalculatorTest` | 7 | All pass |
| ColumnInternalsDesigner | `ColumnInternalsDesignerTest` | 4 | All pass |
| ShortcutDistillationColumn | `ShortcutDistillationColumnTest` | 3 | All pass |
| PVFflash | `PVFflashTest` | 2 | All pass |

All existing distillation tests continue to pass (no regressions).
