---
title: "Sales Contracts"
description: "The sales contract system enables specification verification and compliance checking for natural gas quality."
---

# Sales Contracts

The sales contract system enables specification verification and compliance checking for natural gas quality.

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Creating Contracts](#creating-contracts)
- [Contract Specifications](#contract-specifications)
- [Usage Examples](#usage-examples)
- [Database Integration](#database-integration)

---

## Overview

**Location:** `neqsim.standards.salescontract`

**Purpose:**
- Define quality specifications for gas sales points
- Verify gas quality against contractual limits
- Generate compliance reports
- Support multi-specification contracts

**Classes:**
- `BaseContract` - Main contract implementation
- `ContractInterface` - Contract interface
- `ContractSpecification` - Individual specification

---

## Architecture

### Class Hierarchy

```
ContractInterface
    │
    └── BaseContract
            │
            ├── ArrayList<ContractSpecification>
            │       │
            │       └── StandardInterface (method)
            │
            └── Database connection (gascontractspecifications)
```

### Workflow

```
1. Create Contract (from database or manually)
        ↓
2. Add Specifications (linked to standards)
        ↓
3. Run Compliance Check
        ↓
4. Generate Results Table
        ↓
5. Display/Export Results
```

---

## Creating Contracts

### From Database

```java
import neqsim.standards.salescontract.BaseContract;
import neqsim.standards.salescontract.ContractInterface;

// Load contract from database by terminal and country
ContractInterface contract = new BaseContract(
    thermoSystem,    // Gas composition
    "Kaarstoe",      // Terminal name
    "Norway"         // Country
);
```

### Programmatic Creation

```java
// Create empty contract
ContractInterface contract = new BaseContract();

// Or with basic water dew point spec
ContractInterface contract = new BaseContract(thermoSystem);
```

---

## Contract Specifications

### ContractSpecification Class

Each specification contains:

| Field | Description |
|-------|-------------|
| `name` | Specification name |
| `specification` | Description |
| `country` | Country code |
| `terminal` | Terminal/delivery point |
| `standard` | StandardInterface method |
| `minValue` | Minimum acceptable value |
| `maxValue` | Maximum acceptable value |
| `unit` | Unit of measurement |
| `referenceTemperatureMeasurement` | Reference T for measurement |
| `referenceTemperatureCombustion` | Reference T for combustion |
| `referencePressure` | Reference pressure |
| `comments` | Additional notes |

### Creating Specifications

```java
import neqsim.standards.salescontract.ContractSpecification;
import neqsim.standards.gasquality.Draft_ISO18453;

// Create water dew point specification
StandardInterface waterDPMethod = new Draft_ISO18453(thermoSystem);

ContractSpecification waterSpec = new ContractSpecification(
    "Water Dew Point",           // Name
    "Maximum water dew point",   // Specification description
    "Norway",                    // Country
    "Kaarstoe",                  // Terminal
    waterDPMethod,               // Calculation method
    -20.0,                       // Minimum value
    -8.0,                        // Maximum value
    "°C",                        // Unit
    15.0,                        // Reference T measurement
    15.0,                        // Reference T combustion
    70.0,                        // Reference pressure (bar)
    "At 70 bar"                  // Comments
);
```

### Available Standard Methods

| Method Name | Class | Purpose |
|-------------|-------|---------|
| `ISO18453` | `Draft_ISO18453` | Water dew point |
| `ISO6974` | `Standard_ISO6974` | Gas composition |
| `ISO6976` | `Standard_ISO6976` | Calorific values, Wobbe |
| `BestPracticeHydrocarbonDewPoint` | `BestPracticeHydrocarbonDewPoint` | HC dew point |
| `SulfurSpecificationMethod` | `SulfurSpecificationMethod` | H2S and sulfur |
| `UKspecifications` | `UKspecifications_ICF_SI` | UK ICF/SI specs |

---

## Usage Examples

### Basic Contract Check

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.standards.salescontract.BaseContract;

// Create gas composition
SystemInterface gas = new SystemSrkCPAstatoil(273.15 + 15, 70.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.02);
gas.addComponent("CO2", 0.02);
gas.addComponent("nitrogen", 0.005);
gas.addComponent("water", 30e-6);  // 30 ppm water
gas.setMixingRule("CPA_Statoil");

// Load contract from database
BaseContract contract = new BaseContract(gas, "Kaarstoe", "Norway");

// Run compliance check
contract.runCheck();

// Get results
String[][] results = contract.getResultTable();
int numSpecs = contract.getSpecificationsNumber();

System.out.printf("Checked %d specifications%n", numSpecs);

// Display results
contract.display();
```

### Custom Contract

```java
import neqsim.standards.salescontract.*;
import neqsim.standards.gasquality.*;

// Create empty contract
BaseContract contract = new BaseContract();

// Add water dew point specification
Draft_ISO18453 waterMethod = new Draft_ISO18453(gas);
waterMethod.setReferencePressure(70.0);
ContractSpecification waterSpec = new ContractSpecification(
    "Water DP", "Water dew point max", "Export", "Platform",
    waterMethod, -100, -8, "°C", 15, 15, 70, ""
);
contract.addSpecification(waterSpec);

// Add Wobbe index specification
Standard_ISO6976 wobbeMethod = new Standard_ISO6976(gas, 15, 25, "volume");
ContractSpecification wobbeSpec = new ContractSpecification(
    "Wobbe Index", "Wobbe index range", "Export", "Platform",
    wobbeMethod, 47300, 51500, "kJ/m³", 15, 25, 1.01325, ""
);
contract.addSpecification(wobbeSpec);

// Add GCV specification
ContractSpecification gcvSpec = new ContractSpecification(
    "GCV", "Gross calorific value", "Export", "Platform",
    wobbeMethod, 37500, 43000, "kJ/m³", 15, 25, 1.01325, ""
);
contract.addSpecification(gcvSpec);

// Run check
contract.runCheck();
```

### Attaching Contract to Standard

```java
import neqsim.standards.gasquality.Draft_ISO18453;

// Create standard with contract
Draft_ISO18453 waterDP = new Draft_ISO18453(gas);
waterDP.setSalesContract(contract);

// Calculate
waterDP.calculate();

// Check specification compliance
if (waterDP.isOnSpec()) {
    System.out.println("PASS: Water dew point within specification");
} else {
    System.out.println("FAIL: Water dew point out of specification");
}
```

### Contract Results Table

```java
// After running check
String[][] results = contract.getResultTable();

// Results table structure:
// [row][0] = Specification name
// [row][1] = Measured value
// [row][2] = Min specification
// [row][3] = Max specification
// [row][4] = Unit
// [row][5] = Pass/Fail status

System.out.println("Specification | Value | Min | Max | Unit | Status");
System.out.println("--------------|-------|-----|-----|------|-------");

for (int i = 0; i < contract.getSpecificationsNumber(); i++) {
    System.out.printf("%13s | %5s | %3s | %3s | %4s | %6s%n",
        results[i][0], results[i][1], results[i][2],
        results[i][3], results[i][4], results[i][5]);
}
```

---

## Database Integration

### Database Table Structure

Table: `gascontractspecifications`

| Column | Description |
|--------|-------------|
| `NAME` | Specification name |
| `SPECIFICATION` | Description |
| `COUNTRY` | Country code |
| `TERMINAL` | Delivery point |
| `METHOD` | Calculation method name |
| `MINVALUE` | Minimum value |
| `MAXVALUE` | Maximum value |
| `UNIT` | Unit string |
| `ReferenceTdegC` | Reference temperature |
| `ReferencePbar` | Reference pressure |
| `Comments` | Additional notes |

### Method Name Mapping

| Database Method | Class |
|-----------------|-------|
| `ISO18453` | `Draft_ISO18453` |
| `ISO6974` | `Standard_ISO6974` |
| `ISO6976` | `Standard_ISO6976` |
| `BestPracticeHydrocarbonDewPoint` | `BestPracticeHydrocarbonDewPoint` |
| `SulfurSpecificationMethod` | `SulfurSpecificationMethod` |
| `UKspecifications` | `UKspecifications_ICF_SI` |

### Querying Contracts

```java
// Contracts are loaded by terminal and country
BaseContract norwegianContract = new BaseContract(gas, "Kaarstoe", "Norway");
BaseContract ukContract = new BaseContract(gas, "StFergus", "UK");
BaseContract belgianContract = new BaseContract(gas, "Zeebrugge", "Belgium");
```

---

## Typical Specifications

### Norwegian Pipeline Gas

| Parameter | Min | Max | Unit | Reference |
|-----------|-----|-----|------|-----------|
| Water dew point | - | -8 | °C | 70 bar |
| HC dew point | - | -2 | °C | cricondentherm |
| GCV | 36.5 | 44.0 | MJ/Sm³ | 15°C/15°C |
| Wobbe index | 47.0 | 52.0 | MJ/Sm³ | 15°C/15°C |
| CO₂ | - | 2.5 | mol% | - |
| H₂S | - | 5 | mg/Sm³ | - |

### UK NTS Gas

| Parameter | Min | Max | Unit | Reference |
|-----------|-----|-----|------|-----------|
| GCV | 36.9 | 42.3 | MJ/m³ | 15°C/15°C |
| Wobbe index | 47.2 | 51.41 | MJ/m³ | 15°C/15°C |
| ICF | - | 0.48 | - | - |
| SI | - | 0.60 | - | - |

---

## Best Practices

### Contract Management

1. Store specifications in database for consistency
2. Version control specification changes
3. Document reference conditions clearly
4. Include measurement uncertainty allowances

### Compliance Checking

1. Run checks before custody transfer
2. Log all specification results
3. Alert on near-limit values
4. Track specification trends over time

### Integration

1. Link to online analyzers for real-time checking
2. Interface with SCADA systems
3. Generate automatic compliance reports
4. Archive historical compliance data

---

## References

1. EASEE-gas Common Business Practice 2005-001 - Harmonisation of Natural Gas Quality
2. EN 16726 - Gas infrastructure - Quality of gas - Group H
3. ISO 13686 - Natural gas — Quality designation
4. GTS (Dutch) - Gas quality specifications
5. National Grid (UK) - Gas Ten Year Statement
