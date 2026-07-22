---
title: "Process Engineering Utilities Reference"
description: "Reference guide for NeqSim process engineering utilities: pinch analysis, DCF economics, debottlenecking, fired heater, process validation, and cooling water system design."
---

# Process Engineering Utilities

Six utilities for process design, optimization, and analysis tasks.

## 1. PinchAnalyzer — Heat Integration

**Package:** `neqsim.process.util.heatintegration`

Performs pinch analysis on a process system: identifies hot/cold streams, builds composite curves, finds the pinch point, calculates minimum utility duties, and suggests heat exchanger matches.

### Usage

```java
PinchAnalyzer analyzer = new PinchAnalyzer(process);
analyzer.setMinApproachTemperature(10.0); // K

// Option A: auto-extract from process heaters/coolers
analyzer.analyze();

// Option B: manual stream specification
analyzer.addHotStream("Hot1", 473.15, 323.15, 150000.0); // name, Tsupply, Ttarget, duty(W)
analyzer.addColdStream("Cold1", 293.15, 393.15, 100000.0);
analyzer.analyze();

// Results
double pinchT = analyzer.getPinchTemperature();        // K
double minHot = analyzer.getMinHotUtilityDuty();       // W
double minCold = analyzer.getMinColdUtilityDuty();     // W
double recovery = analyzer.getEnergyRecoveryFraction(); // 0.0-1.0
List<PinchAnalyzer.HeatExchangerMatch> matches = analyzer.getMatches();

// Composite curves for plotting
List<double[]> hotCurve = analyzer.getHotCompositeCurve();   // [Q, T] pairs
List<double[]> coldCurve = analyzer.getColdCompositeCurve();
List<double[]> grandCurve = analyzer.getGrandCompositeCurve();

String json = analyzer.toJson();
```

### Python (Jupyter)

```python
PinchAnalyzer = jneqsim.process.util.heatintegration.PinchAnalyzer
analyzer = PinchAnalyzer(process)
analyzer.setMinApproachTemperature(10.0)
analyzer.analyze()
print(f"Pinch: {analyzer.getPinchTemperature() - 273.15:.1f} C")
print(f"Min hot utility: {analyzer.getMinHotUtilityDuty()/1000:.0f} kW")
```

---

## 2. DCFCalculator — Field Development Economics

**Package:** `neqsim.process.util.fielddevelopment`

NPV, IRR, payback period, and profitability index for field development projects. Supports CAPEX scheduling, production profiles, OPEX, tax, royalty, depreciation, and loss carry-forward.

### Usage

```java
DCFCalculator dcf = new DCFCalculator();
dcf.setDiscountRate(0.08);          // 8%
dcf.setProjectLifeYears(20);
dcf.setTaxRate(0.22);              // 22%
dcf.setRoyaltyRate(0.0);
dcf.setDepreciationYears(6);       // Straight-line
dcf.setInflationRate(0.02);        // 2% OPEX escalation

dcf.addCapex(0, 500e6);            // Year 0
dcf.addCapex(1, 300e6);            // Year 1

double[] production = new double[20];
for (int i = 2; i < 20; i++) production[i] = 10e6; // Sm3/yr
dcf.setAnnualProduction(production);
dcf.setProductPrice(1.5);          // NOK/Sm3
dcf.setAnnualOpex(50e6);           // NOK/yr

dcf.calculate();

double npv = dcf.getNPV();                    // Currency units
double irr = dcf.getIRR();                    // Fraction (e.g. 0.15)
int payback = dcf.getPaybackYear();            // Year index
double pi = dcf.getProfitabilityIndex();       // Benefit-cost ratio
double[] cashFlow = dcf.getAnnualCashFlow();
double[] discounted = dcf.getDiscountedCashFlow();
String json = dcf.toJson();
```

### Python (Jupyter)

```python
DCFCalculator = jneqsim.process.util.fielddevelopment.DCFCalculator
dcf = DCFCalculator()
dcf.setDiscountRate(0.08)
dcf.setProjectLifeYears(20)
dcf.setTaxRate(0.22)
dcf.addCapex(0, 500e6)
# ... set production, price, opex
dcf.calculate()
print(f"NPV: {dcf.getNPV()/1e6:.0f} MNOK, IRR: {dcf.getIRR()*100:.1f}%")
```

---

## 3. DebottleneckAnalyzer — Bottleneck Identification

**Package:** `neqsim.process.util.optimizer`

Scans all equipment with capacity constraints, ranks them by utilization, and provides debottleneck suggestions. Builds on the capacity constraint framework in `neqsim.process.equipment.capacity`.

### Usage

```java
DebottleneckAnalyzer analyzer = new DebottleneckAnalyzer(process);
analyzer.setWarningThreshold(0.85);   // 85%
analyzer.setCriticalThreshold(0.95);  // 95%
analyzer.analyze();

String primary = analyzer.getPrimaryBottleneck();         // Equipment name
double util = analyzer.getOverallUtilization();           // 0.0-1.0+
int overloaded = analyzer.getOverloadedCount();

List<DebottleneckAnalyzer.EquipmentStatus> ranked = analyzer.getRankedEquipment();
for (DebottleneckAnalyzer.EquipmentStatus es : ranked) {
    System.out.printf("%s [%s]: %.0f%% - %s - %s%n",
        es.name, es.type, es.maxUtilization * 100,
        es.status, es.suggestion);
}

List<DebottleneckAnalyzer.EquipmentStatus> constrained = analyzer.getConstrainedEquipment();
String json = analyzer.toJson();
```

---

## 4. FiredHeater — Duty-Controlled Heater with Efficiency

**Package:** `neqsim.process.equipment.heatexchanger`

Process heater that models thermal efficiency, fuel consumption, stack losses, and emissions (CO2, NOx). Extends `Heater` — set the desired outlet temperature and the heater calculates the required fired duty.

### Usage

```java
FiredHeater heater = new FiredHeater("Crude Heater", feedStream);
heater.setOutTemperature(273.15 + 350.0);    // K
heater.setThermalEfficiency(0.85);            // 85%
heater.setFuelLHV(48.0e6);                   // J/kg (natural gas)
heater.setFuelCO2Factor(2.75);               // kg CO2 per kg fuel
heater.setNoxFactor(0.08);                   // kg NOx per GJ
heater.setStackTemperature(273.15 + 150.0);  // K

process.add(heater);
process.run();

double absorbedDuty = heater.getAbsorbedDuty("kW");  // Heat to process
double firedDuty = heater.getFiredDuty("kW");         // Total fuel energy
double stackLoss = heater.getStackLoss("kW");         // Heat lost
double fuel = heater.getFuelConsumption("kg/hr");
double co2 = heater.getCO2Emissions("kg/hr");
double co2Annual = heater.getCO2Emissions("tonnes/hr") * 8760;
double nox = heater.getNOxEmissions("kg/hr");
String json = heater.toJson();
```

---

## 5. ProcessValidator — Automated Process Check

**Package:** `neqsim.process.util.report`

Validates a process system for mass balance closure, extreme temperatures/pressures, negative flows, and zero-flow streams. Run after `process.run()` to catch simulation errors.

### Usage

```java
ProcessValidator validator = new ProcessValidator(process);
validator.setMassBalanceTolerance(0.001);          // 0.1%
validator.setTemperatureLimits(150.0, 1000.0);     // K
validator.setPressureLimits(1.0, 500.0);            // bara
validator.validate();

boolean passed = validator.isValid();               // No ERROR-level issues
int errors = validator.getErrorCount();
int warnings = validator.getWarningCount();

List<ProcessValidator.ValidationIssue> issues = validator.getIssues();
for (ProcessValidator.ValidationIssue issue : issues) {
    System.out.printf("[%s] %s: %s (%.2f)%n",
        issue.severity, issue.location, issue.message, issue.value);
}

List<ProcessValidator.ValidationIssue> errorsOnly = validator.getErrors();
String json = validator.toJson();
```

---

## 6. CoolingWaterSystem — Utility System Design

**Package:** `neqsim.process.equipment.heatexchanger`

Sizes the cooling water circulation system from process cooling requirements. Calculates CW flow rate, pump power, cooling tower fan power, and annual operating cost.

### Usage

```java
CoolingWaterSystem cws = new CoolingWaterSystem();
cws.addCoolingRequirement("After-Cooler", 5000.0, 40.0, 10.0); // name, kW, outC, approachC
cws.addCoolingRequirement("Condenser", 3000.0, 55.0, 15.0);
cws.setCoolingWaterSupplyTemperature(25.0);   // C
cws.setCoolingWaterReturnTemperature(35.0);   // C
cws.setSystemPressureDrop(3.0);              // bar
cws.setPumpEfficiency(0.75);
cws.setElectricityCost(0.10);               // $/kWh
cws.setAnnualOperatingHours(8000.0);
cws.calculate();

double cwFlow = cws.getTotalCWFlowRate();          // m3/hr
double pumpPower = cws.getPumpPower();              // kW
double fanPower = cws.getTowerFanPower();           // kW
double totalPower = cws.getTotalElectricalPower();  // kW
double annualCost = cws.getAnnualOperatingCost();   // $
String json = cws.toJson();
```

---

## Summary Table

| Utility | Package | Purpose |
|---------|---------|---------|
| `PinchAnalyzer` | `process.util.heatintegration` | Heat integration / pinch analysis |
| `DCFCalculator` | `process.util.fielddevelopment` | NPV, IRR, payback, cash flow |
| `DebottleneckAnalyzer` | `process.util.optimizer` | Equipment bottleneck ranking |
| `FiredHeater` | `process.equipment.heatexchanger` | Duty-controlled heater with efficiency |
| `ProcessValidator` | `process.util.report` | Mass/energy balance and limit checks |
| `CoolingWaterSystem` | `process.equipment.heatexchanger` | CW system sizing and cost |
