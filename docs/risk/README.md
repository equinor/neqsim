---
layout: default
title: Advanced Risk Framework
parent: Risk Framework
---

# NeqSim Advanced Risk Framework

The NeqSim Risk Framework provides comprehensive operational risk analysis capabilities for oil and gas operations. This documentation covers the advanced features implemented across seven priority areas.

## Overview

The risk framework integrates with NeqSim's process simulation capabilities to provide:

- **Dynamic Risk Simulation** (P1) - Monte Carlo with transient modeling
- **SIS/SIF Integration** (P2) - Safety Instrumented Systems per IEC 61508/61511
- **Real-time Digital Twin** (P3) - Continuous monitoring interface
- **Bow-Tie Diagram Generation** (P4) - Visual barrier analysis
- **Multi-Asset Portfolio Risk** (P5) - Portfolio-level VaR analysis
- **Condition-Based Reliability** (P6) - Predictive maintenance integration
- **ML/AI Integration** (P7) - Machine learning interface

## Package Structure

```
neqsim.process.safety.risk
├── dynamic/           # P1: Dynamic simulation with transients
├── sis/               # P2: Safety Instrumented Systems
├── realtime/          # P3: Real-time monitoring
├── bowtie/            # P4: Bow-tie diagram analysis
├── portfolio/         # P5: Multi-asset portfolio risk
├── condition/         # P6: Condition-based reliability
├── ml/                # P7: ML/AI integration
└── examples/          # Quick-start examples
```

## Quick Start

```java
import neqsim.process.safety.risk.dynamic.*;
import neqsim.process.safety.risk.sis.*;
import neqsim.process.safety.risk.realtime.*;

// Example: Dynamic simulation
DynamicRiskSimulator sim = new DynamicRiskSimulator("Platform Risk");
sim.setBaseProductionRate(100.0);
sim.addEquipment("Compressor", 8760, 72, 1.0);
DynamicRiskResult result = sim.runSimulation();
System.out.println("Expected production: " + result.getExpectedProduction());
```

For comprehensive examples, see:
- [RiskFrameworkQuickStart.java](../../src/main/java/neqsim/process/safety/risk/examples/RiskFrameworkQuickStart.java)
- [Advanced Risk Framework Tutorial](../examples/AdvancedRiskFramework_Tutorial.ipynb)

## Feature Documentation

### P1: Dynamic Simulation Integration

See [Dynamic Simulation Guide](dynamic-simulation.md)

### P2: SIS/SIF Integration

See [SIS Integration Guide](sis-integration.md)

### P3: Real-time Digital Twin

See [Real-time Monitoring Guide](../integration/REAL_TIME_INTEGRATION_GUIDE.md)

### P4: Bow-Tie Analysis

See [Bow-Tie Analysis Guide](bowtie-analysis.md)

### P5: Portfolio Risk

See [Portfolio Risk Guide](overview.md#portfolio-risk-analysis)

### P6: Condition-Based Reliability

See [Condition-Based Reliability Guide](condition-based.md)

### P7: ML/AI Integration

See [ML Integration Guide](../integration/ml_integration.md)

## Industry Standards

The framework implements or aligns with:

| Standard | Description | Package |
|----------|-------------|---------|
| IEC 61508 | Functional Safety | sis |
| IEC 61511 | Safety Instrumented Systems | sis |
| ISO 14224 | Equipment Reliability Data | condition |
| ISO 31000 | Risk Management | bowtie |
| NORSOK Z-013 | Risk & Emergency Preparedness | All |
| OREDA | Offshore Reliability Data | dynamic |

## API Reference

- [API Reference](api-reference.md)
- [Mathematical Reference](mathematical-reference.md)
