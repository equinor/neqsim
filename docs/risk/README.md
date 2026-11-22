---
layout: default
title: Advanced Risk Framework
parent: Risk Framework
description: "Advanced risk and reliability framework documentation. Equipment reliability, system availability, barrier management, and quantitative risk assessment for oil and gas facilities."
---


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

The following example is complete Java source. It builds a one-stream process, configures a reproducible dynamic risk screen, executes a one-day Monte Carlo simulation, and verifies the returned availability contract without console output. `getMeanAvailability()` returns a percentage from 0 to 100.

<!-- risk-framework-quick-start: complete -->
```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.risk.dynamic.DynamicRiskResult;
import neqsim.process.safety.risk.dynamic.DynamicRiskSimulator;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class RiskFrameworkReadmeExample {
  private RiskFrameworkReadmeExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);

    DynamicRiskSimulator simulator = new DynamicRiskSimulator(process);
    simulator.setFeedStreamName("Feed");
    simulator.setProductStreamName("Feed");
    simulator.setRandomSeed(2026L);
    simulator.addEquipmentMtbf("Compressor", 8760.0, 72.0);
    simulator.setTimestepHours(0.5);

    DynamicRiskResult result = simulator.runDynamicSimulation(10, 1.0);
    double meanAvailabilityPercent = result.getMeanAvailability();
    if (!Double.isFinite(meanAvailabilityPercent)
        || meanAvailabilityPercent < 0.0 || meanAvailabilityPercent > 100.0) {
      throw new IllegalStateException("Availability must remain in [0, 100] percent");
    }
  }
}
```

For comprehensive examples, see:
- [RiskFrameworkQuickStart.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/process/safety/risk/examples/RiskFrameworkQuickStart.java)
- [Advanced Risk Framework Tutorial](https://github.com/equinor/neqsim/blob/master/docs/examples/AdvancedRiskFramework_Tutorial.ipynb)

## Feature Documentation

### P1: Dynamic Simulation Integration

See [Dynamic Simulation Guide](dynamic-simulation)

### P2: SIS/SIF Integration

See [SIS Integration Guide](sis-integration)

### P3: Real-time Digital Twin

See [Real-time Monitoring Guide](../integration/REAL_TIME_INTEGRATION_GUIDE)

### P4: Bow-Tie Analysis

See [Bow-Tie Analysis Guide](bowtie-analysis)

### P5: Portfolio Risk

See [Portfolio Risk Guide](overview#portfolio-risk-analysis)

### P6: Condition-Based Reliability

See [Condition-Based Reliability Guide](condition-based)

### P7: ML/AI Integration

See [ML Integration Guide](../integration/ml_integration)

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

- [API Reference](api-reference)
- [Mathematical Reference](mathematical-reference)
