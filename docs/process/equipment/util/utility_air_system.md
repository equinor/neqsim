---
title: Utility Air System
description: Documentation for utility air systems in NeqSim including instrument air, plant air, and breathing air per ISO 8573-1.
---

# Utility Air System

Models utility air systems for offshore and onshore facilities per ISO 8573-1.

## Table of Contents
- [Overview](#overview)
- [Design Standards](#design-standards)
- [Air Quality Classes](#air-quality-classes)
- [System Components](#system-components)
- [Usage Examples](#usage-examples)
- [Sizing and Capacity](#sizing-and-capacity)
- [API Reference](#api-reference)

---

## Overview

**Location:** `neqsim.process.equipment.util.UtilityAirSystem`

The `UtilityAirSystem` class models compressed air systems providing:

- **Instrument air** - Clean, dry air for pneumatic instruments and controls
- **Plant air** - General purpose air for tools and cleaning
- **Service air** - Air for maintenance activities
- **Breathing air** - Respirable air for personnel (higher purity)

### Key Features

- ISO 8573-1 air quality class specification
- Multiple compressor and dryer type options
- Automatic capacity calculation
- Consumer demand tracking
- Receiver tank sizing

---

## Design Standards

| Standard | Description |
|----------|-------------|
| ISO 8573-1 | Compressed Air Quality Classes |
| NORSOK P-002 | Process System Design |
| API RP 11P | Packaged Reciprocating Compressors |
| EN 12021 | Breathing air for diving and other operations |

---

## Air Quality Classes

### ISO 8573-1 Classification

| Class | Max Particles (μm) | Dew Point (°C) | Max Oil (mg/m³) | Typical Use |
|-------|--------------------:|---------------:|----------------:|-------------|
| **CLASS_1** | 0.1 | -70 | 0.01 | Breathing/Medical |
| **CLASS_2** | 1.0 | -40 | 0.1 | Instrument Air |
| **CLASS_3** | 5.0 | -20 | 1.0 | Control Air |
| **CLASS_4** | 15.0 | +3 | 5.0 | Plant Air |
| **CLASS_5** | 40.0 | +7 | 25.0 | Service Air |

### Quality Class Usage

```java
import neqsim.process.equipment.util.UtilityAirSystem.AirQualityClass;

// Get instrument air requirements
AirQualityClass instrAir = AirQualityClass.CLASS_2;
double maxParticles = instrAir.getMaxParticleSizeMicron();  // 1.0 μm
double dewPoint = instrAir.getMaxDewPointC();                // -40°C
double maxOil = instrAir.getMaxOilMgM3();                    // 0.1 mg/m³
String use = instrAir.getTypicalUse();                       // "Instrument Air"
```

---

## System Components

### Typical System Layout

```
                    ┌───────────────┐
Atmospheric Air ───►│  Air Filter   │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │  Compressor   │───► Intercooler
                    │   (N+1)       │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │  Aftercooler  │───► Condensate
                    │   + Separator │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │  Air Dryer    │───► Purge/Regen
                    │  (Desiccant)  │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │   Receiver    │
                    │     Tank      │
                    └───────┬───────┘
                            │
               ┌────────────┼────────────┐
               ▼            ▼            ▼
          Instrument    Plant Air   Service Air
             Air
```

### Compressor Types

| Type | Description | Best Application |
|------|-------------|------------------|
| `ROTARY_SCREW` | Most common for continuous duty | General purpose |
| `RECIPROCATING` | High pressure capability | Small systems, high P |
| `CENTRIFUGAL` | Large capacity | Large facilities |
| `SCROLL` | Small, quiet operation | Small/specialty |

### Dryer Types

| Type | Dew Point (°C) | Air Yield | Notes |
|------|---------------:|----------:|-------|
| `REFRIGERATED` | +3 | 95% | Lowest cost |
| `DESICCANT_HEATLESS` | -40 | 85% | Simple, reliable |
| `DESICCANT_HEATED` | -40 | 92% | More efficient |
| `MEMBRANE` | -20 | 80% | Compact, no moving parts |
| `HYBRID` | -70 | 88% | Breathing air |

---

## Usage Examples

### Java Example - Complete System

```java
import neqsim.process.equipment.util.UtilityAirSystem;
import neqsim.process.equipment.util.UtilityAirSystem.*;

// Create utility air system
UtilityAirSystem airSystem = new UtilityAirSystem("Platform Air System");

// Configure for instrument air quality
airSystem.setTargetQuality(AirQualityClass.CLASS_2);  // ISO Class 2
airSystem.setCompressorType(CompressorType.ROTARY_SCREW);
airSystem.setDryerType(DryerType.DESICCANT_HEATED);

// Set operating parameters
airSystem.setDischargePressure(8.0);      // barg
airSystem.setInletTemperature(25.0);      // °C ambient
airSystem.setInletRelativeHumidity(70.0); // % RH
airSystem.setAfterCoolerOutletTemp(35.0); // °C

// Configure redundancy (N+1)
airSystem.setNumberOfCompressors(3);  // 2 duty + 1 standby

// Set demand
airSystem.setTotalAirDemand(500.0);   // Nm³/hr
airSystem.setInstrumentAirFraction(0.60);  // 60% instrument air
airSystem.setPlantAirFraction(0.30);       // 30% plant air
airSystem.setServiceAirFraction(0.10);     // 10% service/breathing

// Add specific consumers
airSystem.addConsumer("Instrument Air Header", AirQualityClass.CLASS_2, 300.0);
airSystem.addConsumer("Plant Air Header", AirQualityClass.CLASS_4, 150.0);
airSystem.addConsumer("Breathing Air", AirQualityClass.CLASS_1, 50.0);

// Set receiver tank
airSystem.setReceiverVolume(10.0);  // m³

// Run calculations
airSystem.run();

// Results
System.out.println("Utility Air System Results:");
System.out.println("  Compressor power: " + airSystem.getTotalCompressorPower() + " kW");
System.out.println("  Condensate rate: " + airSystem.getCondensateRateLhr() + " L/hr");
System.out.println("  Delivered dew point: " + airSystem.getDeliveredDewPointC() + " °C");
System.out.println("  Receiver holdup: " + airSystem.getReceiverHoldupMinutes() + " min");

// Get JSON report
String report = airSystem.toJson();
```

### Python Example

```python
from neqsim import jneqsim

UtilityAirSystem = jneqsim.process.equipment.util.UtilityAirSystem
AirQualityClass = jneqsim.process.equipment.util.UtilityAirSystem.AirQualityClass
CompressorType = jneqsim.process.equipment.util.UtilityAirSystem.CompressorType
DryerType = jneqsim.process.equipment.util.UtilityAirSystem.DryerType

# Create utility air system
air_system = UtilityAirSystem("Platform Air")

# Configure
air_system.setTargetQuality(AirQualityClass.CLASS_2)
air_system.setCompressorType(CompressorType.ROTARY_SCREW)
air_system.setDryerType(DryerType.DESICCANT_HEATED)
air_system.setDischargePressure(8.0)  # barg
air_system.setTotalAirDemand(500.0)   # Nm³/hr

# Add consumers
air_system.addConsumer("Instrument Air", AirQualityClass.CLASS_2, 300.0)
air_system.addConsumer("Plant Air", AirQualityClass.CLASS_4, 150.0)

air_system.run()

print(f"Compressor power: {air_system.getTotalCompressorPower():.1f} kW")
print(f"Dew point: {air_system.getDeliveredDewPointC():.1f} °C")
```

### Condensate Calculation

```java
// Estimate condensate from ambient air compression
airSystem.setInletTemperature(30.0);       // Hot day
airSystem.setInletRelativeHumidity(80.0);  // Humid
airSystem.run();

double condensateRate = airSystem.getCondensateRateLhr();
System.out.println("Condensate to drain: " + condensateRate + " L/hr");

// Condensate increases significantly in tropical climates!
```

---

## Sizing and Capacity

### Compressor Sizing

```java
// Set total demand with diversity factor
airSystem.setTotalAirDemand(600.0);  // Nm³/hr peak demand
airSystem.setDiversityFactor(0.7);    // 70% simultaneous use

// Size compressors
airSystem.autoSizeCompressors();

double perCompressor = airSystem.getCompressorCapacityEach();  // Nm³/hr
int numCompressors = airSystem.getNumberOfCompressors();
```

### Receiver Tank Sizing

Standard practice: Provide 1-3 minutes of demand at delivery pressure.

```java
// Size receiver for 2 minutes holdup
airSystem.setReceiverHoldupMinutes(2.0);
airSystem.autoSizeReceiver();

double tankVolume = airSystem.getReceiverVolume();  // m³
System.out.println("Receiver tank: " + tankVolume + " m³");
```

### Power Estimation

```java
// Estimate compressor power
// Typical: 6-8 kW per 100 Nm³/hr at 7 barg

airSystem.setTotalAirDemand(500.0);
airSystem.setDischargePressure(7.0);
airSystem.run();

double power = airSystem.getTotalCompressorPower();  // kW
double specific = power / 500.0 * 100;  // kW per 100 Nm³/hr

System.out.println("Specific power: " + specific + " kW per 100 Nm³/hr");
```

---

## API Reference

### Constructors

| Constructor | Description |
|-------------|-------------|
| `UtilityAirSystem(String name)` | Create system with name |

### Configuration - Quality

| Method | Description |
|--------|-------------|
| `setTargetQuality(AirQualityClass)` | Set ISO 8573-1 quality class |
| `setCompressorType(CompressorType)` | Set compressor type |
| `setDryerType(DryerType)` | Set air dryer type |

### Configuration - Operating Parameters

| Method | Description |
|--------|-------------|
| `setDischargePressure(double)` | Set delivery pressure (barg) |
| `setInletTemperature(double)` | Set ambient temperature (°C) |
| `setInletRelativeHumidity(double)` | Set ambient humidity (%) |
| `setAfterCoolerOutletTemp(double)` | Set aftercooler outlet (°C) |

### Configuration - Capacity

| Method | Description |
|--------|-------------|
| `setTotalAirDemand(double)` | Set total demand (Nm³/hr) |
| `setNumberOfCompressors(int)` | Set number of compressors |
| `setReceiverVolume(double)` | Set receiver tank volume (m³) |
| `addConsumer(String, AirQualityClass, double)` | Add air consumer |

### Configuration - Demand Split

| Method | Description |
|--------|-------------|
| `setInstrumentAirFraction(double)` | Set instrument air fraction |
| `setPlantAirFraction(double)` | Set plant air fraction |
| `setServiceAirFraction(double)` | Set service/breathing fraction |

### Results

| Method | Description |
|--------|-------------|
| `getTotalCompressorPower()` | Get total compressor power (kW) |
| `getCondensateRateLhr()` | Get condensate rate (L/hr) |
| `getDeliveredDewPointC()` | Get actual delivered dew point (°C) |
| `getReceiverHoldupMinutes()` | Get receiver holdup time (min) |
| `getCompressorCapacityEach()` | Get individual compressor capacity |
| `toJson()` | Get full results as JSON |

---

## Related Documentation

- [Compressors](../compressors) - Compressor equipment
- [Controllers](../../controllers) - Process controllers
- [NORSOK Standards](../../standards/) - Design standards
