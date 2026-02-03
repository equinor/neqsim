---
layout: default
title: Framework Overview
parent: Risk Framework
---

# Risk Simulation Framework Overview

## Introduction

The NeqSim Risk Simulation Framework provides comprehensive tools for analyzing equipment failures, their impact on production, and optimizing plant operation under degraded conditions. It integrates with NeqSim's process simulation capabilities to provide physics-based risk assessment.

---

## Key Concepts

### 1. Equipment Failure Modes

Equipment can fail in different ways, each with different consequences:

| Failure Type | Description | Capacity Factor |
|--------------|-------------|-----------------|
| **TRIP** | Equipment stops completely | 0% |
| **DEGRADED** | Reduced capacity operation | 10-90% |
| **PARTIAL_FAILURE** | Some functions lost | 20-80% |
| **FULL_FAILURE** | Complete breakdown | 0% |
| **MAINTENANCE** | Planned shutdown | 0% |
| **BYPASSED** | Flow routed around | 0% (for that unit) |

### 2. Reliability Metrics

Standard reliability metrics from OREDA (Offshore Reliability Data):

| Metric | Symbol | Description |
|--------|--------|-------------|
| Mean Time To Failure | MTTF | Average operating time before failure |
| Mean Time To Repair | MTTR | Average repair duration |
| Mean Time Between Failures | MTBF | MTTF + MTTR |
| Failure Rate | λ | Failures per time unit |
| Availability | A | Fraction of time operational |

### 3. Risk Assessment

Risk is evaluated on two dimensions:

1. **Probability** (likelihood of failure)
2. **Consequence** (impact when failure occurs)

The combination determines the **Risk Level**:

```
Risk Level = f(Probability, Consequence)
```

### 4. Process Topology

Equipment connections form a directed graph:

```
Feed → Separator → Compressor → Cooler → Export
                 ↘ Pump → Storage
```

Understanding topology enables:
- Cascade failure analysis
- Parallel equipment identification
- Critical path determination

---

## Framework Capabilities

### ✅ Equipment Failure Modeling

- Define failure types (trip, degraded, partial, etc.)
- Capacity reduction factors
- Auto-recovery behavior
- OREDA-based reliability data

### ✅ Production Impact Analysis

- Quantify production loss from failures
- Compare failure scenarios
- Rank equipment by criticality
- Calculate revenue impact

### ✅ Monte Carlo Simulation

- Stochastic failure simulation
- P10/P50/P90 production estimates
- Availability calculations
- Confidence intervals

### ✅ Risk Matrix

- 5×5 probability-consequence matrix
- Color-coded risk levels
- Economic cost calculations
- Visual ASCII/JSON export

### ✅ Topology Analysis

- Graph extraction from ProcessSystem
- Topological ordering
- Parallel equipment detection
- DOT format export for visualization

### ✅ STID Tagging

- ISO 14224 compliant tag parsing
- Norwegian offshore installation codes
- Equipment type classification
- Parallel train identification

### ✅ Dependency Analysis

- "If X fails, what's affected?"
- Cascade failure propagation
- Cross-installation dependencies
- Equipment monitoring recommendations

---

## Typical Workflow

```
1. Build Process Model
   └─► ProcessSystem with equipment

2. Define Failure Scenarios
   └─► EquipmentFailureMode for each critical unit

3. Assign Reliability Data
   └─► MTTF, MTTR from OREDA

4. Build Topology
   └─► ProcessTopologyAnalyzer.buildTopology()

5. Tag Equipment
   └─► STID functional locations

6. Analyze Dependencies
   └─► DependencyAnalyzer.analyzeFailure()

7. Build Risk Matrix
   └─► RiskMatrix.buildRiskMatrix()

8. Run Monte Carlo
   └─► OperationalRiskSimulator.runSimulation()

9. Optimize Degraded Operation
   └─► DegradedOperationOptimizer.optimize()
```

---

## Industry Standards

This framework follows industry standards:

| Standard | Application |
|----------|-------------|
| **ISO 14224** | Equipment taxonomy and reliability data |
| **OREDA** | Offshore reliability data handbook |
| **ISO 31000** | Risk management principles |
| **NORSOK Z-013** | Risk and emergency preparedness |
| **IEC 61508** | Functional safety |
| **API 580/581** | Risk-based inspection |

---

## Mathematical Foundation

See [Mathematical Reference](mathematical-reference) for detailed formulas covering:

- Failure probability distributions
- Availability calculations
- Monte Carlo algorithms
- Risk cost functions
- Production loss modeling

---

## Next Steps

- [Equipment Failure Modeling](equipment-failure) - Define failure modes
- [Risk Matrix](risk-matrix) - Build visual risk assessment
- [Monte Carlo Simulation](monte-carlo) - Run stochastic analysis
- [Topology Analysis](topology) - Extract process structure
