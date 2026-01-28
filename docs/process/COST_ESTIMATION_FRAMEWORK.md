# NeqSim Cost Estimation Framework

This document provides comprehensive documentation for the NeqSim cost estimation framework, which enables capital and operating cost estimation for process equipment and complete process systems.

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Core Classes](#core-classes)
4. [Equipment Cost Estimation](#equipment-cost-estimation)
   - [Separator Cost](#separator-cost)
   - [Compressor Cost](#compressor-cost)
   - [Heat Exchanger Cost](#heat-exchanger-cost)
   - [Pump Cost](#pump-cost)
   - [Valve Cost](#valve-cost)
   - [Tank Cost](#tank-cost)
   - [Expander Cost](#expander-cost)
   - [Mixer Cost](#mixer-cost)
   - [Splitter Cost](#splitter-cost)
   - [Ejector Cost](#ejector-cost)
   - [Absorber Cost](#absorber-cost)
   - [Column Cost](#column-cost)
   - [Pipe Cost](#pipe-cost)
5. [Process-Level Cost Estimation](#process-level-cost-estimation)
6. [Currency and Location Support](#currency-and-location-support)
7. [Operating Cost (OPEX) Estimation](#operating-cost-opex-estimation)
8. [Financial Metrics](#financial-metrics)
9. [Usage Examples](#usage-examples)
10. [Extending the Framework](#extending-the-framework)
11. [References](#references)

---

## Overview

The NeqSim cost estimation framework provides tools for estimating:

- **Capital Costs (CAPEX)**
  - Purchased Equipment Cost (PEC)
  - Bare Module Cost (BMC)
  - Total Module Cost (TMC)
  - Grass Roots Cost (GRC)

- **Operating Costs (OPEX)**
  - Utility costs (electricity, steam, cooling water)
  - Maintenance costs
  - Operating labor
  - Administrative overhead

- **Financial Metrics**
  - Payback period
  - Return on Investment (ROI)
  - Net Present Value (NPV)

The framework uses industry-standard correlations from:
- Turton et al. - "Analysis, Synthesis and Design of Chemical Processes"
- Peters & Timmerhaus - "Plant Design and Economics for Chemical Engineers"
- GPSA Engineering Data Book
- Various API standards

---

## Architecture

```
neqsim.process.costestimation/
├── CostEstimationCalculator.java      # Core calculation utilities
├── UnitCostEstimateBaseClass.java     # Base class for equipment costs
├── ProcessCostEstimate.java           # System-level cost aggregation
├── SystemMechanicalDesign.java        # Mechanical design aggregation
│
├── absorber/
│   └── AbsorberCostEstimate.java      # Absorber tower costs
├── column/
│   └── ColumnCostEstimate.java        # Distillation column costs
├── compressor/
│   └── CompressorCostEstimate.java    # Compressor costs
├── ejector/
│   └── EjectorCostEstimate.java       # Ejector/vacuum system costs
├── expander/
│   └── ExpanderCostEstimate.java      # Turboexpander costs
├── heatexchanger/
│   └── HeatExchangerCostEstimate.java # Heat exchanger costs
├── mixer/
│   └── MixerCostEstimate.java         # Mixer costs
├── pipe/
│   └── PipeCostEstimate.java          # Piping costs
├── pump/
│   └── PumpCostEstimate.java          # Pump costs
├── separator/
│   └── SeparatorCostEstimate.java     # Separator vessel costs
├── splitter/
│   └── SplitterCostEstimate.java      # Splitter/manifold costs
├── tank/
│   └── TankCostEstimate.java          # Storage tank costs
└── valve/
    └── ValveCostEstimate.java         # Control valve costs
```

---

## Core Classes

### CostEstimationCalculator

The central utility class providing cost calculation methods and constants.

```java
CostEstimationCalculator calc = new CostEstimationCalculator();

// Set CEPCI index (default: 816.0 for 2024)
calc.setCepci(816.0);

// Currency support
calc.setCurrencyCode("EUR");
double eurCost = calc.convertFromUSD(1000000.0);

// Location factors
calc.setLocationByRegion("North Sea");
double adjustedCost = baseCost * calc.getLocationFactor();
```

#### Available Currencies

| Code | Currency | Default Exchange Rate |
|------|----------|----------------------|
| USD | US Dollar | 1.00 |
| EUR | Euro | 0.92 |
| NOK | Norwegian Krone | 11.00 |
| GBP | British Pound | 0.79 |
| CNY | Chinese Yuan | 7.25 |
| JPY | Japanese Yen | 155.00 |

#### Location Factors

| Region | Factor | Notes |
|--------|--------|-------|
| US Gulf Coast | 1.00 | Base reference |
| North Sea / Norway | 1.35 | High labor costs |
| Western Europe | 1.20 | |
| Eastern Europe | 0.85 | |
| Middle East | 1.10 | |
| Asia Pacific | 0.90 | |
| China | 0.75 | Lower labor costs |
| India | 0.70 | |
| South America | 0.95 | |
| Africa | 1.05 | |
| Australia | 1.25 | Remote location premium |

### UnitCostEstimateBaseClass

Abstract base class for all equipment cost estimators.

**Key Methods:**
- `calculateCostEstimate()` - Calculates all cost metrics
- `getPurchasedEquipmentCost()` - Returns PEC
- `getBareModuleCost()` - Returns BMC
- `getTotalModuleCost()` - Returns TMC
- `getGrassRootsCost()` - Returns GRC
- `getInstallationManHours()` - Returns estimated installation hours
- `getCostBreakdown()` - Returns detailed cost breakdown map
- `toMap()` - Returns all data as a map for JSON export

---

## Equipment Cost Estimation

### Separator Cost

For vertical and horizontal separators, scrubbers, and slug catchers.

```java
Separator separator = new Separator("HP Separator", feed);
separator.run();
separator.initMechanicalDesign();

SeparatorCostEstimate costEst = new SeparatorCostEstimate(
    (SeparatorMechanicalDesign) separator.getMechanicalDesign());
costEst.calculateCostEstimate();

double pec = costEst.getPurchasedEquipmentCost();
```

**Supported Types:**
- Vertical vessels
- Horizontal vessels
- Three-phase separators

---

### Compressor Cost

For centrifugal and reciprocating compressors.

```java
CompressorCostEstimate costEst = new CompressorCostEstimate(compMecDesign);
costEst.setCompressorType("centrifugal"); // or "reciprocating"
costEst.setIncludeDriver(true);
costEst.setDriverType("electric-motor"); // or "gas-turbine", "steam-turbine"
costEst.calculateCostEstimate();
```

**Parameters:**
- Compressor type (centrifugal, reciprocating)
- Driver type and inclusion
- Spare parts package

---

### Heat Exchanger Cost

For shell-and-tube, plate, air-cooled, and other heat exchangers.

```java
HeatExchangerCostEstimate costEst = new HeatExchangerCostEstimate(hxMecDesign);
costEst.setHeatExchangerType("shell-tube"); // or "plate", "air-cooled"
costEst.setShellMaterial("carbon-steel");
costEst.setTubeMaterial("stainless-steel");
costEst.calculateCostEstimate();
```

---

### Pump Cost

For centrifugal and positive displacement pumps.

```java
PumpCostEstimate costEst = new PumpCostEstimate(pumpMecDesign);
costEst.setPumpType("centrifugal"); // or "reciprocating", "gear"
costEst.setIncludeDriver(true);
costEst.calculateCostEstimate();
```

---

### Valve Cost

For control valves, safety valves, and manual valves.

```java
ValveCostEstimate costEst = new ValveCostEstimate(valveMecDesign);
costEst.setValveType("globe"); // or "ball", "butterfly", "gate"
costEst.setActuatorType("pneumatic"); // or "electric", "hydraulic", "manual"
costEst.calculateCostEstimate();
```

---

### Tank Cost

For atmospheric and pressurized storage tanks per API 650/620.

```java
TankCostEstimate costEst = new TankCostEstimate(tankMecDesign);

// Tank configuration
costEst.setTankType("fixed-cone-roof"); // See table below
costEst.setTankVolume(5000.0);          // m³
costEst.setTankDiameter(20.0);          // m
costEst.setTankHeight(16.0);            // m
costEst.setDesignPressure(0.1);         // barg (for pressurized)

// Optional components
costEst.setIncludeFoundation(true);
costEst.setIncludeHeatingCoils(false);
costEst.setIncludeInsulation(true);
costEst.setInsulationThickness(75.0);   // mm

costEst.calculateCostEstimate();
Map<String, Object> breakdown = costEst.getCostBreakdown();
```

**Supported Tank Types:**

| Type | Description | Standards |
|------|-------------|-----------|
| `fixed-cone-roof` | Cone roof atmospheric tank | API 650 |
| `fixed-dome-roof` | Dome roof atmospheric tank | API 650 |
| `floating-roof` | External/internal floating roof | API 650 |
| `spherical` | Spherical pressure vessel | API 620 |
| `horizontal` | Horizontal cylindrical tank | API 620 |

---

### Expander Cost

For turboexpanders used in gas processing and cryogenic applications.

```java
ExpanderCostEstimate costEst = new ExpanderCostEstimate(expMecDesign);

// Expander configuration
costEst.setExpanderType("radial-inflow"); // or "axial", "mixed-flow"
costEst.setShaftPower(2000.0);            // kW (if no MechanicalDesign)
costEst.setCryogenicService(true);        // Below -40°C

// Load configuration
costEst.setLoadType("generator");         // or "compressor", "brake"
costEst.setIncludeLoad(true);

// Auxiliary systems
costEst.setIncludeGearbox(false);
costEst.setIncludeLubeOilSystem(true);
costEst.setIncludeControlSystem(true);

costEst.calculateCostEstimate();
```

**Cost Factors:**
- Cryogenic service: +40% for special materials
- Axial type: +20% vs radial-inflow
- Generator load: Based on power output

---

### Mixer Cost

For static mixers and inline mixing devices.

```java
MixerCostEstimate costEst = new MixerCostEstimate(null); // Can work standalone

costEst.setMixerType("static");        // or "inline", "tee", "vessel"
costEst.setPipeDiameter(8.0);          // inches
costEst.setNumberOfElements(12);       // For static mixers
costEst.setPressureClass(300);         // ASME pressure class
costEst.setFlangedConnections(true);

costEst.calculateCostEstimate();
```

**Mixer Types:**

| Type | Description | Typical Use |
|------|-------------|-------------|
| `static` | Static mixing elements | Chemical injection |
| `inline` | Motorized inline mixer | High-shear mixing |
| `tee` | Simple mixing tee | Low-intensity mixing |
| `vessel` | Agitated mixing vessel | Batch operations |

---

### Splitter Cost

For flow distribution manifolds and headers.

```java
SplitterCostEstimate costEst = new SplitterCostEstimate(null);

costEst.setSplitterType("manifold");   // or "header", "tee", "vessel"
costEst.setNumberOfOutlets(4);
costEst.setInletDiameter(10.0);        // inches
costEst.setOutletDiameter(6.0);        // inches
costEst.setPressureClass(600);         // ASME class

// Optional control equipment
costEst.setIncludeControlValves(true);
costEst.setIncludeFlowMeters(false);

costEst.calculateCostEstimate();
```

---

### Ejector Cost

For steam ejectors, gas ejectors, and vacuum systems.

```java
EjectorCostEstimate costEst = new EjectorCostEstimate(null);

costEst.setEjectorType("steam");       // or "gas", "liquid", "hybrid"
costEst.setNumberOfStages(2);
costEst.setSuctionPressure(50.0);      // mbar abs
costEst.setDischargePressure(1.013);   // bara
costEst.setSuctionCapacity(500.0);     // kg/hr
costEst.setMotivePressure(10.0);       // bara (steam/gas pressure)

// Condensers
costEst.setIncludeIntercondensers(true);
costEst.setIncludeAftercondenser(true);

costEst.calculateCostEstimate();
```

**Ejector Types:**

| Type | Description | Motive Fluid |
|------|-------------|--------------|
| `steam` | Steam jet ejector | Steam |
| `gas` | Gas jet ejector | Process gas |
| `liquid` | Liquid jet ejector | Water/liquid |
| `hybrid` | Ejector + liquid ring pump | Combined |

---

### Absorber Cost

For gas absorption towers (TEG contactors, amine columns, etc.).

```java
AbsorberCostEstimate costEst = new AbsorberCostEstimate(null);

// Column configuration
costEst.setAbsorberType("packed");     // or "trayed", "spray"
costEst.setColumnDiameter(2.0);        // m
costEst.setColumnHeight(15.0);         // m
costEst.setDesignPressure(60.0);       // barg

// For packed columns
costEst.setPackingType("structured");  // or "random"
costEst.setPackingHeight(10.0);        // m

// For trayed columns
costEst.setTrayType("valve");          // or "sieve", "bubble-cap"
costEst.setNumberOfStages(15);

// Internals
costEst.setIncludeLiquidDistributor(true);
costEst.setIncludeMistEliminator(true);

// Auxiliaries
costEst.setIncludeReboiler(false);
costEst.setIncludeRefluxSystem(false);

costEst.calculateCostEstimate();
```

---

### Column Cost

For distillation and fractionation columns.

```java
ColumnCostEstimate costEst = new ColumnCostEstimate(columnMecDesign);
costEst.setColumnType("trayed");
costEst.setNumberOfTrays(40);
costEst.setTrayType("valve");
costEst.calculateCostEstimate();
```

---

### Pipe Cost

For process piping systems.

```java
PipeCostEstimate costEst = new PipeCostEstimate(pipeMecDesign);
costEst.setPipeLength(100.0);          // m
costEst.setPipeDiameter(8.0);          // inches
costEst.setSchedule("40");
costEst.setMaterial("carbon-steel");
costEst.calculateCostEstimate();
```

---

## Process-Level Cost Estimation

The `ProcessCostEstimate` class aggregates costs across an entire process system.

```java
// Create and run process
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);
process.add(cooler);
process.run();

// Calculate costs
ProcessCostEstimate processCost = new ProcessCostEstimate(process);
processCost.setLocationFactor(1.35);     // North Sea
processCost.setComplexityFactor(1.1);    // Complex process
processCost.calculateAllCosts();

// Get results
double pec = processCost.getPurchasedEquipmentCost();
double bmc = processCost.getBareModuleCost();
double tmc = processCost.getTotalModuleCost();
double grc = processCost.getGrassRootsCost();

// Print summary report
processCost.printCostSummary();
```

### Cost Breakdown by Category

```java
Map<String, Double> byType = processCost.getCostByEquipmentType();
// Returns: {Vessels: 321414, Compressors: 168940, ...}

Map<String, Double> byDiscipline = processCost.getCostByDiscipline();
// Returns: {Process Equipment: 544382, Piping & Valves: 1867911, ...}
```

---

## Currency and Location Support

### Setting Currency

```java
ProcessCostEstimate processCost = new ProcessCostEstimate(process);
processCost.setCurrency("NOK");  // Norwegian Krone
processCost.calculateAllCosts();

// Get costs in selected currency
Map<String, Double> costs = processCost.getCostsInCurrency();
```

### Setting Location

```java
processCost.setLocationByRegion("North Sea");
// Automatically sets location factor to 1.35

// Or set directly
processCost.setLocationFactor(1.40);
```

### Custom Exchange Rates

```java
CostEstimationCalculator calc = new CostEstimationCalculator();
calc.setCurrencyCode("EUR");
calc.setExchangeRate(0.95);  // Override default
```

---

## Operating Cost (OPEX) Estimation

The framework calculates annual operating costs based on utility consumption and industry factors.

```java
ProcessCostEstimate processCost = new ProcessCostEstimate(process);
processCost.calculateAllCosts();

// Calculate OPEX (8000 operating hours/year typical)
double annualOpex = processCost.calculateOperatingCost(8000);

// Get breakdown
Map<String, Double> opexBreakdown = processCost.getOperatingCostBreakdown();
// Returns:
// - Electricity: based on power consumption
// - Steam: based on heating duty
// - Cooling Water: based on cooling duty
// - Maintenance: 3-5% of CAPEX
// - Operating Labor: industry factors
// - Administrative Overhead: 25% of labor
```

### Default Utility Prices

| Utility | Default Price | Unit |
|---------|---------------|------|
| Electricity | 0.08 | USD/kWh |
| Steam (LP) | 15.0 | USD/ton |
| Cooling Water | 0.05 | USD/m³ |

---

## Financial Metrics

Calculate key financial metrics for project evaluation:

```java
ProcessCostEstimate processCost = new ProcessCostEstimate(process);
processCost.calculateAllCosts();
processCost.calculateOperatingCost(8000);

double capex = processCost.getGrassRootsCost();
double annualRevenue = 10000000.0;  // USD/year

// Payback Period
double payback = processCost.calculatePaybackPeriod(annualRevenue);
// Returns years to recover investment

// Return on Investment
double roi = processCost.calculateROI(annualRevenue);
// Returns (Revenue - OPEX) / CAPEX as percentage

// Net Present Value
double discountRate = 0.10;  // 10%
int projectLife = 20;        // years
double npv = processCost.calculateNPV(annualRevenue, discountRate, projectLife);
```

---

## Usage Examples

### Example 1: Simple Equipment Cost

```java
// Create and size a separator
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(10000, "kg/hr");
feed.run();

Separator sep = new Separator("HP Separator", feed);
sep.run();
sep.initMechanicalDesign();

// Estimate cost
SeparatorCostEstimate costEst = new SeparatorCostEstimate(
    (SeparatorMechanicalDesign) sep.getMechanicalDesign());
costEst.calculateCostEstimate();

System.out.println("PEC: $" + String.format("%,.0f", costEst.getPurchasedEquipmentCost()));
System.out.println("Grass Roots: $" + String.format("%,.0f", costEst.getGrassRootsCost()));
```

### Example 2: Complete Process Costing

```java
// Build process
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);
process.add(cooler);
process.run();

// Cost estimation with North Sea location
ProcessCostEstimate processCost = new ProcessCostEstimate(process);
processCost.setLocationByRegion("North Sea");
processCost.setCurrency("NOK");
processCost.calculateAllCosts();

// Calculate OPEX
double opex = processCost.calculateOperatingCost(8000);

// Financial analysis
double revenue = 50000000.0;  // NOK/year
double payback = processCost.calculatePaybackPeriod(revenue);
double npv = processCost.calculateNPV(revenue, 0.08, 25);

// Print detailed report
processCost.printCostSummary();
```

### Example 3: Standalone Equipment Costing

Some cost estimators can work without mechanical design for quick estimates:

```java
// Tank cost without process simulation
TankCostEstimate tankCost = new TankCostEstimate(null);
tankCost.setTankType("floating-roof");
tankCost.setTankVolume(50000.0);  // 50,000 m³
tankCost.setIncludeFoundation(true);
tankCost.calculateCostEstimate();

System.out.println("Tank Cost: $" + 
    String.format("%,.0f", tankCost.getPurchasedEquipmentCost()));
```

### Example 4: Export to JSON

```java
ProcessCostEstimate processCost = new ProcessCostEstimate(process);
processCost.calculateAllCosts();

// Get as JSON string
String json = processCost.toJson();

// Or get as Map for custom serialization
Map<String, Object> data = processCost.toMap();
```

---

## Extending the Framework

### Adding New Equipment Types

1. Create a new package under `neqsim.process.costestimation`
2. Create the cost estimate class extending `UnitCostEstimateBaseClass`
3. Implement required methods

```java
package neqsim.process.costestimation.myequipment;

public class MyEquipmentCostEstimate extends UnitCostEstimateBaseClass {
    
    public MyEquipmentCostEstimate(MechanicalDesign mechanicalEquipment) {
        super(mechanicalEquipment);
        setEquipmentType("myequipment");
    }
    
    @Override
    protected double calcPurchasedEquipmentCost() {
        // Implement cost correlation
        double size = getEquipmentSize();
        double baseCost = correlationFunction(size);
        return baseCost * getMaterialFactor() * 
               (getCostCalculator().getCurrentCepci() / 607.5);
    }
    
    @Override
    public Map<String, Object> getCostBreakdown() {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("equipmentType", getEquipmentType());
        breakdown.put("size", getEquipmentSize());
        breakdown.put("purchasedCost", getPurchasedEquipmentCost());
        return breakdown;
    }
}
```

### Adding New Currency

```java
// In CostEstimationCalculator or your code
public static final String CURRENCY_CHF = "CHF";  // Swiss Franc

// Add to getDefaultExchangeRates()
rates.put("CHF", 0.88);  // 1 USD = 0.88 CHF
```

### Adding New Location Factor

```java
// In CostEstimationCalculator
locationFactors.put("Arctic / Remote", 1.50);
```

---

## References

### Cost Correlations

1. Turton, R., et al. "Analysis, Synthesis and Design of Chemical Processes" 5th Ed.
2. Peters, M.S. & Timmerhaus, K.D. "Plant Design and Economics for Chemical Engineers" 5th Ed.
3. Couper, J.R. "Chemical Process Equipment: Selection and Design" 3rd Ed.
4. GPSA Engineering Data Book, 14th Edition

### Industry Standards

- API 650 - Welded Tanks for Oil Storage
- API 620 - Design and Construction of Large, Welded, Low-Pressure Storage Tanks
- API 617 - Axial and Centrifugal Compressors and Expander-compressors
- ASME B16.5 - Pipe Flanges and Flanged Fittings
- HEI Standards for Steam Jet Vacuum Systems

### Cost Indices

- Chemical Engineering Plant Cost Index (CEPCI)
  - 2024 average: ~816
  - 2001 base: 397
  - Update via `CostEstimationCalculator.setCepci(value)`

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | Jan 2026 | Initial framework with basic equipment |
| 1.1 | Jan 2026 | Added Tank, Expander, Mixer, Splitter, Ejector, Absorber |
| 1.2 | Jan 2026 | Added currency conversion and location factors |
| 1.3 | Jan 2026 | Added OPEX calculation and financial metrics |

---

*Document last updated: January 2026*
