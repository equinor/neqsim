---
title: "Standards Package"
description: "The NeqSim standards package implements international standards for gas and oil quality calculations, enabling compliance verification and sales contract management."
---

# Standards Package

The NeqSim standards package implements international standards for gas and oil quality calculations, enabling compliance verification and sales contract management.

## Table of Contents
- [Overview](#overview)
- [Package Structure](#package-structure)
- [Sub-Documentation](#sub-documentation)
- [Core Concepts](#core-concepts)
- [Quick Start](#quick-start)
- [Sales Contracts](#sales-contracts)

---

## Overview

**Location:** `neqsim.standards`

The standards package provides implementations of:

1. **Gas Quality Standards** - ISO 6976, ISO 6974, ISO 6578, ISO 15403, ISO 18453
2. **Oil Quality Standards** - ASTM D6377 for vapor pressure
3. **Sales Contracts** - Specification verification against contractual limits

**Key Applications:**
- Calculating calorific values (GCV/LCV) and Wobbe index per ISO 6976
- LNG density calculations per ISO 6578
- Water and hydrocarbon dew point determination
- Reid vapor pressure (RVP) calculations
- Contractual compliance checking

---

## Package Structure

```
standards/
├── Standard.java                    # Abstract base class
├── StandardInterface.java           # Interface definition
│
├── gasquality/                      # Gas quality standards
│   ├── Standard_ISO6976.java        # Calorific values & Wobbe index
│   ├── Standard_ISO6976_2016.java   # ISO 6976:2016 edition
│   ├── Standard_ISO6974.java        # Gas chromatography composition
│   ├── Standard_ISO6578.java        # LNG density calculation
│   ├── Standard_ISO15403.java       # CNG fuel quality (MON, methane number)
│   ├── Draft_ISO18453.java          # Water dew point (GERG-water)
│   ├── Draft_GERG2004.java          # GERG-2004 EoS properties
│   ├── BestPracticeHydrocarbonDewPoint.java  # HC dew point
│   ├── GasChromotograpyhBase.java   # Gas composition base class
│   ├── SulfurSpecificationMethod.java  # H2S and sulfur content
│   └── UKspecifications_ICF_SI.java # UK ICF/SI specifications
│
├── oilquality/                      # Oil quality standards
│   └── Standard_ASTM_D6377.java     # Reid vapor pressure (RVP)
│
└── salescontract/                   # Contract management
    ├── BaseContract.java            # Contract implementation
    ├── ContractInterface.java       # Contract interface
    └── ContractSpecification.java   # Individual specifications
```

---

## Sub-Documentation

Detailed guides for each major standard:

| Guide | Description |
|-------|-------------|
| [ISO 6976 - Calorific Values](iso6976_calorific_values.md) | GCV, LCV, Wobbe index, density from composition |
| [ISO 6578 - LNG Density](iso6578_lng_density.md) | LNG density calculation method |
| [ISO 15403 - CNG Quality](iso15403_cng_quality.md) | Methane number and MON for vehicle fuel |
| [Dew Point Standards](dew_point_standards.md) | Water and hydrocarbon dew point methods |
| [ASTM D6377 - RVP](astm_d6377_rvp.md) | Reid vapor pressure for crude and condensate |
| [Sales Contracts](sales_contracts.md) | Contract specification and compliance checking |

---

## Core Concepts

### StandardInterface

All standards implement `StandardInterface`:

```java
public interface StandardInterface {
    void calculate();                              // Run calculation
    double getValue(String parameter);             // Get result
    double getValue(String parameter, String unit); // Get result with unit
    String getUnit(String parameter);              // Get unit string
    boolean isOnSpec();                            // Check compliance
    
    ContractInterface getSalesContract();          // Get attached contract
    void setSalesContract(ContractInterface contract);
    SystemInterface getThermoSystem();             // Get fluid
}
```

### Standard Base Class

Standards extend `Standard`:

```java
public abstract class Standard extends NamedBaseClass implements StandardInterface {
    protected SystemInterface thermoSystem;
    protected ThermodynamicOperations thermoOps;
    protected ContractInterface salesContract;
    protected String standardDescription;
    private String referenceState = "real";  // or "ideal"
    private double referencePressure = 70.0;
}
```

### Reference States

Most gas quality standards support:
- **Real gas** - Accounts for compressibility
- **Ideal gas** - Assumes Z = 1

```java
standard.setReferenceState("real");   // Default
standard.setReferenceState("ideal");  // Ideal gas assumption
```

---

## Quick Start

### ISO 6976 - Calorific Values

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.standards.gasquality.Standard_ISO6976;

// Create gas composition
SystemInterface gas = new SystemSrkEos(273.15 + 15, 1.01325);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.02);
gas.addComponent("nitrogen", 0.02);
gas.addComponent("CO2", 0.01);
gas.setMixingRule("classic");

// Create standard
// Parameters: system, volumeRefT(°C), energyRefT(°C), referenceType
Standard_ISO6976 iso6976 = new Standard_ISO6976(gas, 15, 15, "volume");
iso6976.setReferenceState("real");

// Calculate
iso6976.calculate();

// Get results
double gcv = iso6976.getValue("GCV");           // Gross calorific value [kJ/m³]
double lcv = iso6976.getValue("LCV");           // Net calorific value [kJ/m³]
double wobbe = iso6976.getValue("SuperiorWobbeIndex");  // Wobbe index [kJ/m³]
double relDens = iso6976.getValue("RelativeDensity");   // Relative density [-]
double Z = iso6976.getValue("CompressionFactor");       // Compressibility [-]
double molarMass = iso6976.getValue("MolarMass");       // g/mol

System.out.printf("GCV = %.2f kJ/m³%n", gcv);
System.out.printf("Wobbe Index = %.2f kJ/m³%n", wobbe);
System.out.printf("Relative Density = %.4f%n", relDens);
```

### LNG Density (ISO 6578)

```java
import neqsim.standards.gasquality.Standard_ISO6578;

// LNG composition
SystemInterface lng = new SystemSrkEos(110, 1.01325);  // -163°C
lng.addComponent("methane", 0.92);
lng.addComponent("ethane", 0.05);
lng.addComponent("propane", 0.02);
lng.addComponent("nitrogen", 0.01);
lng.setMixingRule("classic");

// Calculate density
Standard_ISO6578 iso6578 = new Standard_ISO6578(lng);
iso6578.calculate();

double density = iso6578.getValue("density", "kg/m3");
System.out.printf("LNG Density = %.2f kg/m³%n", density);
```

### Water Dew Point (ISO 18453)

```java
import neqsim.standards.gasquality.Draft_ISO18453;

// Natural gas with water
SystemInterface wetGas = new SystemSrkCPA(273.15 + 20, 70.0);
wetGas.addComponent("methane", 0.95);
wetGas.addComponent("water", 50e-6);  // 50 ppm water
wetGas.setMixingRule("CPA-EoS");

// Calculate water dew point
Draft_ISO18453 waterDewPoint = new Draft_ISO18453(wetGas);
waterDewPoint.calculate();

double wdp = waterDewPoint.getValue("dewPointTemperature");
System.out.printf("Water Dew Point = %.1f °C%n", wdp);
```

### Reid Vapor Pressure (ASTM D6377)

```java
import neqsim.standards.oilquality.Standard_ASTM_D6377;

// Crude oil / condensate
SystemInterface crude = new SystemSrkEos(273.15 + 15, 1.0);
crude.addComponent("methane", 0.02);
crude.addComponent("ethane", 0.03);
crude.addComponent("propane", 0.05);
crude.addComponent("n-butane", 0.08);
crude.addComponent("n-pentane", 0.10);
crude.addTBPfraction("C6", 0.15, 86/1000.0, 0.66);
crude.addTBPfraction("C10", 0.30, 142/1000.0, 0.78);
crude.addTBPfraction("C20", 0.27, 282/1000.0, 0.85);
crude.setMixingRule("classic");

// Calculate RVP
Standard_ASTM_D6377 rvpStandard = new Standard_ASTM_D6377(crude);
rvpStandard.setMethodRVP("VPCR4");  // Options: VPCR4, RVP_ASTM_D6377, RVP_ASTM_D323_82
rvpStandard.calculate();

double rvp = rvpStandard.getValue("RVP", "bara");
double tvp = rvpStandard.getValue("TVP", "bara");
System.out.printf("RVP = %.3f bara%n", rvp);
System.out.printf("TVP = %.3f bara%n", tvp);
```

---

## Sales Contracts

### Creating a Contract

```java
import neqsim.standards.salescontract.BaseContract;
import neqsim.standards.salescontract.ContractInterface;

// Create contract from database
ContractInterface contract = new BaseContract(gas, "Kaarstoe", "Norway");

// Run compliance check
contract.runCheck();

// Get results
String[][] results = contract.getResultTable();
int numSpecs = contract.getSpecificationsNumber();

// Display results
contract.display();
```

### Attaching Contract to Standard

```java
Standard_ISO6976 standard = new Standard_ISO6976(gas);
standard.setSalesContract(contract);
standard.calculate();

// Check if on specification
boolean onSpec = standard.isOnSpec();
```

### Custom Contract Specifications

```java
import neqsim.standards.salescontract.ContractSpecification;

// Create custom specification
ContractSpecification spec = new ContractSpecification(
    "Water Dew Point",           // Name
    "Maximum water dew point",   // Description
    "Norway",                    // Country
    "Kaarstoe",                  // Terminal
    waterDewPointStandard,       // Standard method
    -20.0,                       // Min value
    -8.0,                        // Max value
    "°C",                        // Unit
    15.0,                        // Reference T measurement
    15.0,                        // Reference T combustion
    70.0,                        // Reference pressure
    "At 70 bar"                  // Comments
);
```

---

## Available Return Parameters

### ISO 6976

| Parameter | Description | Unit |
|-----------|-------------|------|
| `GCV` / `SuperiorCalorificValue` | Gross calorific value | kJ/m³ |
| `LCV` / `InferiorCalorificValue` | Net calorific value | kJ/m³ |
| `SuperiorWobbeIndex` | Superior Wobbe index | kJ/m³ |
| `InferiorWobbeIndex` | Inferior Wobbe index | kJ/m³ |
| `WI` | Wobbe index (alias) | kJ/m³ |
| `RelativeDensity` | Relative density (air=1) | - |
| `CompressionFactor` | Compressibility factor Z | - |
| `MolarMass` | Average molar mass | g/mol |
| `DensityIdeal` | Ideal gas density | kg/m³ |
| `DensityReal` | Real gas density | kg/m³ |

### ISO 6578

| Parameter | Description | Unit |
|-----------|-------------|------|
| `density` | LNG density | kg/m³ |

### ASTM D6377

| Parameter | Description | Unit |
|-----------|-------------|------|
| `RVP` | Reid vapor pressure | bara |
| `TVP` | True vapor pressure | bara |
| `VPCR4` | Vapor pressure at V/L=4 | bara |

---

## Reference Conditions

### Standard Temperature/Pressure

| Standard | Volume Ref T | Energy Ref T | Pressure |
|----------|-------------|--------------|----------|
| ISO 6976 | 0, 15, 20°C | 0, 15, 20, 25°C, 60°F | 1.01325 bar |
| ISO 6578 | -160 to -140°C | - | 1.01325 bar |
| ASTM D6377 | 37.8°C (100°F) | - | - |

### Setting Reference Conditions

```java
// ISO 6976 with specific reference conditions
Standard_ISO6976 standard = new Standard_ISO6976(
    gas,
    15.0,      // Volume reference temperature (°C)
    25.0,      // Energy reference temperature (°C)  
    "volume"   // Reference type: "volume", "mass", or "molar"
);

// Modify reference conditions after creation
standard.setVolRefT(0.0);      // Volume at 0°C
standard.setEnergyRefT(15.0);  // Combustion at 15°C
```

---

## Best Practices

### Composition Normalization
- Ensure compositions sum to 1.0 before calculation
- Standards internally use mole fractions

### Component Coverage
- ISO 6976 has data for common natural gas components
- Unknown components are approximated (HC → n-heptane, alcohols → methanol)
- Check `componentsNotDefinedByStandard` for warnings

### Reference State Selection
- Use "real" for custody transfer calculations
- Use "ideal" for simplified comparisons
- Document the reference state used

### Contract Database
- Contract specifications are stored in database table `gascontractspecifications`
- Query by terminal and country

---

## References

1. ISO 6976:2016 - Natural gas — Calculation of calorific values, density, relative density and Wobbe indices from composition
2. ISO 6578:2017 - Refrigerated hydrocarbon liquids — Static measurement — Calculation procedure
3. ISO 15403-1:2006 - Natural gas — Natural gas for use as a compressed fuel for vehicles
4. ISO 18453:2004 - Natural gas — Correlation between water content and water dew point
5. ASTM D6377 - Standard Test Method for Determination of Vapor Pressure of Crude Oil
6. GERG-2004 - The GERG-2004 Wide-Range Equation of State for Natural Gases and Other Mixtures
