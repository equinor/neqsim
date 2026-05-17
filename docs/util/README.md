---
title: "Utilities Package"
description: "The `util` package provides common utilities for database access, unit conversion, serialization, exceptions, and threading."
---

# Utilities Package

The `util` package provides common utilities for database access, unit conversion, serialization, exceptions, and threading.

## Table of Contents
- [Overview](#overview)
- [Package Structure](#package-structure)
- [Database Access](#database-access)
- [Unit Conversion](#unit-conversion)
- [Serialization](#serialization)
- [Exceptions](#exceptions)
- [Threading](#threading)

---

## Overview

**Location:** `neqsim.util`

**Purpose:**
- Database connectivity and queries
- Unit conversion utilities
- Object serialization and deserialization
- Custom exception handling
- Thread pool management
- Python integration

---

## Package Structure

```
util/
├── NamedBaseClass.java           # Base class with name property
├── NamedInterface.java           # Named interface
├── NeqSimLogging.java            # Logging utilities
├── NeqSimThreadPool.java         # Thread pool management
├── ExcludeFromJacocoGeneratedReport.java
│
├── database/                     # Database access
│   ├── NeqSimDataBase.java       # Main database class
│   ├── NeqSimContractDataBase.java
│   ├── NeqSimExperimentDatabase.java
│   └── NeqSimFluidDataBase.java
│
├── exception/                    # Custom exceptions
│   ├── InvalidInputException.java
│   ├── ThermoException.java
│   └── NotImplementedException.java
│
├── generator/                    # Code generation
│   └── PropertyGenerator.java
│
├── manifest/                     # Manifest handling
│   └── ManifestHandler.java
│
├── python/                       # Python integration
│   └── PythonIntegration.java
│
├── serialization/                # Serialization utilities
│   └── SerializationManager.java
│
├── unit/                         # Unit conversion
│   ├── Units.java
│   └── UnitConverter.java
│
└── util/                         # General utilities
    └── Utilities.java
```

---

## Database Access

### NeqSimDataBase

Main class for database connectivity.

```java
import neqsim.util.database.NeqSimDataBase;

// Get database connection
try (NeqSimDataBase db = new NeqSimDataBase()) {
    // Execute query
    ResultSet rs = db.getResultSet(
        "SELECT * FROM comp WHERE compname = 'methane'"
    );
    
    while (rs.next()) {
        double Tc = rs.getDouble("TC");
        double Pc = rs.getDouble("PC");
        double omega = rs.getDouble("ACF");
    }
}
```

### Database Configuration

```java
// Set database path (for embedded Derby)
NeqSimDataBase.setDataBaseType("Derby");
NeqSimDataBase.setConnectionString("jdbc:derby:NeqSimDatabase");

// Or use PostgreSQL
NeqSimDataBase.setDataBaseType("PostgreSQL");
NeqSimDataBase.setConnectionString("jdbc:postgresql://localhost:5432/neqsim");
NeqSimDataBase.setUsername("user");
NeqSimDataBase.setPassword("password");
```

### Predefined Queries

```java
// Component data
NeqSimFluidDataBase.getComponentData("methane");

// Binary interaction parameters
NeqSimFluidDataBase.getInteractionParameters("methane", "ethane", "SRK");

// Experiment data
NeqSimExperimentDatabase.getExperimentData("VLE_CH4_CO2");
```

---

## Unit Conversion

For comprehensive unit conversion documentation, see **[Unit Conversion Guide](unit_conversion)**.

### Quick Reference

```java
import neqsim.util.unit.Units;
import neqsim.util.unit.PressureUnit;
import neqsim.util.unit.TemperatureUnit;

// Direct unit conversion
PressureUnit pu = new PressureUnit(50.0, "bara");
double p_psia = pu.getValue("psia");

TemperatureUnit tu = new TemperatureUnit(25.0, "C");
double t_K = tu.getValue("K");

// In fluid properties
double T_C = fluid.getTemperature("C");
fluid.setTemperature(25.0, "C");

double P_bara = fluid.getPressure("bara");
fluid.setPressure(50.0, "bara");

double flow = stream.getFlowRate("kg/hr");
stream.setFlowRate(1000.0, "kg/hr");
```

### Supported Units

| Property | Units |
|----------|-------|
| Temperature | K, C, F, R |
| Pressure | Pa, bara, barg, psia, psig, atm, mmHg, kPa, MPa |
| Flow rate | kg/s, kg/hr, lb/hr, Sm3/hr, MSm3/day, mol/s, kmol/hr |
| Volume | m3, L, ft3, bbl, gal |
| Density | kg/m3, g/cm3, lb/ft3 |
| Viscosity | Pa.s, cP, mPa.s |
| Energy | J, kJ, MJ, cal, BTU |
| Power | W, kW, MW, hp |

// Enthalpy
double H_kJ = fluid.getEnthalpy("kJ/kg");
```

---

## Serialization

NeqSim provides multiple serialization options for saving and loading simulations.

### Process System Serialization

```java
// Save process system to compressed .neqsim file
ProcessSystem process = new ProcessSystem("My Process");
// ... add equipment ...
process.saveToNeqsim("myprocess.neqsim");

// Load from file (auto-runs after loading)
ProcessSystem loaded = ProcessSystem.loadFromNeqsim("myprocess.neqsim");

// Auto-detect format by extension
process.saveAuto("myprocess.neqsim");  // Compressed
process.saveAuto("myprocess.json");    // JSON state
```

### Process Model Serialization (Multi-Process)

```java
// Save ProcessModel containing multiple ProcessSystems
ProcessModel model = new ProcessModel();
model.add("upstream", upstreamProcess);
model.add("downstream", downstreamProcess);

model.saveToNeqsim("field_model.neqsim");

// Load (auto-runs after loading)
ProcessModel loaded = ProcessModel.loadFromNeqsim("field_model.neqsim");
```

### JSON State for Version Control

```java
// Export to Git-friendly JSON format
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
state.setVersion("1.0.0");
state.saveToFile("process_v1.0.0.json");

// Load and validate
ProcessSystemState loaded = ProcessSystemState.loadFromFile("process_v1.0.0.json");
if (loaded.validate().isValid()) {
    ProcessSystem restored = loaded.toProcessSystem();
}
```

### Deep Copy via Serialization

```java
// Clone using serialization (deep copy)
SystemInterface clone = fluid.clone();

// Or for process equipment
ProcessEquipmentInterface copy = equipment.copy();
```

**For full documentation:** See [Process Serialization Guide](../simulation/process_serialization)

---

## Exceptions

### Custom Exceptions

```java
import neqsim.util.exception.*;

// Invalid input
if (temperature < 0) {
    throw new InvalidInputException("Temperature", 
        "Temperature must be positive");
}

// Thermodynamic calculation failure
try {
    ops.TPflash();
} catch (ThermoException e) {
    System.err.println("Flash calculation failed: " + e.getMessage());
}

// Not implemented feature
throw new NotImplementedException("This feature", 
    "Will be available in next release");
```

### Exception Handling Pattern

```java
try {
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
} catch (InvalidInputException e) {
    // Handle invalid inputs
    logger.error("Invalid input: " + e.getMessage());
} catch (ThermoException e) {
    // Handle calculation failures
    logger.error("Calculation failed: " + e.getMessage());
} catch (Exception e) {
    // Handle unexpected errors
    logger.error("Unexpected error", e);
}
```

---

## Threading

### NeqSimThreadPool

Manage parallel calculations.

```java
import neqsim.util.NeqSimThreadPool;

// Configure thread pool
NeqSimThreadPool.setNumberOfThreads(8);

// Submit tasks
Future<Double> result1 = NeqSimThreadPool.submit(() -> {
    // Parallel calculation
    return calculateProperty1();
});

Future<Double> result2 = NeqSimThreadPool.submit(() -> {
    return calculateProperty2();
});

// Get results
double prop1 = result1.get();
double prop2 = result2.get();
```

### Parallel Flash Calculations

```java
// Run multiple flashes in parallel
List<SystemInterface> fluids = prepareFluids();

List<Future<SystemInterface>> futures = fluids.stream()
    .map(f -> NeqSimThreadPool.submit(() -> {
        ThermodynamicOperations ops = new ThermodynamicOperations(f);
        ops.TPflash();
        return f;
    }))
    .collect(Collectors.toList());

// Collect results
for (Future<SystemInterface> future : futures) {
    SystemInterface result = future.get();
    // Process result
}
```

---

## Logging

### NeqSimLogging

Configure logging.

```java
import neqsim.util.NeqSimLogging;

// Set log level
NeqSimLogging.setLogLevel(Level.DEBUG);

// Log messages
NeqSimLogging.info("Process started");
NeqSimLogging.debug("Temperature: " + T);
NeqSimLogging.error("Calculation failed", exception);
```

### Log4j2 Configuration

NeqSim uses Log4j2. Configure via `log4j2.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
    </Console>
  </Appenders>
  <Loggers>
    <Root level="info">
      <AppenderRef ref="Console"/>
    </Root>
    <Logger name="neqsim" level="debug"/>
  </Loggers>
</Configuration>
```

---

## Python Integration

### Direct Java Access

```python
import jpype
import jpype.imports
from jpype.types import *

# Start JVM
jpype.startJVM(classpath=['neqsim.jar'])

from neqsim.thermo.system import SystemSrkEos
from neqsim.thermodynamicoperations import ThermodynamicOperations

# Create fluid
fluid = SystemSrkEos(300.0, 50.0)
fluid.addComponent("methane", 0.9)
fluid.addComponent("ethane", 0.1)
fluid.setMixingRule("classic")

# Flash
ops = ThermodynamicOperations(fluid)
ops.TPflash()

print(f"Density: {fluid.getDensity('kg/m3'):.2f} kg/m³")
```

---

## Named Objects

### NamedBaseClass

Base class for named objects.

```java
public class MyEquipment extends NamedBaseClass {
    public MyEquipment(String name) {
        super(name);
    }
}

// Usage
MyEquipment eq = new MyEquipment("E-100");
String name = eq.getName();
eq.setName("E-101");
```

---

## Best Practices

1. **Close database connections** - Use try-with-resources
2. **Handle units explicitly** - Always specify units in API calls
3. **Use thread pool** for parallel calculations
4. **Serialize for persistence** - Save/load complex objects
5. **Log appropriately** - Use debug for details, info for important events

---

## Related Documentation

- [Developer Setup](../development/DEVELOPER_SETUP) - Environment configuration
- [Component Database Guide](../thermo/component_database_guide) - Database tables
