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
- [Stream Introspection](#stream-introspection)
- [Named Controllers and Connections](#named-controllers-and-connections)
- [Complete Process Flowsheet](#complete-process-flowsheet)
- [Recycle and Adjuster](#recycle-and-adjuster)
- [PVT Simulations](#pvt-simulations)
- [Standards Calculations](#standards-calculations)
- [Field Development Economics](#field-development-economics-npv--cash-flow)
- [SURF Cost Estimation](#surf-cost-estimation-subsea-field-development)
- [Test Patterns](#test-patterns)
- [Jupyter Notebook Patterns](#jupyter-notebook-patterns)
- [Benchmark Validation Notebook](#benchmark-validation-notebook)
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
double[] dewT = ops.get("dewT");
double[] dewP = ops.get("dewP");
double[] bubT = ops.get("bubT");
double[] bubP = ops.get("bubP");
```

---

## Reading Properties

```java
// CRITICAL: call initProperties() after flash — this initializes BOTH
// thermodynamic properties (Cp, enthalpy, entropy) AND transport properties
// (viscosity, thermal conductivity, density with volume correction).
//
// WHY THIS IS NEEDED: Flash calculations (TPflash, PHflash, PSflash) only solve
// phase equilibrium (compositions, phase fractions, Z-factor). They do NOT compute
// transport properties automatically — this is by design for performance, since
// many internal NeqSim loops (stability analysis, phase envelope) only need
// equilibrium results. Without initProperties(), getViscosity() and
// getThermalConductivity() will return ZERO.
//
// NOTE: When using ProcessSystem.run(), initProperties() is called internally
// by each equipment's run() method — no separate call needed for process streams.
fluid.initProperties();

// System-level
double density = fluid.getDensity("kg/m3");
double molarMass = fluid.getMolarMass("kg/mol");
double Z = fluid.getZ();
int numPhases = fluid.getNumberOfPhases();

// Phase-level (transport properties REQUIRE initProperties())
double gasDensity = fluid.getPhase("gas").getDensity("kg/m3");
double oilVisc = fluid.getPhase("oil").getViscosity("kg/msec");
double gasThermCond = fluid.getPhase("gas").getThermalConductivity("W/mK");
double gasCp = fluid.getPhase("gas").getCp("J/kgK");
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

### Compressor Casing Mechanical Design (API 617 / ASME VIII)

```java
// Via CompressorMechanicalDesign (after process run)
Compressor comp = new Compressor("K-100", gasStream);
comp.setOutletPressure(120.0, "bara");
comp.setIsentropicEfficiency(0.75);
process.add(comp);
process.run();

comp.initMechanicalDesign();
CompressorMechanicalDesign design =
    (CompressorMechanicalDesign) comp.getMechanicalDesign();
design.setCasingMaterialGrade("SA-516-70");
design.setCasingCorrosionAllowanceMm(3.0);
// For sour service:
// design.setH2sPartialPressureKPa(0.5);
design.calcDesign();

CompressorCasingDesignCalculator casing = design.getCasingDesignCalculator();
double wallMm = casing.getRequiredWallThicknessMm();
double mawpBarg = casing.getMawpBarg();
String flangeClass = casing.getSelectedFlangeClass();
boolean naceOk = casing.isNaceCompliant();
String json = casing.toJson();
```

```java
// Standalone calculator (without process simulation)
CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
calc.setDesignPressureBarg(135.0);
calc.setDesignTemperatureC(200.0);
calc.setInnerDiameterMm(800.0);
calc.setMaterialGrade("F316L");
calc.setCorrosionAllowanceMm(1.5);
calc.setJointEfficiency(0.85);
calc.setCasingType("horizontally-split");
calc.calculate();
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

### Two-Fluid Pipe (Transient Multiphase)

```java
TwoFluidPipe pipe = new TwoFluidPipe("Flowline", feedStream);
pipe.setLength(5000);            // meters
pipe.setDiameter(0.3);           // meters
pipe.setNumberOfSections(100);   // computational cells
pipe.setRoughness(4.5e-5);       // wall roughness (m)

// Steady-state initialization
pipe.run();

// Transient simulation
UUID simId = UUID.randomUUID();
for (int step = 0; step < 600; step++) {
    pipe.runTransient(0.5, simId);  // 0.5 s time step
}

// Results
double[] pressures = pipe.getPressureProfile();
double[] holdups = pipe.getLiquidHoldupProfile();
double inventory = pipe.getLiquidInventory("m3");
```

### Two-Fluid Pipe Benchmark (Cross-Validate vs Beggs & Brill)

```java
// Compare TwoFluidPipe pressure drop against PipeBeggsAndBrills
PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB", feedStream);
bbPipe.setLength(5000); bbPipe.setDiameter(0.3);
bbPipe.setAngle(0); bbPipe.setPipeWallRoughness(4.5e-5);

TwoFluidPipe tfPipe = new TwoFluidPipe("TF", feedStream);
tfPipe.setLength(5000); tfPipe.setDiameter(0.3);
tfPipe.setNumberOfSections(50); tfPipe.setRoughness(4.5e-5);

// Run both
bbPipe.run(); tfPipe.run();
double dpBB = feedStream.getPressure() - bbPipe.getOutletStream().getPressure();
double dpTF = feedStream.getPressure() - tfPipe.getOutletStream().getPressure();
double ratio = dpTF / dpBB;
// Expect ratio 0.8–1.3 for engineering accuracy
```

---

## Stream Introspection

Query inlet/outlet streams on any equipment without casting:

```java
// Works on any ProcessEquipmentInterface
List<StreamInterface> inlets = equipment.getInletStreams();
List<StreamInterface> outlets = equipment.getOutletStreams();

// Example: walk the flowsheet
for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
    System.out.printf("%-20s  in=%d  out=%d%n",
        unit.getName(),
        unit.getInletStreams().size(),
        unit.getOutletStreams().size());
}
```

| Equipment | Inlets | Outlets |
|-----------|--------|---------|
| TwoPortEquipment (Heater, Compressor, Valve, ...) | 1 | 1 |
| Separator | N | 2 (gas, liquid) |
| ThreePhaseSeparator | N | 3 (gas, oil, water) |
| Mixer | N | 1 |
| Splitter | 1 | N |

---

## Named Controllers and Connections

### Multiple Controllers per Equipment

```java
// Attach by tag
valve.addController("LC-100", levelController);
valve.addController("PC-200", pressureController);

// Retrieve by tag
ControllerDeviceInterface lc = valve.getController("LC-100");

// All controllers
Collection<ControllerDeviceInterface> all = valve.getControllers();
```

### Explicit Connections (metadata for DEXPI / diagrams)

```java
process.connect(feed, separator, feed.getOutletStream(),
    ProcessConnection.ConnectionType.MATERIAL, "Feed to HP Sep");

// Simple form
process.connect(separator, compressor);

// Query
List<ProcessConnection> conns = process.getConnections();
```

### Unified Element Query

```java
// All elements: equipment + measurements + controllers
List<ProcessElementInterface> all = process.getAllElements();
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
// Adjust flow to match a target outlet pressure
Adjuster adj = new Adjuster("pressure adj");
adj.setAdjustedVariable(stream1, "flow", "MSm3/day");  // what to vary
adj.setTargetVariable(outletStream, "pressure", 50.0, "bara"); // target spec
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

## Field Development Economics (NPV / Cash Flow)

### Norwegian Petroleum Tax Model

```java
// Norwegian tax: corporate (22%) + special petroleum (56%) on INDEPENDENT bases
double corporateTaxRate = 0.22;
double specialTaxRate = 0.56;
double upliftRate = 0.055;  // 5.5% per year for 4 years
int upliftYears = 4;
int depreciationYears = 6;  // Straight-line

// Depreciation (straight-line over 6 years)
double annualDepreciation = (year >= 1 && year <= depreciationYears)
    ? totalCapex / depreciationYears : 0.0;

// Uplift (only for special petroleum tax base)
double uplift = (year >= 1 && year <= upliftYears)
    ? totalCapex * upliftRate : 0.0;

// INDEPENDENT taxable incomes (NOT cascaded)
double taxableIncomeCorp = revenue - opex - annualDepreciation - tariff;
double taxableIncomePetro = revenue - opex - annualDepreciation - tariff - uplift;

// Loss carry-forward per pool (no interest on carried losses)
double corpLoss = Math.max(0, -taxableIncomeCorp + carriedCorpLoss);
double petroLoss = Math.max(0, -taxableIncomePetro + carriedPetroLoss);

double corpTax = Math.max(0, taxableIncomeCorp - carriedCorpLoss) * corporateTaxRate;
double petroTax = Math.max(0, taxableIncomePetro - carriedPetroLoss) * specialTaxRate;
double totalTax = corpTax + petroTax;

// Cash flow: Year 0 = CAPEX only, revenue starts Year 1
double cashFlow = (year == 0) ? -totalCapex : revenue - opex - tariff - totalTax;
```

### Production Profile (Plateau + Decline)

```python
import numpy as np

def production_profile(reserves_Sm3, plateau_rate_Sm3_yr, decline_rate, years):
    """Generate plateau + exponential decline production profile."""
    production = np.zeros(years)
    cumulative = 0.0
    for yr in range(years):
        if cumulative < reserves_Sm3 * 0.3:  # plateau phase
            annual = min(plateau_rate_Sm3_yr, reserves_Sm3 - cumulative)
        else:  # decline phase
            annual = production[yr-1] * (1 - decline_rate) if yr > 0 else plateau_rate_Sm3_yr
        annual = min(annual, reserves_Sm3 - cumulative)
        production[yr] = max(0, annual)
        cumulative += production[yr]
    return production
```

### NPV Calculation

```python
def calculate_npv(cash_flows, discount_rate):
    """Calculate NPV from array of annual cash flows (year 0 at index 0)."""
    return sum(cf / (1 + discount_rate)**t for t, cf in enumerate(cash_flows))
```

### Using CashFlowEngine (Java)

```java
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.economics.CashFlowEngine.CashFlowResult;

// Create engine with Norwegian tax model ("NO")
CashFlowEngine engine = new CashFlowEngine("NO");

// Prices (units match your currency — e.g., NOK/Sm3 for Norwegian gas)
engine.setGasPrice(1.5);           // NOK/Sm3
engine.setGasTariff(0.015);        // NOK/Sm3
engine.setFixedOpexPerYear(200.0); // MNOK/year

// CAPEX schedule by calendar year
engine.addCapex(50.0, 2020);   // Pre-DG3
engine.addCapex(150.0, 2021);
engine.addCapex(4033.0, 2022); // DG3-DG4 construction
engine.addCapex(4033.0, 2023);
engine.addCapex(4033.0, 2024);

// Annual production (year, oilBbl, gasSm3, nglBbl)
engine.addAnnualProduction(2025, 0, 3.4e9, 0);
engine.addAnnualProduction(2026, 0, 3.4e9, 0);
engine.addAnnualProduction(2027, 0, 3.0e9, 0);
// ... more years

// Calculate
CashFlowResult result = engine.calculate(0.08); // 8% discount rate

double npv = result.getNpv();
double irr = result.getIrr();
double payback = result.getPaybackYears();
String summary = result.getSummary();
String table = result.toMarkdownTable();

// Breakeven analysis
double breakevenGasPrice = engine.calculateBreakevenGasPrice(0.08);
```

---

## SURF Cost Estimation (Subsea Field Development)

### Using SURFCostEstimator (Java)

```java
import neqsim.process.mechanicaldesign.subsea.SURFCostEstimator;
import neqsim.process.mechanicaldesign.subsea.SubseaCostEstimator;

// Constructor: (numberOfWells, waterDepthM, region)
SURFCostEstimator surf = new SURFCostEstimator(6, 300.0, SubseaCostEstimator.Region.NORWAY);

// S — Subsea infrastructure
surf.setTreePressureRatingPsi(10000.0);
surf.setTreeBoreSizeInches(5.0);
surf.setHorizontalTrees(true);
surf.setManifoldSlots(6);
surf.setManifoldWeightTonnes(140.0);
surf.setNumberOfPLETs(2);
surf.setNumberOfJumpers(6);

// U — Umbilicals
surf.setUmbilicalLengthKm(10.0);
surf.setUmbilicalDynamic(true);

// R — Risers
surf.setIncludeRisers(true);
surf.setFlexibleRiser(true);
surf.setRiserDiameterInches(8.0);
surf.setNumberOfProductionRisers(1);

// F — Flowlines
surf.setInfieldFlowlineLengthKm(10.0);
surf.setInfieldFlowlineDiameterInches(14.0);
surf.setExportPipelineLengthKm(80.0);
surf.setExportPipelineDiameterInches(24.0);
surf.setPipelineMaterialGrade("X65");
surf.setPipelineDesignPressureBar(165.0);
surf.setPipelineInstallMethod("S-lay");
surf.setContingencyPct(0.15);

double totalUSD = surf.calculate();

// Get cost breakdown
double subseaCost = surf.getSubseaCostUSD();
double umbilicalCost = surf.getUmbilicalCostUSD();
double riserCost = surf.getRiserCostUSD();
double flowlineCost = surf.getFlowlineCostUSD();
double totalNOK = surf.getTotalCostInCurrency(10.5); // exchange rate
String json = surf.toJson();
```

### Using SURFCostEstimator from Python Notebook

```python
import jpype

# Load from local build if not in pip package
try:
    SURFCostEstimator = jpype.JClass(
        "neqsim.process.mechanicaldesign.subsea.SURFCostEstimator")
    SubseaCostEstimator = jpype.JClass(
        "neqsim.process.mechanicaldesign.subsea.SubseaCostEstimator")
except Exception:
    import glob, pathlib
    jar = glob.glob(str(pathlib.Path("target") / "neqsim-*-shaded.jar"))
    if jar:
        jpype.addClassPath(jar[0])
    SURFCostEstimator = jpype.JClass(
        "neqsim.process.mechanicaldesign.subsea.SURFCostEstimator")
    SubseaCostEstimator = jpype.JClass(
        "neqsim.process.mechanicaldesign.subsea.SubseaCostEstimator")

surf = SURFCostEstimator(6, 300.0, SubseaCostEstimator.Region.NORWAY)
surf.setExportPipelineLengthKm(80.0)
surf.setExportPipelineDiameterInches(24.0)
surf.setNumberOfPLETs(2)
surf.setNumberOfJumpers(6)
surf.calculate()

total_usd = float(surf.getTotalSURFCostUSD())
total_nok = float(surf.getTotalCostInCurrency(10.5))
```

### Typical NCS SURF Cost Benchmarks

| Component | Typical Range (MUSD) | Notes |
|-----------|---------------------|-------|
| Subsea tree | 5-15 per tree | Vertical or horizontal |
| Manifold | 10-25 each | 4-slot or 8-slot |
| PLET | 2-5 each | Per flowline end |
| Jumper | 3-8 each | Rigid or flexible |
| Umbilical | 800-2,000/km | Static + dynamic |
| Riser | 15-50 each | Flexible; more for SCR |
| Flowline (rigid) | 1,500-5,000/m | Depends on diameter and wall thickness |
| Export pipeline | 1,500-4,000/m | Material + installation |
| SURF as % of total CAPEX | 40-60% | NCS subsea tieback |

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

### Benchmark Validation Notebook

Every task MUST include a separate benchmark notebook. Use this template:

```python
# Cell 1: Introduction (markdown)
# ## Benchmark Validation
# Compare NeqSim results against independent reference data to verify
# that the model and equations are producing trustworthy results.

# Cell 2: Reference data
import pandas as pd
import numpy as np

benchmark_data = pd.DataFrame({
    "Condition": ["25C, 1 bar", "25C, 50 bar", "25C, 100 bar", "50C, 100 bar"],
    "Source": ["NIST", "NIST", "NIST", "NIST"],
    "density_kg_m3": [0.656, 35.18, 77.50, 60.12],  # reference values
    "Cp_J_kgK": [2226, 2950, 4100, 3200],
})
print("Reference data from NIST Webbook (methane)")
benchmark_data

# Cell 3: NeqSim calculation at same conditions
from neqsim import jneqsim
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

def calc_properties(T_C, P_bar):
    fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + T_C, P_bar)
    fluid.addComponent("methane", 1.0)
    fluid.setMixingRule("classic")
    ops = ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()  # initializes thermo + transport properties
    return {
        "density_kg_m3": float(fluid.getDensity("kg/m3")),
        "Cp_J_kgK": float(fluid.getCp("J/kgK")),
    }

conditions = [(25, 1), (25, 50), (25, 100), (50, 100)]
neqsim_results = [calc_properties(T, P) for T, P in conditions]

# Cell 4: Comparison table
for i, res in enumerate(neqsim_results):
    benchmark_data.loc[i, "neqsim_density"] = res["density_kg_m3"]
    benchmark_data.loc[i, "density_dev_pct"] = abs(
        res["density_kg_m3"] - benchmark_data.loc[i, "density_kg_m3"]
    ) / benchmark_data.loc[i, "density_kg_m3"] * 100

print("\nComparison: Benchmark vs NeqSim")
benchmark_data[["Condition", "density_kg_m3", "neqsim_density", "density_dev_pct"]]

# Cell 5: Parity plot
import matplotlib.pyplot as plt

fig, axes = plt.subplots(1, 2, figsize=(12, 5))

# Parity plot
ax = axes[0]
ax.scatter(benchmark_data["density_kg_m3"], benchmark_data["neqsim_density"])
lims = [0, max(benchmark_data["density_kg_m3"].max(), benchmark_data["neqsim_density"].max()) * 1.1]
ax.plot(lims, lims, 'k--', label='Perfect agreement')
ax.set_xlabel("Benchmark density (kg/m\u00b3)")
ax.set_ylabel("NeqSim density (kg/m\u00b3)")
ax.set_title("Parity Plot: Density")
ax.legend(); ax.grid(True)

# Deviation bar chart
ax = axes[1]
ax.bar(benchmark_data["Condition"], benchmark_data["density_dev_pct"])
ax.axhline(y=5.0, color='r', linestyle='--', label='5% tolerance')
ax.set_ylabel("Deviation (%)")
ax.set_title("Deviation from NIST Benchmark")
ax.legend(); ax.grid(True, axis='y')

plt.tight_layout()
# fig.savefig(str(FIGURES_DIR / "benchmark_parity.png"), dpi=150, bbox_inches="tight")
plt.show()

# Cell 6: Save benchmark results to results.json
benchmark_validation = {
    "benchmark_source": "NIST Webbook",
    "comparisons": [
        {"parameter": "density_kg_m3",
         "benchmark": float(row["density_kg_m3"]),
         "neqsim": float(row["neqsim_density"]),
         "deviation_pct": round(float(row["density_dev_pct"]), 2),
         "condition": row["Condition"]}
        for _, row in benchmark_data.iterrows()
    ],
    "max_deviation_pct": round(float(benchmark_data["density_dev_pct"].max()), 2),
    "all_within_tolerance": bool(benchmark_data["density_dev_pct"].max() < 5.0),
    "tolerance_pct": 5.0,
}
# Add to results dict: results["benchmark_validation"] = benchmark_validation
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
