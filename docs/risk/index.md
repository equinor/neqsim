---
layout: default
title: Risk Simulation Framework
nav_order: 7
---

# Risk Simulation Framework Documentation

This documentation covers NeqSim's comprehensive **Operational Risk Simulation Framework** for equipment failure analysis, production impact assessment, and process topology analysis.

---

## 📚 Documentation Structure

### Core Framework
| Section | Description |
|---------|-------------|
| [Overview](overview.md) | Framework architecture and key concepts |
| [Equipment Failure Modeling](equipment-failure.md) | Failure modes, types, and reliability data |
| [Risk Matrix](risk-matrix.md) | 5×5 risk matrix with probability, consequence, and cost |
| [Monte Carlo Simulation](monte-carlo.md) | Stochastic simulation for availability analysis |
| [Production Impact Analysis](production-impact.md) | Analyzing failure effects on production |
| [Degraded Operation](degraded-operation.md) | Optimizing plant operation during outages |
| [Process Topology](topology.md) | Graph structure extraction and analysis |
| [STID & Functional Location](stid-tagging.md) | Equipment tagging following ISO 14224 |
| [Dependency Analysis](dependency-analysis.md) | Cascade failure and cross-installation effects |
| [Mathematical Reference](mathematical-reference.md) | Formulas and statistical methods |
| [API Reference](api-reference.md) | Complete Java API documentation |

### Advanced Risk Framework (P1-P7)
| Section | Description |
|---------|-------------|
| [**Advanced Framework Overview**](README.md) | Overview of all 7 priority packages |
| [P1: Dynamic Simulation](dynamic-simulation.md) | Monte Carlo with transient modeling |
| [P2: SIS/SIF Integration](sis-integration.md) | IEC 61508/61511, LOPA, SIL verification |
| [P4: Bow-Tie Analysis](bowtie-analysis.md) | Barrier analysis, threat/consequence visualization |
| [P6: Condition-Based Reliability](condition-based.md) | Health monitoring, RUL estimation |

---

## 🚀 Quick Start

### Java

```java
import neqsim.process.safety.risk.*;
import neqsim.process.util.topology.*;
import neqsim.process.equipment.failure.*;

// Create process system
ProcessSystem process = new ProcessSystem();
// ... add equipment ...

// Risk analysis
RiskMatrix matrix = new RiskMatrix(process);
matrix.buildRiskMatrix();
System.out.println(matrix.toVisualization());

// Monte Carlo simulation
OperationalRiskSimulator simulator = new OperationalRiskSimulator(process);
simulator.addEquipmentReliability("Compressor A", 0.5, 24.0);
OperationalRiskResult result = simulator.runSimulation(10000, 365);
System.out.println("Availability: " + result.getAvailability() + "%");

// Topology analysis
ProcessTopologyAnalyzer topology = new ProcessTopologyAnalyzer(process);
topology.buildTopology();
topology.setFunctionalLocation("Compressor A", "1775-KA-23011A");
```

### Advanced Risk Framework (Python)

```python
# Dynamic simulation with transients
from neqsim.process.safety.risk.dynamic import DynamicRiskSimulator

sim = DynamicRiskSimulator("Platform Risk")
sim.setBaseProductionRate(100.0)
sim.addEquipment("Compressor", 8760, 72, 1.0)
sim.setShutdownProfile(DynamicRiskSimulator.RampProfile.S_CURVE)
result = sim.runSimulation()
print(f"Transient losses: {result.getTransientLoss().getTotalTransientLoss()}")

# SIS/LOPA Analysis
from neqsim.process.safety.risk.sis import SISIntegratedRiskModel, SafetyInstrumentedFunction

model = SISIntegratedRiskModel("Overpressure Protection")
model.setInitiatingEventFrequency(0.1)
model.addIPL("BPCS Alarm", 10)
model.addIPL("Operator", 10)
sif = SafetyInstrumentedFunction("SIF-001", "PAHH")
sif.setSILTarget(2)
model.addSIF(sif)
lopa = model.performLOPA()
print(f"LOPA: {'PASS' if lopa.isAcceptable() else 'FAIL'}")
```

### Python (neqsim-python)

```python
import jpype
import neqsim

from neqsim.process.safety.risk import RiskMatrix, OperationalRiskSimulator
from neqsim.process.util.topology import ProcessTopologyAnalyzer, FunctionalLocation

# Build topology
topology = ProcessTopologyAnalyzer(process)
topology.buildTopology()

# STID tagging
topology.setFunctionalLocation("Compressor A", "1775-KA-23011A")

# Risk matrix
matrix = RiskMatrix()
matrix.addRiskItem("Compressor Trip", 
    RiskMatrix.ProbabilityCategory.POSSIBLE,
    RiskMatrix.ConsequenceCategory.MAJOR, 
    500000.0)
print(matrix.toVisualization())
```

---

## 📊 Framework Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    NeqSim Process Simulation                        │
│                         ProcessSystem                               │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
           ┌───────────────────┼───────────────────┐
           ▼                   ▼                   ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  Equipment      │  │  Production     │  │  Process        │
│  Failure        │  │  Impact         │  │  Topology       │
│  Modeling       │  │  Analysis       │  │  Analysis       │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  Reliability    │  │  Degraded       │  │  Dependency     │
│  Data           │  │  Operation      │  │  Analysis       │
│  (OREDA)        │  │  Optimizer      │  │                 │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         └────────────────────┼────────────────────┘
                              ▼
              ┌───────────────────────────────┐
              │      Risk Assessment          │
              │  ┌─────────────────────────┐  │
              │  │    Monte Carlo          │  │
              │  │    Simulation           │  │
              │  └─────────────────────────┘  │
              │  ┌─────────────────────────┐  │
              │  │    Risk Matrix          │  │
              │  │    (5×5 Visualization)  │  │
              │  └─────────────────────────┘  │
              └───────────────────────────────┘
```

---

## 📦 Package Structure

```
neqsim.process
├── equipment.failure
│   ├── EquipmentFailureMode      - Failure type definitions
│   ├── ReliabilityDataSource     - OREDA reliability data
│   └── package-info.java
├── safety.risk
│   ├── OperationalRiskSimulator  - Monte Carlo engine
│   ├── OperationalRiskResult     - Simulation results
│   └── RiskMatrix                - 5×5 risk matrix
└── util
    ├── optimizer
    │   ├── ProductionImpactAnalyzer   - Impact analysis
    │   ├── ProductionImpactResult     - Impact results
    │   ├── DegradedOperationOptimizer - Degraded optimization
    │   └── DegradedOperationResult    - Optimization results
    └── topology
        ├── ProcessTopologyAnalyzer    - Graph extraction
        ├── FunctionalLocation         - STID parsing
        ├── DependencyAnalyzer         - Cascade analysis
        └── package-info.java
```

---

## 🔗 Related Resources

- [NeqSim Main Documentation](../index.md)
- [Process Simulation Guide](../process/README.md)
- [Jupyter Notebook Examples](../../examples/notebooks/)
- [OREDA Handbook](https://www.oreda.com/)
- [ISO 14224 - Petroleum and natural gas industries](https://www.iso.org/standard/64076.html)
- [NORSOK Z-013 - Risk and emergency preparedness assessment](https://www.standard.no/en/sectors/energi-og-klima/petroleum/norsok-standard-categories/z-regularity--emergency/z-0132/)
