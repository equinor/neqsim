# Cost Estimation API Reference

This document provides detailed API reference for all cost estimation classes in NeqSim.

## Table of Contents

1. [Core Classes](#core-classes)
2. [Equipment Cost Estimators](#equipment-cost-estimators)
3. [Enumerations and Constants](#enumerations-and-constants)
4. [Data Structures](#data-structures)

---

## Core Classes

### CostEstimationCalculator

Central utility class for cost calculations.

**Package:** `neqsim.process.costestimation`

#### Constants

```java
// Currency codes
public static final String CURRENCY_USD = "USD";
public static final String CURRENCY_EUR = "EUR";
public static final String CURRENCY_NOK = "NOK";
public static final String CURRENCY_GBP = "GBP";
public static final String CURRENCY_CNY = "CNY";
public static final String CURRENCY_JPY = "JPY";

// Location factor codes
public static final String LOC_US_GULF = "US Gulf Coast";
public static final String LOC_NORTH_SEA = "North Sea / Norway";
public static final String LOC_WESTERN_EUROPE = "Western Europe";
public static final String LOC_EASTERN_EUROPE = "Eastern Europe";
public static final String LOC_MIDDLE_EAST = "Middle East";
public static final String LOC_ASIA_PACIFIC = "Asia Pacific";
public static final String LOC_CHINA = "China";
public static final String LOC_INDIA = "India";
public static final String LOC_SOUTH_AMERICA = "South America";
public static final String LOC_AFRICA = "Africa";
public static final String LOC_AUSTRALIA = "Australia";
```

#### Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `setCepci(double value)` | void | Set Chemical Engineering Plant Cost Index |
| `getCurrentCepci()` | double | Get current CEPCI value |
| `setCurrencyCode(String code)` | void | Set output currency |
| `getCurrencyCode()` | String | Get current currency code |
| `setExchangeRate(double rate)` | void | Override exchange rate |
| `getExchangeRate()` | double | Get current exchange rate |
| `convertFromUSD(double usdAmount)` | double | Convert USD to current currency |
| `convertToUSD(double localAmount)` | double | Convert current currency to USD |
| `setLocationByRegion(String region)` | void | Set location factor by region name |
| `setLocationFactor(double factor)` | void | Set location factor directly |
| `getLocationFactor()` | double | Get current location factor |
| `formatCost(double cost)` | String | Format cost with currency symbol |
| `getAvailableLocationFactors()` | Map<String, Double> | Get all available location factors |
| `getDefaultExchangeRates()` | Map<String, Double> | Get default exchange rates |

#### Static Calculation Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `calcBareModuleCost(double pec, double pressure)` | double | Calculate bare module cost |
| `calcTotalModuleCost(double bmc)` | double | Calculate total module cost |
| `calcGrassRootsCost(double tmc)` | double | Calculate grass roots cost |
| `calcVerticalVesselCost(double volume)` | double | Vessel cost correlation |
| `calcHorizontalVesselCost(double volume)` | double | Horizontal vessel cost |
| `calcShellTubeHxCost(double area)` | double | Shell & tube HX cost |
| `calcPlateFinnedHxCost(double area)` | double | Plate-fin HX cost |
| `calcAirCoolerCost(double area)` | double | Air cooler cost |
| `calcCentrifugalCompressorCost(double power)` | double | Centrifugal compressor cost |
| `calcReciprocatingCompressorCost(double power)` | double | Reciprocating compressor cost |
| `calcCentrifugalPumpCost(double power)` | double | Centrifugal pump cost |
| `calcControlValveCost(double cv)` | double | Control valve cost |
| `calcPipingCost(double diameter, double length, int schedule)` | double | Piping cost |

---

### UnitCostEstimateBaseClass

Abstract base class for all equipment cost estimators.

**Package:** `neqsim.process.costestimation`

#### Constructor

```java
public UnitCostEstimateBaseClass(MechanicalDesign mechanicalEquipment)
```

#### Core Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `calculateCostEstimate()` | void | Calculate all cost metrics |
| `getPurchasedEquipmentCost()` | double | Get purchased equipment cost |
| `getBareModuleCost()` | double | Get bare module cost |
| `getTotalModuleCost()` | double | Get total module cost |
| `getGrassRootsCost()` | double | Get grass roots cost |
| `getInstallationManHours()` | double | Get installation hours |

#### Configuration Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `setEquipmentType(String type)` | void | Set equipment type identifier |
| `getEquipmentType()` | String | Get equipment type |
| `setMaterial(String material)` | void | Set construction material |
| `getMaterial()` | String | Get material |
| `setMaterialFactor(double factor)` | void | Override material factor |
| `getMaterialFactor()` | double | Get material factor |
| `setDesignPressure(double pressure)` | void | Set design pressure (barg) |
| `getDesignPressure()` | double | Get design pressure |

#### Output Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getCostBreakdown()` | Map<String, Object> | Get detailed cost breakdown |
| `toMap()` | Map<String, Object> | Get all data as map |
| `toJson()` | String | Get JSON representation |
| `getCostCalculator()` | CostEstimationCalculator | Get calculator instance |

#### Protected Methods (for subclasses)

| Method | Return Type | Description |
|--------|-------------|-------------|
| `calcPurchasedEquipmentCost()` | double | **Abstract** - implement cost correlation |
| `calcInstallationManHours()` | double | Calculate installation hours |

---

### ProcessCostEstimate

System-level cost aggregation for complete process systems.

**Package:** `neqsim.process.costestimation`

#### Constructors

```java
public ProcessCostEstimate()
public ProcessCostEstimate(ProcessSystem process)
```

#### Configuration Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `setProcessSystem(ProcessSystem process)` | void | Set process system |
| `setLocationFactor(double factor)` | void | Set location factor |
| `getLocationFactor()` | double | Get location factor |
| `setLocationByRegion(String region)` | void | Set location by region name |
| `setComplexityFactor(double factor)` | void | Set complexity factor |
| `getComplexityFactor()` | double | Get complexity factor |
| `setCurrency(String code)` | void | Set output currency |
| `getCurrencyCode()` | String | Get currency code |

#### Calculation Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `calculateAllCosts()` | void | Calculate all equipment costs |
| `getPurchasedEquipmentCost()` | double | Get total PEC |
| `getBareModuleCost()` | double | Get total BMC |
| `getTotalModuleCost()` | double | Get total TMC |
| `getGrassRootsCost()` | double | Get total GRC |
| `getTotalInstallationManHours()` | double | Get total installation hours |

#### Cost Breakdown Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getCostByEquipmentType()` | Map<String, Double> | Cost by equipment type |
| `getCostByDiscipline()` | Map<String, Double> | Cost by discipline |
| `getEquipmentCostList()` | List<Map<String, Object>> | Detailed equipment list |
| `getCostsInCurrency()` | Map<String, Double> | All costs in selected currency |

#### OPEX Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `calculateOperatingCost(int hours)` | double | Calculate annual OPEX |
| `getTotalAnnualOperatingCost()` | double | Get calculated OPEX |
| `getOperatingCostBreakdown()` | Map<String, Double> | Get OPEX breakdown |
| `setElectricityPrice(double price)` | void | Set $/kWh |
| `setSteamPrice(double price)` | void | Set $/ton |
| `setCoolingWaterPrice(double price)` | void | Set $/m³ |

#### Financial Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `calculatePaybackPeriod(double annualRevenue)` | double | Calculate payback years |
| `calculateROI(double annualRevenue)` | double | Calculate ROI percentage |
| `calculateNPV(double revenue, double rate, int years)` | double | Calculate NPV |

#### Output Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `printCostSummary()` | void | Print formatted report |
| `toMap()` | Map<String, Object> | Get all data as map |
| `toJson()` | String | Get JSON representation |

---

## Equipment Cost Estimators

### TankCostEstimate

Storage tank cost estimation per API 650/620.

**Package:** `neqsim.process.costestimation.tank`

#### Constructor

```java
public TankCostEstimate(TankMechanicalDesign mechanicalEquipment)
```

#### Tank Configuration

| Method | Parameters | Description |
|--------|------------|-------------|
| `setTankType(String type)` | "fixed-cone-roof", "fixed-dome-roof", "floating-roof", "spherical", "horizontal" | Set tank type |
| `setTankVolume(double volume)` | m³ | Set tank volume |
| `setTankDiameter(double diameter)` | m | Set tank diameter |
| `setTankHeight(double height)` | m | Set tank height |
| `setDesignPressure(double pressure)` | barg | Set design pressure |
| `setFloatingRoof(boolean floating)` | true/false | Enable floating roof |

#### Optional Components

| Method | Parameters | Description |
|--------|------------|-------------|
| `setIncludeFoundation(boolean include)` | true/false | Include foundation cost |
| `setIncludeHeatingCoils(boolean include)` | true/false | Include heating coils |
| `setIncludeInsulation(boolean include)` | true/false | Include insulation |
| `setInsulationThickness(double thickness)` | mm | Set insulation thickness |

---

### ExpanderCostEstimate

Turboexpander cost estimation.

**Package:** `neqsim.process.costestimation.expander`

#### Constructor

```java
public ExpanderCostEstimate(ExpanderMechanicalDesign mechanicalEquipment)
```

#### Expander Configuration

| Method | Parameters | Description |
|--------|------------|-------------|
| `setExpanderType(String type)` | "radial-inflow", "axial", "mixed-flow" | Set expander type |
| `setShaftPower(double power)` | kW | Set shaft power (standalone mode) |
| `setCryogenicService(boolean cryo)` | true/false | Enable cryogenic factors |
| `setInletTemperature(double temp)` | K | Set inlet temperature |

#### Load Configuration

| Method | Parameters | Description |
|--------|------------|-------------|
| `setLoadType(String type)` | "generator", "compressor", "brake" | Set load type |
| `setIncludeLoad(boolean include)` | true/false | Include load cost |
| `setIncludeGearbox(boolean include)` | true/false | Include gearbox |
| `setIncludeLubeOilSystem(boolean include)` | true/false | Include lube oil system |
| `setIncludeControlSystem(boolean include)` | true/false | Include control system |

---

### MixerCostEstimate

Static mixer and inline mixer cost estimation.

**Package:** `neqsim.process.costestimation.mixer`

#### Constructor

```java
public MixerCostEstimate(MechanicalDesign mechanicalEquipment)
```

#### Configuration

| Method | Parameters | Description |
|--------|------------|-------------|
| `setMixerType(String type)` | "static", "inline", "tee", "vessel" | Set mixer type |
| `setPipeDiameter(double diameter)` | inches | Set pipe diameter |
| `setNumberOfElements(int count)` | integer | Set mixing elements (static) |
| `setPressureClass(int class)` | 150, 300, 600, 900, 1500, 2500 | Set ASME class |
| `setFlangedConnections(boolean flanged)` | true/false | Use flanged connections |

---

### SplitterCostEstimate

Flow splitter and manifold cost estimation.

**Package:** `neqsim.process.costestimation.splitter`

#### Constructor

```java
public SplitterCostEstimate(MechanicalDesign mechanicalEquipment)
```

#### Configuration

| Method | Parameters | Description |
|--------|------------|-------------|
| `setSplitterType(String type)` | "manifold", "header", "tee", "vessel" | Set splitter type |
| `setNumberOfOutlets(int count)` | integer | Set number of outlets |
| `setInletDiameter(double diameter)` | inches | Set inlet diameter |
| `setOutletDiameter(double diameter)` | inches | Set outlet diameter |
| `setPressureClass(int class)` | ASME class | Set pressure class |
| `setIncludeControlValves(boolean include)` | true/false | Include control valves |
| `setIncludeFlowMeters(boolean include)` | true/false | Include flow meters |

---

### EjectorCostEstimate

Ejector and vacuum system cost estimation.

**Package:** `neqsim.process.costestimation.ejector`

#### Constructor

```java
public EjectorCostEstimate(EjectorMechanicalDesign mechanicalEquipment)
```

#### Configuration

| Method | Parameters | Description |
|--------|------------|-------------|
| `setEjectorType(String type)` | "steam", "gas", "liquid", "hybrid" | Set ejector type |
| `setNumberOfStages(int stages)` | integer | Set number of stages |
| `setSuctionPressure(double pressure)` | mbar abs | Set suction pressure |
| `setDischargePressure(double pressure)` | bara | Set discharge pressure |
| `setSuctionCapacity(double capacity)` | kg/hr | Set suction capacity |
| `setMotivePressure(double pressure)` | bara | Set motive fluid pressure |
| `setIncludeIntercondensers(boolean include)` | true/false | Include intercondensers |
| `setIncludeAftercondenser(boolean include)` | true/false | Include aftercondenser |

---

### AbsorberCostEstimate

Gas absorption tower cost estimation.

**Package:** `neqsim.process.costestimation.absorber`

#### Constructor

```java
public AbsorberCostEstimate(AbsorberMechanicalDesign mechanicalEquipment)
```

#### Column Configuration

| Method | Parameters | Description |
|--------|------------|-------------|
| `setAbsorberType(String type)` | "packed", "trayed", "spray" | Set absorber type |
| `setColumnDiameter(double diameter)` | m | Set column diameter |
| `setColumnHeight(double height)` | m | Set column height |
| `setDesignPressure(double pressure)` | barg | Set design pressure |
| `setNumberOfStages(int stages)` | integer | Set theoretical stages |

#### Packing Configuration (packed columns)

| Method | Parameters | Description |
|--------|------------|-------------|
| `setPackingType(String type)` | "structured", "random" | Set packing type |
| `setPackingHeight(double height)` | m | Set packing height |

#### Tray Configuration (trayed columns)

| Method | Parameters | Description |
|--------|------------|-------------|
| `setTrayType(String type)` | "sieve", "valve", "bubble-cap" | Set tray type |

#### Internals

| Method | Parameters | Description |
|--------|------------|-------------|
| `setIncludeLiquidDistributor(boolean include)` | true/false | Include distributor |
| `setIncludeMistEliminator(boolean include)` | true/false | Include mist eliminator |
| `setIncludeReboiler(boolean include)` | true/false | Include reboiler |
| `setIncludeRefluxSystem(boolean include)` | true/false | Include reflux system |
| `setReboilerDuty(double duty)` | kW | Set reboiler duty |

---

## Enumerations and Constants

### Material Factors

| Material | Factor | Notes |
|----------|--------|-------|
| Carbon Steel | 1.0 | Base reference |
| Stainless Steel 304 | 1.3 | |
| Stainless Steel 316 | 1.5 | |
| Duplex SS | 2.0 | |
| Super Duplex | 2.5 | |
| Inconel | 3.0 | |
| Titanium | 4.0 | |
| Monel | 3.5 | |
| Hastelloy | 3.8 | |

### Pressure Factors

| Pressure (barg) | Factor |
|-----------------|--------|
| < 10 | 1.0 |
| 10-50 | 1.15 |
| 50-100 | 1.25 |
| 100-200 | 1.40 |
| > 200 | 1.60 |

### Installation Hours (typical)

| Equipment Type | Hours/Unit |
|----------------|------------|
| Separator/Vessel | 15-25 |
| Compressor | 30-50 |
| Heat Exchanger | 5-15 |
| Pump | 8-12 |
| Valve | 1-2 |
| Tank | 20-40 |
| Column | 40-80 |

---

## Data Structures

### Cost Breakdown Map

Returned by `getCostBreakdown()`:

```java
Map<String, Object> breakdown = {
    "equipmentType": "separator",
    "material": "carbon-steel",
    "materialFactor": 1.0,
    "designPressure_barg": 50.0,
    "pressureFactor": 1.15,
    "purchasedEquipmentCost_USD": 250000.0,
    "bareModuleCost_USD": 875000.0,
    "totalModuleCost_USD": 1093750.0,
    "grassRootsCost_USD": 1257812.5,
    "installationManHours": 20.0,
    // Equipment-specific fields...
}
```

### Process Cost Summary Map

Returned by `ProcessCostEstimate.toMap()`:

```java
Map<String, Object> summary = {
    "processName": "Gas Processing Plant",
    "timestamp": "2026-01-28T12:00:00Z",
    "costSummary": {
        "purchasedEquipmentCost_USD": 5000000.0,
        "bareModuleCost_USD": 17500000.0,
        "totalModuleCost_USD": 21875000.0,
        "grassRootsCost_USD": 25156250.0,
        "totalInstallationManHours": 450.0
    },
    "locationFactor": 1.35,
    "complexityFactor": 1.0,
    "currency": "NOK",
    "exchangeRate": 11.0,
    "costByEquipmentType": {...},
    "costByDiscipline": {...},
    "operatingCost": {
        "annualTotal_USD": 2500000.0,
        "breakdown": {...}
    },
    "equipmentList": [...]
}
```

---

*API Reference last updated: January 2026*
