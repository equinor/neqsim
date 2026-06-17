---
title: Absorbers and Strippers
description: "Documentation for mass transfer columns in NeqSim: rate-based packed absorber and stripper modeling, TEG dehydration with Fs-factor sizing, amine gas sweetening (MDEA/DEA/MEA), simple absorber models, and water stripping."
---

# Absorbers and Strippers

Documentation for mass transfer columns in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Absorber](#absorber)
- [Stripper](#stripper)
- [Simple Absorber](#simple-absorber)
- [Simple TEG Absorber](#simple-teg-absorber)
- [Simple Amine Absorber](#simple-amine-absorber)
- [Rate-Based Packed Column](#rate-based-packed-column)
- [Legacy Rate-Based Absorber](#legacy-rate-based-absorber)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.equipment.absorber`

**Classes:**
| Class                   | Description                                           |
| ----------------------- | ----------------------------------------------------- |
| `SimpleAbsorber`        | Simplified absorber model (base class)                |
| `SimpleTEGAbsorber`     | TEG dehydration with Fs-factor sizing                 |
| `SimpleAmineAbsorber`   | Amine gas sweetening (MDEA, DEA, MEA)                 |
| `WaterStripperColumn`   | Water stripping column                                |
| `RateBasedPackedColumn` | Counter-current packed absorber/stripper with segment profiles |
| `RateBasedAbsorber`     | Legacy first-pass gas-to-liquid rate absorber with enhancement factors |
| `H2SScavenger`          | H2S scavenger model                                   |

Absorbers transfer components from gas to liquid phase, while strippers transfer from liquid to gas. Use `RateBasedPackedColumn` when the transfer direction may reverse locally, when packing hydraulics matter, or when segment-by-segment profiles are needed.

---

## Absorber

The absorber classes in NeqSim model gas-liquid contactors where components transfer
from the gas phase into a liquid solvent. The `SimpleAbsorber` is the base class
providing equilibrium-stage calculations. For TEG dehydration use `SimpleTEGAbsorber`;
for amine gas sweetening use `SimpleAmineAbsorber`; for non-equilibrium packed-column
mass transfer use `RateBasedPackedColumn`.

### Basic Usage (SimpleAbsorber)

```java
import neqsim.process.equipment.absorber.SimpleAbsorber;

SimpleAbsorber absorber = new SimpleAbsorber("Gas Absorber", gasStream, solventStream);
absorber.setNumberOfStages(10);
absorber.setAproachToEquilibrium(0.7);  // Murphree stage efficiency
absorber.run();

Stream treatedGas = (Stream) absorber.getGasOutStream();
Stream richSolvent = (Stream) absorber.getLiquidOutStream();
```

### Stage Configuration

```java
absorber.setNumberOfStages(20);
absorber.setStageEfficiency(0.7);  // Murphree efficiency
```

---

## Stripper

### Basic Usage

```java
import neqsim.process.equipment.absorber.WaterStripperColumn;

WaterStripperColumn stripper = new WaterStripperColumn("Regenerator");
stripper.addGasInStream(stripGas);
stripper.addSolventInStream(richAmine);
stripper.run();

Stream leanAmine = (Stream) stripper.getLiquidOutStream();
Stream acidGas = (Stream) stripper.getGasOutStream();
```

---

## Simple Absorber

Simplified mass transfer model.

### Usage

```java
import neqsim.process.equipment.absorber.SimpleAbsorber;

SimpleAbsorber absorber = new SimpleAbsorber("CO2 Absorber", feedGas, solvent);
absorber.setAproachToEquilibrium(0.90);  // 90% approach to equilibrium
absorber.run();
```

### Fs-Factor and Wetting Rate

The base `SimpleAbsorber` class provides Fs-factor and wetting rate calculations used by all absorber subclasses:

$$F_s = v_s \cdot \sqrt{\rho_g}$$

where $v_s$ is the superficial gas velocity (m/s) and $\rho_g$ is gas density (kg/m3).

The wetting rate is the liquid volume flowing per unit packing area:

$$WR = \frac{Q_L}{\pi / 4 \cdot D^2}$$

---

## Simple TEG Absorber

`SimpleTEGAbsorber` extends the base absorber with TEG-specific sizing via the Kremser equation and Fs-factor capacity checking.

### Fs-Factor Sizing

The Fs-factor determines whether the contactor diameter is adequate for the gas load. Industry practice limits Fs to approximately 2.5-3.5 (Pa)^0.5 for structured packing:

```java
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;

SimpleTEGAbsorber tegAbsorber = new SimpleTEGAbsorber("TEG Contactor");
tegAbsorber.addGasInStream(wetGasStream);
tegAbsorber.addSolventInStream(leanTEGStream);
tegAbsorber.run();

// Fs-factor capacity check
double fs = tegAbsorber.getFsFactor();
double maxFs = tegAbsorber.getMaxAllowableFsFactor();  // default 3.0 (Pa)^0.5
boolean withinLimit = tegAbsorber.isFsFactorWithinDesignLimit();
double utilization = tegAbsorber.getFsFactorUtilization();  // fs / maxFs

// Minimum diameter needed
double minDiameter = tegAbsorber.getMinimumDiameterForFsLimit();

// Full validation report
String report = tegAbsorber.validateContactorDesign();
System.out.println(report);
```

### TEG Quality and Water Dew Point

```java
// Check equilibrium water dew point from lean TEG stream
double dewPointK = tegAbsorber.getLeanTEGEquilibriumWaterDewPoint();
System.out.println("Lean TEG equilibrium water dew point: " + (dewPointK - 273.15) + " °C");

// Check if TEG quality margin is adequate (target dew point in °C, margin in °C)
boolean adequate = tegAbsorber.hasAdequateTEGQualityMargin(-18.0, 10.0);
```

### Key Methods

| Method | Description |
|--------|-------------|
| `getFsFactor()` | Current Fs-factor from gas outlet stream |
| `getMaxAllowableFsFactor()` | Design limit (default 3.0) |
| `isFsFactorWithinDesignLimit()` | Check if Fs is below limit |
| `getFsFactorUtilization()` | Fraction of capacity used (0-1) |
| `getMinimumDiameterForFsLimit()` | Minimum diameter at current gas load |
| `getLeanTEGEquilibriumWaterDewPoint()` | Equilibrium water dew point (K) from lean TEG stream |
| `hasAdequateTEGQualityMargin(double, double)` | Check target dew point (°C) vs margin (°C) |
| `validateContactorDesign()` | Full text validation report |

---

## Simple Amine Absorber

`SimpleAmineAbsorber` models amine-based gas sweetening for CO2 and H2S removal. It calculates acid gas removal by applying user-specified removal efficiencies, and includes design calculations for column sizing, loading, and validation.

### Basic Usage

```java
import neqsim.process.equipment.absorber.SimpleAmineAbsorber;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create sour gas
SystemSrkEos sourGas = new SystemSrkEos(273.15 + 40.0, 70.0);
sourGas.addComponent("methane", 0.85);
sourGas.addComponent("CO2", 0.10);
sourGas.addComponent("H2S", 0.005);
sourGas.addComponent("ethane", 0.045);
sourGas.setMixingRule("classic");

Stream sourGasStream = new Stream("Sour Gas", sourGas);
sourGasStream.setFlowRate(50000.0, "kg/hr");

// Create amine absorber
SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("MDEA Absorber", sourGasStream);
absorber.setAmineType("MDEA");
absorber.setAmineConcentrationWtPct(50.0);
absorber.setCO2RemovalEfficiency(0.95);
absorber.setH2SRemovalEfficiency(0.99);

// Wire into process
ProcessSystem process = new ProcessSystem();
process.add(sourGasStream);
process.add(absorber);
process.run();

// Get treated gas
Stream sweetGas = (Stream) absorber.getSweetGasOutStream();
```

### With Lean Amine Stream

When a lean amine stream is provided, the absorber also calculates the rich amine outlet (acid gas picked up by the solvent):

```java
// Lean amine
SystemSrkEos amineFluid = new SystemSrkEos(273.15 + 42.0, 70.0);
amineFluid.addComponent("MDEA", 0.50);
amineFluid.addComponent("water", 0.50);
amineFluid.setMixingRule("classic");

Stream leanAmine = new Stream("Lean Amine", amineFluid);
leanAmine.setFlowRate(30000.0, "kg/hr");

absorber.setLeanAmineInStream(leanAmine);
process.add(leanAmine);
process.run();

// Rich amine with absorbed acid gas
Stream richAmine = (Stream) absorber.getRichAmineOutStream();
```

### Design Calculations

```java
// Acid gas loading
absorber.setLeanAmineLoading(0.01);
absorber.setApproachToEquilibrium(0.70);
double richLoading = absorber.calcRichAmineLoading(0.50);
// richLoading = 0.01 + (0.50 - 0.01) * 0.70 = 0.353 mol/mol

// Amine circulation rate
double rate = absorber.calcRequiredCirculationRate(
    10.0,     // mol/s acid gas to remove
    1050.0,   // kg/m3 amine density
    0.119     // kg/mol MDEA molar mass
);

// Packing height with redistribution sections
absorber.setMaxPackingHeightPerSection(5.5);
absorber.calcPackingHeight(1.0, 12.0);  // HTU=1.0m, NTU=12
int sections = absorber.getNumberOfPackingSections();  // 3

// Demister K-factor
double kFactor = absorber.calcDemisterKFactor(2.0, 50.0, 1050.0);
boolean withinLimit = absorber.isDemisterWithinLimit();  // K <= 0.08

// Temperature margin check
boolean tempOk = absorber.checkAmineTemperatureMargin(30.0, 37.0);  // need 6°C margin

// Foaming derating
double effectiveFlow = absorber.getEffectiveGasCapacityWithFoamingMargin(100.0);  // 120.0
```

### Design Validation

```java
// Run all design checks at once
Map<String, SimpleAmineAbsorber.DesignCheck> checks = absorber.validateDesign();
for (Map.Entry<String, SimpleAmineAbsorber.DesignCheck> entry : checks.entrySet()) {
    System.out.println(entry.getKey() + ": " +
        (entry.getValue().isPassed() ? "PASS" : "FAIL") +
        " - " + entry.getValue().getDetail());
}

// Get full design summary
System.out.println(absorber.getDesignSummary());
```

### Design Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| Amine type | MDEA | Amine solvent (MDEA, DEA, MEA) |
| Concentration | 50 wt% | Amine weight percent in lean solvent |
| CO2 removal | 90% | Overall CO2 removal efficiency |
| H2S removal | 99% | Overall H2S removal efficiency |
| Foaming margin | 20% | Capacity derating for foaming |
| Max packing height | 5.5 m | Maximum per section before redistribution |
| Amine temperature margin | 6 °C | Lean amine T above gas feed T |
| Gas carry-under | 0.03 | Am3 gas per Am3 amine |
| Demister K-factor limit | 0.08 m/s | Wire mesh at gas outlet |
| Approach to equilibrium | 70% | Fraction of thermodynamic equilibrium loading |

---

## Examples

### Example 1: Amine Gas Treating

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.absorber.SimpleAmineAbsorber;

// Sour gas
SystemSrkCPAstatoil sourGas = new SystemSrkCPAstatoil(313.15, 70.0);
sourGas.addComponent("methane", 0.85);
sourGas.addComponent("CO2", 0.10);
sourGas.addComponent("H2S", 0.01);
sourGas.addComponent("water", 0.04);
sourGas.setMixingRule("classic");

Stream gasIn = new Stream("Sour Gas", sourGas);
gasIn.setFlowRate(100000.0, "Sm3/hr");
gasIn.run();

// Amine absorber
SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("MDEA Contactor", gasIn);
absorber.setAmineType("MDEA");
absorber.setAmineConcentrationWtPct(50.0);
absorber.setCO2RemovalEfficiency(0.95);
absorber.setH2SRemovalEfficiency(0.99);
absorber.run();

// Results
Stream sweetGas = (Stream) absorber.getSweetGasOutStream();
```

### Example 2: TEG Dehydration

```java
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;

// Wet natural gas
SystemSrkEos wetGas = new SystemSrkEos(303.15, 70.0);
wetGas.addComponent("methane", 0.90);
wetGas.addComponent("ethane", 0.05);
wetGas.addComponent("propane", 0.03);
wetGas.addComponent("water", 0.02);
wetGas.setMixingRule("classic");

Stream gasIn = new Stream("Wet Gas", wetGas);
gasIn.setFlowRate(5000000.0, "Sm3/day");
gasIn.run();

// Lean TEG
SystemSrkEos teg = new SystemSrkEos(313.15, 70.0);
teg.addComponent("TEG", 0.99);
teg.addComponent("water", 0.01);
teg.setMixingRule("classic");

Stream leanTEG = new Stream("Lean TEG", teg);
leanTEG.setFlowRate(1000.0, "kg/hr");
leanTEG.run();

// Contactor
SimpleTEGAbsorber contactor = new SimpleTEGAbsorber("TEG Contactor");
contactor.addGasInStream(gasIn);
contactor.addSolventInStream(leanTEG);
contactor.run();

// Results
Stream dryGas = (Stream) contactor.getGasOutStream();
double fs = contactor.getFsFactor();
System.out.println("Fs-factor: " + fs);
```

### Example 3: Water Wash Column

```java
// Gas with methanol
SystemSrkEos gas = new SystemSrkEos(280.0, 50.0);
gas.addComponent("methane", 0.95);
gas.addComponent("methanol", 0.03);
gas.addComponent("water", 0.02);
gas.setMixingRule("classic");

Stream gasIn = new Stream("Gas", gas);
gasIn.setFlowRate(10000.0, "kg/hr");
gasIn.run();

// Wash water
SystemSrkEos water = new SystemSrkEos(290.0, 50.0);
water.addComponent("water", 1.0);
water.setMixingRule("classic");

Stream washWater = new Stream("Wash Water", water);
washWater.setFlowRate(500.0, "kg/hr");
washWater.run();

// Simple absorber (constructor takes gas and solvent streams)
SimpleAbsorber waterWash = new SimpleAbsorber("Water Wash", gasIn, washWater);
waterWash.setAproachToEquilibrium(0.85);
waterWash.run();

Stream cleanGas = (Stream) waterWash.getGasOutStream();
```

---

## Rate-Based Packed Column

`RateBasedPackedColumn` is the recommended non-equilibrium packed-column model for physical absorption and stripping. The gas enters the bottom, the liquid enters the top, and the packed section is solved as counter-current axial segments.

The model combines existing NeqSim functionality:

- Thermodynamic flashes provide the segment equilibrium driving force.
- Physical-property models provide effective gas and liquid diffusivities after `initProperties()`.
- `PackingHydraulicsCalculator` provides wetted area, pressure drop, flooding fraction, and volumetric film coefficients.
- A Maxwell-Stefan matrix film model can correct component film coefficients using NeqSim binary diffusivities and phase composition.
- A Chilton-Colburn analogy can calculate explicit interphase heat transfer from the same packed-bed film data.
- An optional simultaneous segment residual solver couples component fluxes, interface temperature, interface equilibrium, and segment enthalpy targets.
- An optional column-wide equation-oriented solver couples all segment fluxes, outlet temperatures, interface temperatures, component balances, and energy residuals in one damped Newton system.
- `PackingSpecificationLibrary` resolves built-in and CSV-backed packing data from `designdata/Packing.csv`.

Positive component transfer means gas-to-liquid absorption. Negative transfer means liquid-to-gas stripping.

### Model Scope and Solver

The packed section is discretized into axial segments. For each segment, the model estimates an interface temperature, runs a local flash calculation at the interface to obtain gas and liquid equilibrium compositions, applies gas- and liquid-film transport coefficients from the packing hydraulics model, and transfers the selected components between phases. A fixed-point counter-current iteration updates the liquid profile from top to bottom and the gas profile from bottom to top until the liquid profile change is below the configured tolerance.

The default film model is `MAXWELL_STEFAN_MATRIX`. It mirrors the structure of the Krishna-Standart film model used in the fluid-mechanics package: binary diffusion coefficients from NeqSim physical properties are assembled into a multicomponent resistance matrix and inverted to obtain component-specific film coefficients. If binary diffusion data are missing or the matrix is ill-conditioned, the model falls back to robust effective diffusivities.

The default heat-transfer model is `CHILTON_COLBURN_ANALOGY`. It converts gas- and liquid-side mass-transfer coefficients into volumetric heat-transfer coefficients using phase density, heat capacity, viscosity, diffusivity, and thermal conductivity. Segment heat transfer is explicit and rate-limited to avoid overshooting the thermal approach; final segment states are re-flashed after material and heat transfer.

The default segment solver is `SEQUENTIAL_EXPLICIT`, which keeps the established robust counter-current profile behaviour. For detailed studies, set `SegmentSolver.SIMULTANEOUS_RESIDUAL` to solve component transfer rates and interface temperature together. The simultaneous residual vector contains one Maxwell-Stefan flux residual per transferred component plus an interfacial heat-balance residual:

$$
r_i = N_i - N_{i,MS}
$$

$$
r_Q = h_g A_v V (T_g - T_i) + \sum_i N_i \bar{H}_{i,g}^{I} - h_l A_v V (T_i - T_l) - \sum_i N_i \bar{H}_{i,l}^{I}
$$

where $N_i$ is the segment molar transfer rate, $N_{i,MS}$ is the Maxwell-Stefan film prediction, $T_i$ is the interface temperature, $A_v V$ is the wetted interfacial area in the segment, and $\bar{H}_{i,g}^{I}$ and $\bar{H}_{i,l}^{I}$ are interface component molar enthalpies. After the material transfer is applied, the gas and liquid outlet states are driven toward their segment enthalpy targets using PH flash calculations. If a trial interface flash or PH flash enters an invalid thermodynamic state, the solver falls back to bulk-phase interface data or a bounded temperature initialization so the column solve remains stable.

### Column-Wide Equation-Oriented Solver

The default column solver is `ColumnSolver.FIXED_POINT_PROFILE`. For research-grade absorber and stripper studies, `ColumnSolver.EQUATION_ORIENTED` uses the fixed-point profile as a seed and then solves a column-wide residual system with homotopy continuation and damped Newton steps. The unknown vector contains, for every segment, the component molar fluxes, interface temperature, gas outlet temperature, and liquid outlet temperature. Gas and liquid segment compositions and molar flows are reconstructed from the full-column component balances at every residual evaluation.

The equation-oriented residual vector includes:

- one Maxwell-Stefan flux residual per transferred component and segment;
- one interfacial heat-balance residual per segment;
- gas and liquid energy residuals expressed as outlet temperature errors against enthalpy targets;
- gas and liquid component-balance residual diagnostics for every transferred component and segment.

The Jacobian is assembled in a sparse row/column structure from finite-difference perturbations and solved as a damped least-squares Newton step. Homotopy ramps the transport equations from a mild continuation factor to the full Maxwell-Stefan/heat-transfer residual system. The JSON report exposes `columnSolver`, `columnResidualNorm`, `columnResidualIterations`, gas and liquid component-balance residuals, and the column energy-balance residual.

```java
column.setColumnSolver(RateBasedPackedColumn.ColumnSolver.EQUATION_ORIENTED);
column.setColumnResidualTolerance(1.0e-5);
column.setMaxColumnResidualIterations(8);
column.setColumnHomotopySteps(3);
column.run();

double residual = column.getLastColumnResidualNorm();
double gasBalance = column.getLastGasComponentBalanceResidual();
double liquidBalance = column.getLastLiquidComponentBalanceResidual();
```

Keep the fixed-point solver for routine screening and production workflows. Use the equation-oriented solver when coupled heat and mass transfer, interface equilibrium, and whole-column balance residuals are part of the study acceptance criteria.

Use this model when these details matter:

- component transfer can be limited by film rates instead of equilibrium stages;
- absorption and stripping may both occur in different parts of the packed bed;
- packing type, wetted area, pressure drop, and flooding fraction are part of the engineering question;
- segment profiles are needed for diagnostics, scale-up, or model calibration.

For quick screening where only a stage efficiency or approach-to-equilibrium factor is available, `SimpleAbsorber`, `SimpleTEGAbsorber`, or `SimpleAmineAbsorber` remain faster and easier to parameterize.

### Basic Usage

```java
import neqsim.process.equipment.distillation.RateBasedPackedColumn;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface gasFluid = new SystemSrkEos(313.15, 50.0);
gasFluid.addComponent("methane", 0.90);
gasFluid.addComponent("CO2", 0.10);
gasFluid.setMixingRule("classic");

Stream gasIn = new Stream("gas in", gasFluid);
gasIn.setFlowRate(1000.0, "kg/hr");
gasIn.run();

SystemInterface liquidFluid = new SystemSrkEos(303.15, 50.0);
liquidFluid.addComponent("water", 1.0);
liquidFluid.addComponent("CO2", 0.0);
liquidFluid.setMixingRule("classic");

Stream liquidIn = new Stream("lean liquid", liquidFluid);
liquidIn.setFlowRate(2000.0, "kg/hr");
liquidIn.run();

RateBasedPackedColumn column = new RateBasedPackedColumn("CO2 packed absorber", gasIn, liquidIn);
column.setColumnDiameter(1.0);
column.setPackedHeight(6.0);
column.setNumberOfSegments(4);
column.setPackingType("Pall-Ring-50");
column.setTransferComponents("CO2");
column.run();

double co2Transfer = column.getComponentTransferTotals().get("CO2");
Stream treatedGas = (Stream) column.getGasOutStream();
Stream richLiquid = (Stream) column.getLiquidOutStream();
String reportJson = column.toJson();
```

The API used above is covered by `RateBasedPackedColumnTest`.

### TEG Dehydration Guidance

For natural gas dehydration with triethylene glycol, use a CPA-based thermodynamic system for both the wet gas and lean TEG streams so water-glycol interactions are represented consistently. The focused tests use `SystemSrkCPAstatoil`, call `createDatabase(true)`, and set CPA mixing rule `10` before running the streams.

Recommended setup checks:

| Check | Guidance |
|-------|----------|
| Transfer components | Use `setTransferComponents("water")` for dehydration so methane, glycol, and heavier hydrocarbons do not move unless intentionally modelled. |
| Packing | Structured packing such as `Mellapak-250Y` is a good default for compact TEG contactors; random packing can be used for retrofit studies. |
| Packed height | Increase `setPackedHeight(...)` or `setNumberOfSegments(...)` when outlet water is sensitive to discretization. |
| Solvent circulation | Check the circulation ratio against typical TEG absorber practice, not only the outlet water content. |
| Mass-transfer correction | Treat `setMassTransferCorrectionFactor(...)` as a calibration or design-margin parameter until plant/vendor data are available. |
| Heat transfer | Leave `CHILTON_COLBURN_ANALOGY` enabled when gas and lean TEG temperatures differ; disable only for isothermal sensitivity studies. |

### TEG Circulation Ratio

The TEG dehydration regression test now checks that the model gives a typical water-removal efficiency for a synthetic packed contactor. The design metric is usually reported as liquid TEG circulation per water removed:

$$
R_{TEG} = \frac{\dot{m}_{TEG} / \rho_{TEG}}{\dot{m}_{H2O,removed}}
$$

where $R_{TEG}$ is in L TEG/kg H2O, $\dot{m}_{TEG}$ is the lean TEG circulation in kg/hr, $\rho_{TEG}$ is the lean TEG density in kg/L, and $\dot{m}_{H2O,removed}$ is the water removed from the gas in kg/hr.

Typical absorber practice is about 15-40 L TEG/kg H2O removed, as also noted in the [TEG dehydration tutorial](../../tutorials/teg_dehydration_tutorial.md). With a lean TEG density near 1.125 kg/L, that corresponds to approximately 0.02-0.07 kg water removed per kg TEG circulated. Values outside this band should prompt a review of wet-gas water loading, lean TEG rate, packing height, mass-transfer factor, and thermodynamic setup.

### Validation Coverage

`RateBasedPackedColumnTest` exercises the behaviours that are most important for a reusable non-equilibrium model:

| Test area | What is checked |
|-----------|-----------------|
| Absorption | CO2 decreases in gas and total CO2 transfer is positive. |
| Stripping | CO2 increases in gas and total CO2 transfer is negative. |
| TEG dehydration | Water moves from wet natural gas to lean TEG using CPA thermodynamics. |
| TEG circulation | Water removed per TEG circulation is within the typical 15-40 L/kg range. |
| Interface equilibrium | Segment results expose gas/liquid interface compositions and K-ratios. |
| Heat transfer | Segment results expose heat-transfer coefficients and heat-transfer rate. |
| Simultaneous residuals | Optional residual mode exposes flux residuals, heat-balance residuals, and enthalpy-balance diagnostics. |
| Column equation-oriented solve | Optional column-wide solver exposes residual norm, component-balance diagnostics, and structured-packing TEG benchmark bands. |
| Structured-packing distillation | Mellapak-style HETP, pressure drop, and flood fraction remain inside broad published design bands. |
| Packed height | Taller packing gives stronger absorption for the same inlet streams. |
| Zero height | A packed height of zero gives no molar transfer. |
| Material balance | Selected transferred components are conserved across gas and liquid outlets. |
| Reporting | Outlet stream introspection and JSON reports include segment transfer data. |

### Packing Data

The packing library supports aliases such as `pall ring 50`, `Pall-Ring-50`, `Mellapak250Y`, and `IMTP 70`. It combines built-in data with the `designdata/Packing.csv` table.

| Random packing examples | Structured packing examples |
|-------------------------|-----------------------------|
| Pall-Ring-25, Pall-Ring-38, Pall-Ring-50 | Mellapak-125Y, Mellapak-250Y, Mellapak-350Y, Mellapak-500Y |
| Raschig-Ring-25, Raschig-Ring-50 | Flexipac-1Y, Flexipac-2Y, Flexipac-3Y |
| IMTP-25, IMTP-40, IMTP-50, IMTP-70 | Sulzer-BX, Sulzer-CY |
| Berl-Saddle-25, Berl-Saddle-38, Berl-Saddle-50 | |
| Intalox-Saddle-25 | |

### Segment Profiles

Per-segment results include temperature, pressure, diffusivities, wetted area, `kGa`, `kLa`, pressure drop, flooding fraction, component transfer, interface compositions, heat-transfer rate, and residual diagnostics when the simultaneous solver is used.

```java
for (RateBasedPackedColumn.SegmentResult segment : column.getSegmentResults()) {
    System.out.printf("Segment %d: kGa=%.4f  kLa=%.4f  CO2 transfer=%.6g mol/s%n",
        segment.getSegmentNumber(),
        segment.getKGa(),
        segment.getKLa(),
        segment.getComponentMoleTransfer().get("CO2"));
}
```

### Key Methods

| Method | Description |
|--------|-------------|
| `setGasInStream(StreamInterface)` / `addGasInStream(StreamInterface)` | Gas feed entering the bottom |
| `setLiquidInStream(StreamInterface)` / `addLiquidInStream(StreamInterface)` | Liquid feed entering the top |
| `setColumnDiameter(double)` | Column internal diameter in metres |
| `setPackedHeight(double)` | Packed bed height in metres |
| `setNumberOfSegments(int)` | Axial discretization |
| `setPackingType(String)` | Packing name or alias from the packing library |
| `setTransferComponents(String...)` | Optional component whitelist; empty means all components |
| `setMassTransferCorrelation(MassTransferCorrelation)` | `ONDA_1968` or `BILLET_SCHULTES_1999` |
| `setFilmModel(FilmModel)` | `MAXWELL_STEFAN_MATRIX` or `OVERALL_TWO_RESISTANCE` |
| `setColumnSolver(ColumnSolver)` | `FIXED_POINT_PROFILE` for the robust default or `EQUATION_ORIENTED` for the column-wide residual solve |
| `setColumnResidualTolerance(double)` | Normalized residual tolerance for the column-wide residual norm |
| `setMaxColumnResidualIterations(int)` | Maximum damped Newton iterations per homotopy step |
| `setColumnHomotopySteps(int)` | Number of continuation steps for the equation-oriented solve |
| `setSegmentSolver(SegmentSolver)` | `SEQUENTIAL_EXPLICIT` for robust default profile stepping or `SIMULTANEOUS_RESIDUAL` for coupled segment residuals |
| `setSegmentResidualTolerance(double)` | Convergence tolerance for the simultaneous residual norm |
| `setMaxSegmentResidualIterations(int)` | Maximum Newton iterations for each simultaneous segment solve |
| `setMassTransferCorrectionFactor(double)` | Multiplier for effective segment transfer, useful for calibration and sensitivity studies |
| `setHeatTransferModel(HeatTransferModel)` | `CHILTON_COLBURN_ANALOGY` or `NONE` |
| `setHeatTransferCorrectionFactor(double)` | Multiplier for interphase heat-transfer coefficients |
| `setMaxIterations(int)` | Maximum counter-current profile iterations |
| `setConvergenceTolerance(double)` | Liquid-profile convergence tolerance |
| `getGasOutStream()` / `getLiquidOutStream()` | Treated gas and rich liquid outlet streams |
| `getSegmentResults()` | Segment profile from bottom to top |
| `getComponentTransferTotals()` | Total component transfer, positive for absorption |
| `getTotalAbsoluteMolarTransfer()` | Sum of absolute transferred molar rates across all selected components |
| `toJson()` | Complete rate-based column report |

## Legacy Rate-Based Absorber

`RateBasedAbsorber` extends `SimpleAbsorber` with first-pass gas-to-liquid mass transfer calculations
using published correlations and optional reactive enhancement factors. For new packed absorber and stripper studies, prefer `RateBasedPackedColumn` because it uses the shared packing library, physical-property diffusivities, segment profiles, and bidirectional transfer.

### When to Use Rate-Based vs Equilibrium

| Factor | Equilibrium (`SimpleAbsorber`) | Packed non-equilibrium (`RateBasedPackedColumn`) | Legacy rate-based (`RateBasedAbsorber`) |
|--------|--------------------------------|--------------------------------------------------|-----------------------------------------|
| Speed | Fast | Moderate | Moderate |
| Input data | Stage efficiency or approach factor | Packing type, diameter, packed height, transfer components | Packing type, column diameter, packed height |
| Physical basis | Assumed HETP/efficiency | Counter-current film transfer with local flash driving forces | Film theory with correlations |
| Transfer direction | Usually gas-to-liquid by service assumption | Bidirectional by component and segment | Primarily gas-to-liquid |
| Diagnostics | Overall outlet streams | Segment profile, hydraulics, transfer totals, JSON report | Stage profile and enhancement factors |
| Use case | Screening, quick estimates | TEG, amine, water wash, and physical absorber/stripper studies where packing and profiles matter | Legacy studies using enhancement-factor APIs |

### Mass Transfer Models

Two correlations are available:

**Onda et al. (1968)** — Classic correlation for random and structured packings:

$$k_G a_w = C \cdot \left(\frac{Re_G}{a_p}\right)^{0.7} Sc_G^{1/3} (a_p D_p)^{-2.0}$$

$$k_L \left(\frac{\rho_L}{\mu_L g}\right)^{1/3} = 0.0051 \left(\frac{Re_L}{a_w}\right)^{2/3} Sc_L^{-1/2} (a_p D_p)^{0.4}$$

**Billet & Schultes (1999)** — Modern correlation with packing-specific constants ($C_l$, $C_v$):

$$k_L = C_l \left(\frac{D_L}{d_h}\right) \sqrt{\frac{u_{Ls}}{a_p \varepsilon_h}}$$

$$k_G = C_v \frac{1}{\varepsilon - h_L} \sqrt{\frac{a_p D_G}{u_{Gs}}}$$

### Enhancement Factor Models

For reactive absorption (e.g., CO2 into amine):

| Model | Use Case |
|-------|----------|
| `NONE` | Physical absorption only |
| `HATTA_PSEUDO_FIRST_ORDER` | Fast pseudo-first-order reaction (e.g., CO2 + MEA) |
| `VAN_KREVELEN_HOFTIJZER` | Second-order reaction with finite amine concentration |

The Hatta number characterises the ratio of reaction rate to diffusion rate:

$$Ha = \frac{\sqrt{k_1 D_L}}{k_L^0}$$

where $k_1$ is the pseudo-first-order rate constant and $D_L$ is liquid diffusivity.

### Basic Usage

```java
import neqsim.process.equipment.absorber.RateBasedAbsorber;
import neqsim.process.equipment.absorber.RateBasedAbsorber.MassTransferModel;
import neqsim.process.equipment.absorber.RateBasedAbsorber.EnhancementModel;

// Create streams (gas and solvent)
RateBasedAbsorber absorber = new RateBasedAbsorber("CO2 Absorber");
absorber.addGasInStream(sourGasStream);
absorber.addSolventInStream(amineSolventStream);

// Column geometry
absorber.setColumnDiameter(2.0);       // 2 m diameter
absorber.setPackedHeight(10.0);        // 10 m packed height
absorber.setNumberOfTheoreticalStages(10);

// Packing properties (e.g., Mellapak 250Y)
absorber.setPackingSpecificArea(250.0);  // m2/m3
absorber.setPackingVoidFraction(0.95);
absorber.setPackingNominalSize(0.025);   // 25 mm
absorber.setPackingCriticalSurfaceTension(0.075);  // N/m

// Mass transfer model
absorber.setMassTransferModel(MassTransferModel.ONDA_1968);

// Enhancement factor for reactive absorption
absorber.setEnhancementModel(EnhancementModel.HATTA_PSEUDO_FIRST_ORDER);
absorber.setReactionRateConstant(5000.0);  // 1/s for CO2 + amine

absorber.run();

// Results
double kGa = absorber.getOverallKGa();
double kLa = absorber.getOverallKLa();
double wettedArea = absorber.getWettedArea();
double htu = absorber.getHeightOfTransferUnit();
double ntu = absorber.getNumberOfTransferUnits();
```

### Stage Results

Per-stage detail is available for profiling the column:

```java
java.util.List<RateBasedAbsorber.StageResult> stages = absorber.getStageResults();
for (RateBasedAbsorber.StageResult stage : stages) {
    System.out.printf("Stage %d: T=%.1f K, kGa=%.4f, kLa=%.4f, E=%.2f%n",
        stage.getStageNumber(),
        stage.getTemperature(),
        stage.getKGa(),
        stage.getKLa(),
        stage.getEnhancementFactor());
}
```

### Billet-Schultes Model

When using Billet-Schultes, supply packing-specific constants from the literature:

```java
absorber.setMassTransferModel(MassTransferModel.BILLET_SCHULTES_1999);
absorber.setBilletSchultesConstants(1.2, 0.4);  // Cl, Cv for Mellapak 250Y
```

### Key Methods Reference

| Method | Description |
|--------|-------------|
| `setMassTransferModel(MassTransferModel)` | `ONDA_1968` or `BILLET_SCHULTES_1999` |
| `setEnhancementModel(EnhancementModel)` | `NONE`, `HATTA_PSEUDO_FIRST_ORDER`, `VAN_KREVELEN_HOFTIJZER` |
| `setColumnDiameter(double)` | Column ID in metres |
| `setPackedHeight(double)` | Height of packing in metres |
| `setPackingSpecificArea(double)` | Packing area per unit volume (m2/m3) |
| `setPackingVoidFraction(double)` | Void fraction (0-1) |
| `setPackingNominalSize(double)` | Nominal packing size (m) |
| `setPackingCriticalSurfaceTension(double)` | Surface tension of packing (N/m) |
| `setReactionRateConstant(double)` | Pseudo-1st-order rate constant (1/s) |
| `setStoichiometricRatio(double)` | Moles of amine per mole of CO2 |
| `setBilletSchultesConstants(double, double)` | Packing constants Cl, Cv |
| `getOverallKGa()` / `getOverallKLa()` | Overall volumetric coefficients (1/s) |
| `getWettedArea()` | Effective wetted area (m2/m3) |
| `getHeightOfTransferUnit()` | HTU based on gas-side (m) |
| `getNumberOfTransferUnits()` | NTU for the given packed height |
| `getStageResults()` | List of per-stage mass transfer results |

---

## Related Documentation

- [Equipment Index](index.md) - All equipment
- [Water Treatment](water_treatment) - Hydrocyclones and gas flotation units
- [Distillation](distillation) - Distillation columns
- [Separators](separators) - Phase separation
- [H2S Scavenger Guide](../H2S_scavenger_guide) - Chemical scavenging of H2S
- [Multiphase Flow Correlations](multiphase_flow_correlations) - Pipeline flow models
