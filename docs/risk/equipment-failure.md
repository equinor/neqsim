---
layout: default
title: Equipment Failure Modeling
parent: Risk Framework
---

# Equipment Failure Modeling

This document describes how to model equipment failures in NeqSim, including failure types, capacity factors, and reliability data.

---

## Failure Types

### FailureType Enum

```java
public enum FailureType {
    TRIP,           // Complete stop, requires restart
    DEGRADED,       // Reduced capacity operation
    PARTIAL_FAILURE,// Some functions lost
    FULL_FAILURE,   // Equipment non-functional
    MAINTENANCE,    // Planned shutdown
    BYPASSED        // Flow routed around
}
```

### Failure Type Characteristics

| Type | Capacity | Recovery | Typical Duration |
|------|----------|----------|------------------|
| TRIP | 0% | Manual restart | 1-4 hours |
| DEGRADED | 10-90% | Continues operating | Until repair |
| PARTIAL_FAILURE | 20-80% | May continue | Until repair |
| FULL_FAILURE | 0% | Major repair | Days to weeks |
| MAINTENANCE | 0% | Planned | Hours to days |
| BYPASSED | 0% | Reconfiguration | Until restored |

---

## Creating Failure Modes

### Using Static Factory Methods

```java
// Quick creation for common failure types
EquipmentFailureMode trip = EquipmentFailureMode.trip("Compressor A");
EquipmentFailureMode trip2 = EquipmentFailureMode.trip("Compressor A", "High vibration");

EquipmentFailureMode degraded = EquipmentFailureMode.degraded("Pump B", 0.5);  // 50% capacity

EquipmentFailureMode maintenance = EquipmentFailureMode.maintenance("Heat Exchanger", 8.0);  // 8 hours
```

### Using Builder Pattern

```java
EquipmentFailureMode failure = EquipmentFailureMode.builder()
    .name("Compressor surge")
    .description("Compressor enters surge condition")
    .type(FailureType.TRIP)
    .capacityFactor(0.0)        // Complete loss
    .efficiencyFactor(1.0)      // N/A when tripped
    .mttr(24.0)                 // 24 hours to repair
    .failureFrequency(0.5)      // 0.5 per year
    .requiresImmediateAction(true)
    .autoRecoverable(false)
    .build();
```

---

## Capacity Factor

The **capacity factor** defines the fraction of normal output during failure:

$$C_f = \frac{\text{Output during failure}}{\text{Normal output}}$$

| Value | Meaning |
|-------|---------|
| 0.0 | Complete loss (TRIP, FULL_FAILURE) |
| 0.5 | 50% capacity (DEGRADED) |
| 0.8 | 80% capacity (minor degradation) |
| 1.0 | No effect on capacity |

### Example: Degraded Compressor

```java
// Compressor running at 70% capacity due to fouling
EquipmentFailureMode fouling = EquipmentFailureMode.builder()
    .name("Compressor fouling")
    .type(FailureType.DEGRADED)
    .capacityFactor(0.7)         // 70% capacity
    .efficiencyFactor(0.85)      // 85% efficiency
    .build();

double normalFlow = 100.0;  // kg/s
double degradedFlow = normalFlow * fouling.getCapacityFactor();  // 70 kg/s
```

---

## Efficiency Factor

For degraded operation, efficiency may also be reduced:

$$\eta_{\text{degraded}} = \eta_{\text{normal}} \times E_f$$

Where $E_f$ is the efficiency factor (0.0 to 1.0).

### Example: Reduced Efficiency

```java
// Compressor with fouled internals
EquipmentFailureMode fouling = EquipmentFailureMode.builder()
    .name("Fouling")
    .type(FailureType.DEGRADED)
    .capacityFactor(0.9)        // 90% capacity
    .efficiencyFactor(0.8)      // 80% of normal efficiency
    .build();

double normalEfficiency = 0.75;
double degradedEfficiency = normalEfficiency * fouling.getEfficiencyFactor();  // 0.6
```

---

## Reliability Data Source

### OREDA-Based Data

The `ReliabilityDataSource` provides reliability data from OREDA (Offshore Reliability Data):

```java
ReliabilityDataSource source = ReliabilityDataSource.getInstance();

// Get reliability data for equipment type
double mttf = source.getMTTF("Compressor");        // Mean Time To Failure (hours)
double mttr = source.getMTTR("Compressor");        // Mean Time To Repair (hours)
double failureRate = source.getFailureRate("Compressor");  // Failures per year
```

### Equipment Types with Reliability Data

| Equipment Type | MTTF (hours) | MTTR (hours) | Availability |
|----------------|--------------|--------------|--------------|
| Compressor | 8,760 | 24 | 99.7% |
| Pump | 17,520 | 8 | 99.95% |
| Separator | 43,800 | 4 | 99.99% |
| Heat Exchanger | 43,800 | 12 | 99.97% |
| Valve (Control) | 26,280 | 4 | 99.98% |
| Turbine | 8,760 | 48 | 99.5% |

### CSV Data Format

Reliability data is stored in CSV files under `src/main/resources/reliabilitydata/`:

**equipment_reliability.csv**:
```csv
EquipmentType,MTTF_hours,MTTR_hours,Source
Compressor,8760,24,OREDA-2015
Pump,17520,8,OREDA-2015
Separator,43800,4,OREDA-2015
HeatExchanger,43800,12,OREDA-2015
Valve,26280,4,OREDA-2015
```

**failure_modes.csv**:
```csv
EquipmentType,FailureMode,Probability,CapacityFactor,TypicalMTTR
Compressor,Trip,0.6,0.0,24
Compressor,Degraded,0.3,0.7,48
Compressor,Partial,0.1,0.5,72
```

---

## Mathematical Background

### Failure Rate

Failure rate $\lambda$ is the expected number of failures per unit time:

$$\lambda = \frac{1}{\text{MTTF}}$$

For a Poisson process, the probability of $k$ failures in time $t$:

$$P(k) = \frac{(\lambda t)^k e^{-\lambda t}}{k!}$$

### Availability

Inherent availability:

$$A = \frac{\text{MTTF}}{\text{MTTF} + \text{MTTR}} = \frac{\text{Uptime}}{\text{Total Time}}$$

For multiple independent equipment in series:

$$A_{\text{system}} = \prod_{i=1}^{n} A_i$$

For redundant equipment in parallel:

$$A_{\text{parallel}} = 1 - \prod_{i=1}^{n} (1 - A_i)$$

### Reliability Function

Exponential reliability function (constant failure rate):

$$R(t) = e^{-\lambda t}$$

Probability of failure before time $t$:

$$F(t) = 1 - R(t) = 1 - e^{-\lambda t}$$

---

## Common Failure Scenarios

### 1. Compressor Trip

```java
EquipmentFailureMode compressorTrip = EquipmentFailureMode.builder()
    .name("Compressor trip - high vibration")
    .description("Trip due to vibration exceeding 25mm/s")
    .type(FailureType.TRIP)
    .capacityFactor(0.0)
    .mttr(24.0)
    .failureFrequency(0.5)  // Once every 2 years
    .requiresImmediateAction(true)
    .build();
```

### 2. Pump Degradation

```java
EquipmentFailureMode pumpWear = EquipmentFailureMode.builder()
    .name("Pump impeller wear")
    .description("Gradual performance degradation")
    .type(FailureType.DEGRADED)
    .capacityFactor(0.8)    // 80% of design flow
    .efficiencyFactor(0.75) // Reduced efficiency
    .mttr(72.0)             // Impeller replacement
    .failureFrequency(0.2)
    .build();
```

### 3. Separator Level Control Failure

```java
EquipmentFailureMode levelFailure = EquipmentFailureMode.builder()
    .name("Level control failure")
    .description("Level transmitter malfunction")
    .type(FailureType.PARTIAL_FAILURE)
    .capacityFactor(0.7)  // Manual level control possible
    .mttr(4.0)
    .requiresImmediateAction(false)
    .autoRecoverable(false)
    .build();
```

### 4. Planned Maintenance

```java
EquipmentFailureMode turnaround = EquipmentFailureMode.builder()
    .name("Planned turnaround")
    .description("Annual maintenance shutdown")
    .type(FailureType.MAINTENANCE)
    .capacityFactor(0.0)
    .mttr(168.0)  // 7 days
    .failureFrequency(1.0)  // Annual
    .requiresImmediateAction(false)
    .build();
```

---

## Integration with Process Simulation

### Applying Failure to Equipment

```java
// Get compressor from process
Compressor compressor = (Compressor) process.getUnit("HP Compressor");

// Create failure mode
EquipmentFailureMode failure = EquipmentFailureMode.trip("HP Compressor");

// Analyze impact
ProductionImpactAnalyzer analyzer = new ProductionImpactAnalyzer(process);
ProductionImpactResult result = analyzer.analyzeFailureImpact(failure);

System.out.println("Production loss: " + result.getPercentLoss() + "%");
System.out.println("Affected equipment: " + result.getAffectedEquipment());
```

---

## Best Practices

1. **Use OREDA data** when available for realistic reliability values
2. **Define multiple failure modes** for critical equipment
3. **Include degraded modes** - not all failures are complete trips
4. **Consider auto-recovery** for transient failures
5. **Document failure causes** in descriptions for maintenance planning
6. **Validate with historical data** when possible

---

## See Also

- [Reliability Data Source](api-reference.md#reliabilitydatasource)
- [Production Impact Analysis](production-impact)
- [Monte Carlo Simulation](monte-carlo)
- [Mathematical Reference](mathematical-reference)
