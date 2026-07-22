---
layout: default
title: Risk Simulation Framework
nav_order: 7
description: "NeqSim risk and reliability simulation framework. Covers equipment failure modeling, Monte Carlo simulation, bow-tie analysis, SIS integration, risk matrix, and production impact analysis."
---

This documentation covers NeqSim's comprehensive **Operational Risk Simulation Framework** for equipment failure analysis, production impact assessment, and process topology analysis.

---

## 📚 Documentation Structure

### Core Framework

| Section | Description |
|---------|-------------|
| [Overview](overview) | Framework architecture and key concepts |
| [Equipment Failure Modeling](equipment-failure) | Failure modes, types, and reliability data |
| [Risk Matrix](risk-matrix) | 5×5 risk matrix with probability, consequence, and cost |
| [Monte Carlo Simulation](monte-carlo) | Stochastic simulation for availability analysis |
| [Production Impact Analysis](production-impact) | Analyzing failure effects on production |
| [Degraded Operation](degraded-operation) | Optimizing plant operation during outages |
| [Process Topology](topology) | Graph structure extraction and analysis |
| [STID & Functional Location](stid-tagging) | Equipment tagging following ISO 14224 |
| [Dependency Analysis](dependency-analysis) | Cascade failure and cross-installation effects |
| [Mathematical Reference](mathematical-reference) | Formulas and statistical methods |
| [API Reference](api-reference) | Complete Java API documentation |

### Advanced Risk Framework (P1-P7)

| Section | Description |
|---------|-------------|
| [Advanced Framework Overview](overview.md#framework-capabilities) | Overview of the implemented risk-analysis packages |
| [P1: Dynamic Simulation](dynamic-simulation) | Monte Carlo with transient modeling |
| [P2: SIS/SIF Integration](sis-integration) | IEC 61508/61511, LOPA, SIL verification |
| [P4: Bow-Tie Analysis](bowtie-analysis) | Barrier analysis, threat/consequence visualization |
| [P6: Condition-Based Reliability](condition-based) | Health monitoring, RUL estimation |

---

## Quick start: a traceable LOPA calculation

The example below is a complete Java 8 program. It applies a BPCS protection layer and a SIL 2 safety instrumented
function (SIF) to an initiating-event frequency. The numerical result is transparent:

$$
f_{\mathrm{mitigated}}
= f_{\mathrm{IE}}\,\mathrm{PFD}_{\mathrm{BPCS}}\,\mathrm{PFD}_{\mathrm{SIF}}
= 0.1 \times 0.1 \times 0.005
= 5.0 \times 10^{-5}\ \mathrm{yr}^{-1}
$$

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.safety.risk.RiskEvent;
import neqsim.process.safety.risk.sis.LOPAResult;
import neqsim.process.safety.risk.sis.SISIntegratedRiskModel;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;

public final class RiskFrameworkQuickStart {
  private static final Logger logger = LogManager.getLogger(RiskFrameworkQuickStart.class);

  private RiskFrameworkQuickStart() {}

  public static void main(String[] args) {
    String eventName = "HP vessel overpressure";

    SISIntegratedRiskModel model = new SISIntegratedRiskModel("HP vessel LOPA");
    model.addInitiatingEvent(eventName, 0.1, RiskEvent.ConsequenceCategory.MAJOR);

    SISIntegratedRiskModel.IndependentProtectionLayer bpcs =
        new SISIntegratedRiskModel.IndependentProtectionLayer(
            "BPCS pressure control",
            0.1,
            SISIntegratedRiskModel.IndependentProtectionLayer.IPLType.BPCS);
    bpcs.addApplicableEvent(eventName);
    model.addIPL(bpcs);

    SafetyInstrumentedFunction esd = SafetyInstrumentedFunction.builder()
        .id("SIF-001")
        .name("High-pressure ESD")
        .description("Isolate the HP vessel on confirmed high pressure")
        .sil(2)
        .pfd(0.005)
        .initiatingEvent(eventName)
        .addProtectedEquipment("HP vessel")
        .safeState("Isolated")
        .build();
    model.addSIF(esd);

    LOPAResult result = model.performLOPA(eventName);
    double expectedFrequency = 0.1 * 0.1 * 0.005;
    if (Math.abs(result.getMitigatedFrequency() - expectedFrequency) > 1.0e-12) {
      throw new IllegalStateException("LOPA layer multiplication did not close");
    }

    logger.info("Mitigated frequency: {} per year", result.getMitigatedFrequency());
    logger.info("Total risk-reduction factor: {}", result.getTotalRRF());
  }
}
```

This calculation demonstrates software behavior, not approval of an initiating-event frequency, IPL independence,
SIL target, test interval, or safe state. Those inputs require a traceable hazard study and accountable review. For a
process-coupled study, continue with [Monte Carlo simulation](monte-carlo.md), [dynamic risk](dynamic-simulation.md),
[SIS integration](sis-integration.md), and [process topology](topology.md). Python users should access these Java
classes through the supported `from neqsim import jneqsim` gateway; see the
[advanced risk notebook](../examples/AdvancedRiskFramework_Tutorial.ipynb) for the complete setup.

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

- [NeqSim Main Documentation](../index)
- [Process Simulation Guide](../process/)
- [Advanced Risk Framework Tutorial (Jupyter)](../examples/AdvancedRiskFramework_Tutorial.ipynb)
- [Examples Index](../examples/index)
- [OREDA Handbook](https://www.oreda.com/)
- [ISO 14224 - Petroleum and natural gas industries](https://www.iso.org/standard/64076.html)
- [NORSOK Z-013 - Risk and emergency preparedness assessment](https://standard.no/en/sectors/petroleum/norsok-standards/s-safety-she/)
