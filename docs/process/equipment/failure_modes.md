---
title: Equipment Failure Modes
description: Documentation for equipment failure mode modeling in NeqSim for reliability and risk analysis.
---

# Equipment Failure Modes

Documentation for equipment failure mode modeling in NeqSim for reliability and risk analysis.

## Table of Contents
- [Overview](#overview)
- [EquipmentFailureMode Class](#equipmentfailuremode-class)
- [Failure Types](#failure-types)
- [Builder Pattern](#builder-pattern)
- [Reliability Data Sources](#reliability-data-sources)
- [Usage Examples](#usage-examples)
- [Integration with Risk Analysis](#integration-with-risk-analysis)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.failure`

The failure package provides classes for modeling equipment failure modes and their impact on process operations. This enables:

- Reliability analysis
- Risk assessment
- Availability calculations
- Monte Carlo simulation support
- Degraded operation modeling

| Class | Description |
|-------|-------------|
| `EquipmentFailureMode` | Defines how equipment can fail |
| `ReliabilityDataSource` | Source of reliability data (OREDA, etc.) |

---

## EquipmentFailureMode Class

The `EquipmentFailureMode` class represents a specific way that equipment can fail, including the consequences and recovery characteristics.

### Class Hierarchy

```
Serializable
└── EquipmentFailureMode
    ├── enum: FailureType
    └── static class: Builder
```

### Key Features

- **Failure Type Classification**: Trip, degraded, partial, full failure
- **Capacity Impact**: Capacity factor after failure (0.0 to 1.0)
- **Efficiency Impact**: Efficiency degradation factor
- **Recovery Modeling**: MTTR, auto-recovery options
- **Frequency Data**: Failure frequency per year
- **Builder Pattern**: Fluent API for construction

---

## Failure Types

### FailureType Enumeration

| Type | Description | Typical Capacity |
|------|-------------|------------------|
| `TRIP` | Equipment stops completely | 0.0 |
| `DEGRADED` | Operates at reduced capacity | 0.3 - 0.8 |
| `PARTIAL_FAILURE` | Some functions lost | 0.5 - 0.9 |
| `FULL_FAILURE` | Equipment non-functional | 0.0 |
| `MAINTENANCE` | Planned shutdown | 0.0 |
| `BYPASSED` | Flow routed around equipment | 0.0 (but flow continues) |

### Failure Type Examples

```java
import neqsim.process.equipment.failure.EquipmentFailureMode;
import neqsim.process.equipment.failure.EquipmentFailureMode.FailureType;

// Complete trip - compressor shutdown
EquipmentFailureMode trip = EquipmentFailureMode.builder()
    .name("Compressor Trip")
    .type(FailureType.TRIP)
    .capacityFactor(0.0)      // No output
    .mttr(24.0)               // 24 hours to repair
    .build();

// Degraded operation - fouled heat exchanger
EquipmentFailureMode fouled = EquipmentFailureMode.builder()
    .name("Heat Exchanger Fouling")
    .type(FailureType.DEGRADED)
    .capacityFactor(0.7)      // 70% capacity
    .efficiencyFactor(0.85)   // 85% of normal efficiency
    .mttr(48.0)               // 48 hours to clean
    .build();

// Partial failure - one of two pumps fails
EquipmentFailureMode partial = EquipmentFailureMode.builder()
    .name("Pump A Failure")
    .type(FailureType.PARTIAL_FAILURE)
    .capacityFactor(0.5)      // 50% capacity (Pump B still running)
    .mttr(12.0)
    .build();
```

---

## Builder Pattern

### Creating Failure Modes

The `EquipmentFailureMode` class uses the builder pattern for clear, readable construction:

```java
EquipmentFailureMode failureMode = EquipmentFailureMode.builder()
    .name("Control System Failure")
    .description("Loss of DCS communication to field devices")
    .type(FailureType.TRIP)
    .capacityFactor(0.0)
    .efficiencyFactor(1.0)
    .mttr(4.0)                          // 4 hours MTTR
    .failureFrequency(0.5)              // 0.5 per year
    .requiresImmediateAction(true)
    .autoRecoverable(false)
    .build();
```

### Builder Methods

| Method | Description | Default |
|--------|-------------|---------|
| `name(String)` | Failure mode name | - |
| `description(String)` | Detailed description | "" |
| `type(FailureType)` | Type of failure | TRIP |
| `capacityFactor(double)` | Capacity after failure (0-1) | 0.0 |
| `efficiencyFactor(double)` | Efficiency after failure (0-1) | 1.0 |
| `mttr(double)` | Mean time to repair (hours) | 0.0 |
| `failureFrequency(double)` | Failures per year | 0.0 |
| `requiresImmediateAction(boolean)` | Requires operator action | false |
| `autoRecoverable(boolean)` | Can recover automatically | false |
| `autoRecoveryTime(double)` | Time to auto-recover (seconds) | 0.0 |

---

## Reliability Data Sources

### ReliabilityDataSource Class

The `ReliabilityDataSource` class provides access to industry reliability databases:

```java
import neqsim.process.equipment.failure.ReliabilityDataSource;

// Get OREDA data for centrifugal compressor
ReliabilityDataSource oreda = new ReliabilityDataSource("OREDA");

// Standard equipment types and their failure rates
// Centrifugal Compressor: ~2.5 failures/million hours
// Reciprocating Compressor: ~5.0 failures/million hours
// Centrifugal Pump: ~1.5 failures/million hours
// Gas Turbine: ~3.0 failures/million hours
```

### Common Failure Rate Data

| Equipment | Failure Rate (per 10⁶ hours) | MTTR (hours) | Source |
|-----------|------------------------------|--------------|--------|
| Centrifugal Compressor | 2.0 - 3.5 | 24 - 72 | OREDA |
| Reciprocating Compressor | 4.0 - 6.0 | 12 - 48 | OREDA |
| Centrifugal Pump | 1.0 - 2.0 | 8 - 24 | OREDA |
| Heat Exchanger | 0.5 - 1.5 | 24 - 168 | OREDA |
| Control Valve | 0.5 - 1.0 | 4 - 12 | OREDA |
| Gas Turbine | 2.5 - 4.0 | 48 - 168 | OREDA |
| PSV | 0.1 - 0.3 | 4 - 8 | OREDA |

---

## Usage Examples

### Example 1: Compressor with Multiple Failure Modes

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.failure.EquipmentFailureMode;
import neqsim.process.equipment.failure.EquipmentFailureMode.FailureType;

// Create compressor
Compressor compressor = new Compressor("Export Compressor", gasStream);
compressor.setOutletPressure(150.0);

// Define failure modes
EquipmentFailureMode sealFailure = EquipmentFailureMode.builder()
    .name("Dry Gas Seal Failure")
    .description("Primary seal degradation requiring shutdown")
    .type(FailureType.TRIP)
    .capacityFactor(0.0)
    .mttr(72.0)  // 3 days to replace seals
    .failureFrequency(0.3)  // 0.3 per year
    .requiresImmediateAction(true)
    .build();

EquipmentFailureMode bearingWear = EquipmentFailureMode.builder()
    .name("Bearing Wear")
    .description("Bearing degradation causing vibration")
    .type(FailureType.DEGRADED)
    .capacityFactor(0.8)  // Can run at 80%
    .mttr(48.0)
    .failureFrequency(0.5)
    .build();

EquipmentFailureMode controlFault = EquipmentFailureMode.builder()
    .name("Control System Fault")
    .description("PLC communication error")
    .type(FailureType.TRIP)
    .capacityFactor(0.0)
    .mttr(4.0)
    .failureFrequency(1.0)
    .autoRecoverable(true)
    .autoRecoveryTime(300.0)  // 5 minutes
    .build();

// Apply failure mode to equipment
// compressor.setFailureMode(sealFailure);
```

### Example 2: Heat Exchanger Degradation

```java
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.failure.EquipmentFailureMode;

// Heat exchanger fouling modes
EquipmentFailureMode lightFouling = EquipmentFailureMode.builder()
    .name("Light Fouling")
    .type(FailureType.DEGRADED)
    .capacityFactor(0.9)
    .efficiencyFactor(0.85)
    .mttr(24.0)
    .failureFrequency(2.0)  // Common occurrence
    .build();

EquipmentFailureMode severeFouling = EquipmentFailureMode.builder()
    .name("Severe Fouling")
    .type(FailureType.DEGRADED)
    .capacityFactor(0.6)
    .efficiencyFactor(0.6)
    .mttr(72.0)  // Requires cleaning
    .failureFrequency(0.5)
    .build();

EquipmentFailureMode tubeLeakage = EquipmentFailureMode.builder()
    .name("Tube Leakage")
    .type(FailureType.TRIP)
    .capacityFactor(0.0)
    .mttr(168.0)  // 1 week for tube bundle replacement
    .failureFrequency(0.1)
    .requiresImmediateAction(true)
    .build();
```

### Example 3: Separator Level Control Failure

```java
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.failure.EquipmentFailureMode;

// Level control failures
EquipmentFailureMode levelTransmitterFail = EquipmentFailureMode.builder()
    .name("Level Transmitter Failure")
    .description("LT fails high causing overfill risk")
    .type(FailureType.PARTIAL_FAILURE)
    .capacityFactor(0.7)  // Operate with manual control
    .mttr(8.0)
    .failureFrequency(0.8)
    .requiresImmediateAction(true)
    .build();

EquipmentFailureMode levelValveStuck = EquipmentFailureMode.builder()
    .name("Level Control Valve Stuck")
    .description("LCV stuck in position")
    .type(FailureType.DEGRADED)
    .capacityFactor(0.5)
    .mttr(4.0)
    .failureFrequency(0.3)
    .autoRecoverable(false)
    .build();
```

---

## Integration with Risk Analysis

### Calculating Equipment Availability

```java
// Calculate availability from failure data
double failureRate = 2.5;  // per million hours
double mttr = 48.0;        // hours

double mttf = 1.0e6 / failureRate;  // Mean time to failure (hours)
double availability = mttf / (mttf + mttr);

System.out.println("MTTF: " + mttf + " hours");
System.out.println("Availability: " + (availability * 100) + "%");
// Output: MTTF: 400000 hours, Availability: 99.99%
```

### Monte Carlo Simulation Integration

```java
import java.util.Random;

// Simple Monte Carlo for production availability
Random random = new Random();
int simulations = 10000;
double totalUptime = 0.0;

double failureRatePerHour = 2.5 / 1.0e6;  // Convert to per hour
double mttrHours = 48.0;
double simulationHours = 8760.0;  // 1 year

for (int i = 0; i < simulations; i++) {
    double uptime = 0.0;
    double currentTime = 0.0;
    
    while (currentTime < simulationHours) {
        // Time to next failure (exponential distribution)
        double ttf = -Math.log(random.nextDouble()) / failureRatePerHour;
        
        if (currentTime + ttf > simulationHours) {
            uptime += simulationHours - currentTime;
            break;
        }
        
        uptime += ttf;
        currentTime += ttf + mttrHours;
    }
    
    totalUptime += uptime / simulationHours;
}

double averageAvailability = totalUptime / simulations;
System.out.println("Monte Carlo Availability: " + (averageAvailability * 100) + "%");
```

---

## Common Failure Mode Patterns

### Compressor Failure Modes

| Failure Mode | Type | Capacity | MTTR | Frequency |
|--------------|------|----------|------|-----------|
| Dry gas seal failure | TRIP | 0% | 72h | 0.3/yr |
| Bearing failure | TRIP | 0% | 48h | 0.2/yr |
| Anti-surge valve stuck | DEGRADED | 70% | 8h | 0.5/yr |
| Vibration high | DEGRADED | 80% | 24h | 1.0/yr |
| Motor/driver failure | TRIP | 0% | 24h | 0.5/yr |

### Pump Failure Modes

| Failure Mode | Type | Capacity | MTTR | Frequency |
|--------------|------|----------|------|-----------|
| Seal leakage | TRIP | 0% | 12h | 0.8/yr |
| Impeller wear | DEGRADED | 85% | 24h | 0.5/yr |
| Bearing failure | TRIP | 0% | 16h | 0.3/yr |
| Cavitation damage | DEGRADED | 70% | 48h | 0.2/yr |

### Separator Failure Modes

| Failure Mode | Type | Capacity | MTTR | Frequency |
|--------------|------|----------|------|-----------|
| Level control failure | DEGRADED | 70% | 4h | 1.0/yr |
| Pressure control failure | DEGRADED | 80% | 4h | 0.5/yr |
| Internals damage | TRIP | 0% | 168h | 0.1/yr |
| Instrument failure | PARTIAL | 90% | 8h | 2.0/yr |

---

## Related Documentation

- [Risk Simulation](../../risk/index) - Risk analysis framework
- [SIS Integration](../../risk/sis-integration) - Safety system verification
- [Process Equipment](../README) - Equipment overview
- [Capacity Constraints](../CAPACITY_CONSTRAINT_FRAMEWORK) - Operational limits

---

## API Reference

### EquipmentFailureMode

```java
// Builder creation
static Builder builder()

// Getters
String getName()
String getDescription()
FailureType getType()
double getCapacityFactor()
double getEfficiencyFactor()
double getMttr()
double getFailureFrequency()
boolean isRequiresImmediateAction()
boolean isAutoRecoverable()
double getAutoRecoveryTime()
```

### FailureType Enum

```java
enum FailureType {
    TRIP,
    DEGRADED,
    PARTIAL_FAILURE,
    FULL_FAILURE,
    MAINTENANCE,
    BYPASSED
}
```
